package pl.havronskyi.finance.stats;

import org.springframework.stereotype.Service;
import pl.havronskyi.finance.domain.*;
import pl.havronskyi.finance.repo.TxnRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final TxnRepository txns;

    public StatsService(TxnRepository txns) {
        this.txns = txns;
    }

    private static BigDecimal money(long minor) {
        return new BigDecimal(minor).movePointLeft(2);
    }

    public long totalTransactionCount(Long workspaceId) {
        return txns.countByWorkspaceId(workspaceId);
    }

    public List<CategoryTransaction> transactionsForCategory(Long workspaceId, String category, LocalDate from,
                                                               LocalDate to) {
        return toCategoryTransactions(txns.findByWorkspaceIdAndCategoryAndTxnDateBetween(workspaceId, category, from,
                to));
    }

    /**
     * Internal transfers are excluded from byCategory entirely, so without this there's no
     * way to even see one, let alone move it into a real category (or move a wrongly-flagged
     * expense into INTERNAL_TRANSFER) from the Stats page.
     */
    public List<CategoryTransaction> transfersForRange(Long workspaceId, LocalDate from, LocalDate to) {
        return toCategoryTransactions(txns.findByWorkspaceIdAndKindAndTxnDateBetween(workspaceId,
                TxnKind.INTERNAL_TRANSFER, from, to));
    }

    private List<CategoryTransaction> toCategoryTransactions(List<Txn> list) {
        return list.stream()
                .sorted(Comparator.comparing(Txn::getTxnDate).reversed()
                        .thenComparing(Comparator.comparing(Txn::getId).reversed()))
                .map(t -> new CategoryTransaction(
                        t.getId(),
                        t.getTxnDate(),
                        t.getMerchantNorm() == null || t.getMerchantNorm().isBlank()
                                ? t.getDescription() : t.getMerchantNorm(),
                        t.getDescription(),
                        money(t.getKind() == TxnKind.EXPENSE ? -Math.abs(plnMinor(t)) : plnMinor(t)),
                        t.getCurrency(),
                        t.getKind()))
                .toList();
    }

    private static long plnMinor(Txn t) {
        // Native PLN rows never get amountPlnMinor set - amountMinor is already the PLN
        // value there. For foreign-currency rows, amountPlnMinor is the converted value
        // once ExchangeRateService has filled it in; falling back to amountMinor when it's
        // still missing keeps the row from vanishing, at the cost of mixing currencies -
        // exactly what the currency warning exists to flag.
        return t.getAmountPlnMinor() != null ? t.getAmountPlnMinor() : t.getAmountMinor();
    }

    /**
     * The period is counted by transaction date, not by booking date and not by the
     * credit card billing cycle - otherwise "October expenses" would mean the
     * period from the 12th to the 11th, and nobody trusts the numbers.
     */
    public PeriodReport forRange(Long workspaceId, LocalDate from, LocalDate to) {
        List<Txn> all = txns.findByWorkspaceIdAndTxnDateBetween(workspaceId, from, to);

        long transfers = 0;
        Map<String, Long> byCategory = new TreeMap<>();
        int uncategorized = 0;
        List<String> warnings = new ArrayList<>();
        List<String> nettedCounterparties = new ArrayList<>();

        // Pass 1: bucket EXPENSE/INCOME rows by merchant so a counterparty who both took and
        // sent money (a refund, a settle-up) nets directly - the natural case, same identity
        // on both sides. Rows with no usable merchant identity get their own singleton bucket,
        // which can never net (nothing to pair it against) and just falls through as-is.
        Map<String, List<Txn>> byMerchant = new LinkedHashMap<>();

        for (Txn t : all) {
            if (t.getKind() == TxnKind.UNKNOWN) {
                warnings.add("Transakcja " + t.getId() + " bez ustalonego typu");
            }
            if (!t.getCurrency().equals("PLN") && t.getAmountPlnMinor() == null) {
                warnings.add("Transakcja " + t.getId() + " w " + t.getCurrency()
                        + " bez przeliczenia na PLN - nie wchodzi poprawnie do sum");
            }

            switch (t.getKind()) {
                case INTERNAL_TRANSFER -> transfers += Math.abs(plnMinor(t));
                case EXPENSE, INCOME -> {
                    String merchant = t.getMerchantNorm() == null || t.getMerchantNorm().isBlank()
                            ? "txn-" + t.getId() : t.getMerchantNorm();
                    byMerchant.computeIfAbsent(merchant, k -> new ArrayList<>()).add(t);
                }
                case UNKNOWN -> {
                    // already warned above
                }
            }
        }

        long expenses = 0, income = 0;
        // Anything that doesn't net at the merchant level (a single-direction merchant) moves
        // here for pass 2 - the same person/category relationship can still span different
        // counterparties (e.g. you pay one person and get paid back by another for the same
        // thing), and the category the user assigned is the signal that ties them together.
        List<Txn> remaining = new ArrayList<>();

        for (Map.Entry<String, List<Txn>> entry : byMerchant.entrySet()) {
            String merchant = entry.getKey();
            long grossExpense = 0, grossIncome = 0;
            for (Txn t : entry.getValue()) {
                if (t.getKind() == TxnKind.EXPENSE) grossExpense += Math.abs(plnMinor(t));
                else grossIncome += plnMinor(t);
            }

            if (grossExpense > 0 && grossIncome > 0) {
                long net = grossIncome - grossExpense;
                if (net >= 0) {
                    income += net;
                } else {
                    expenses += -net;
                }
                nettedCounterparties.add(merchant + ": saldo netto " + money(net) + " PLN (wyslano "
                        + money(grossExpense) + ", otrzymano " + money(grossIncome) + ")");
                for (Txn t : entry.getValue()) {
                    if (t.getKind() == TxnKind.EXPENSE && t.getCategory() == null) uncategorized++;
                }
            } else {
                remaining.addAll(entry.getValue());
            }
        }

        // Pass 2: category-level netting over whatever didn't net by merchant. Categories are
        // the user's own signal for "this money belongs together" - e.g. money you send one
        // person and get back from another under the same category should still net out.
        Map<String, List<Txn>> byCategoryGroup = new LinkedHashMap<>();
        for (Txn t : remaining) {
            if (t.getCategory() == null) {
                long m = plnMinor(t);
                if (t.getKind() == TxnKind.EXPENSE) {
                    expenses += Math.abs(m);
                    uncategorized++;
                } else {
                    income += m;
                }
            } else {
                byCategoryGroup.computeIfAbsent(t.getCategory(), k -> new ArrayList<>()).add(t);
            }
        }

        for (Map.Entry<String, List<Txn>> entry : byCategoryGroup.entrySet()) {
            String category = entry.getKey();
            long grossExpense = 0, grossIncome = 0;
            for (Txn t : entry.getValue()) {
                if (t.getKind() == TxnKind.EXPENSE) grossExpense += Math.abs(plnMinor(t));
                else grossIncome += plnMinor(t);
            }

            if (grossExpense > 0 && grossIncome > 0) {
                long net = grossIncome - grossExpense;
                if (net >= 0) {
                    income += net;
                } else {
                    expenses += -net;
                }
                nettedCounterparties.add(category + " (kategoria): saldo netto " + money(net)
                        + " PLN (wyslano " + money(grossExpense) + ", otrzymano " + money(grossIncome) + ")");
            } else {
                expenses += grossExpense;
                income += grossIncome;
            }
        }

        // byCategory is intentionally a separate, simple pass: the signed net (income positive,
        // expense negative) of every EXPENSE/INCOME transaction with that category, independent
        // of whether the merchant/category netting above found a counterparty to offset against.
        // Without this, a category that happens to be income-dominant (e.g. VITA netting to a
        // net gain some months) would just vanish into the income total with no visibility into
        // which category it came from.
        for (Txn t : all) {
            if (t.getCategory() == null) continue;
            if (t.getKind() != TxnKind.EXPENSE && t.getKind() != TxnKind.INCOME) continue;
            long signed = t.getKind() == TxnKind.EXPENSE ? -Math.abs(plnMinor(t)) : plnMinor(t);
            byCategory.merge(t.getCategory(), signed, Long::sum);
        }

        long orphanTransfers = all.stream()
                .filter(t -> t.getKind() == TxnKind.INTERNAL_TRANSFER && t.getTransferGroup() == null)
                .count();
        if (orphanTransfers > 0) {
            warnings.add(orphanTransfers + " przelewow wewnetrznych bez pary - "
                    + "prawdopodobnie brakuje wyciagu drugiego rachunku");
        }
        if (uncategorized > 0) {
            warnings.add(uncategorized + " wydatkow bez kategorii - kolejka review nie zostala domknieta");
        }

        return new PeriodReport(
                from,
                to,
                money(expenses),
                money(income),
                money(income - expenses),
                byCategory.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> money(e.getValue()),
                                (a, b) -> a, TreeMap::new)),
                money(transfers),
                uncategorized,
                warnings,
                nettedCounterparties);
    }
}
