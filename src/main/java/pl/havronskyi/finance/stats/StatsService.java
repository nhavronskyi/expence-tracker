package pl.havronskyi.finance.stats;

import org.springframework.stereotype.Service;
import pl.havronskyi.finance.domain.*;
import pl.havronskyi.finance.repo.AccountRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final TxnRepository txns;
    private final AccountRepository accounts;

    public StatsService(TxnRepository txns, AccountRepository accounts) {
        this.txns = txns;
        this.accounts = accounts;
    }

    private static BigDecimal money(long minor) {
        return new BigDecimal(minor).movePointLeft(2);
    }

    /**
     * The month is counted by transaction date, not by booking date and not by the
     * credit card billing cycle - otherwise "October expenses" would mean the
     * period from the 12th to the 11th, and nobody trusts the numbers.
     */
    public MonthlyReport monthly(YearMonth month, AccountScope scope) {
        Map<Long, Account> accountById = accounts.findAll().stream()
                .collect(Collectors.toMap(Account::getId, a -> a));

        List<Txn> all = txns.findByTxnDateBetween(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(t -> {
                    Account a = accountById.get(t.getAccountId());
                    return a != null && a.getScope() == scope;
                })
                .toList();

        long expenses = 0, income = 0, transfers = 0;
        Map<Category, Long> byCategory = new EnumMap<>(Category.class);
        int uncategorized = 0;
        List<String> warnings = new ArrayList<>();

        for (Txn t : all) {
            switch (t.getKind()) {
                case INTERNAL_TRANSFER -> transfers += Math.abs(t.getAmountMinor());
                case INCOME -> income += t.getAmountMinor();
                case EXPENSE -> {
                    expenses += Math.abs(t.getAmountMinor());
                    if (t.getCategory() == null) {
                        uncategorized++;
                    } else {
                        byCategory.merge(t.getCategory(), Math.abs(t.getAmountMinor()), Long::sum);
                    }
                }
                case UNKNOWN -> warnings.add("Transakcja " + t.getId() + " bez ustalonego typu");
            }
            if (!t.getCurrency().equals("PLN") && t.getAmountPlnMinor() == null) {
                warnings.add("Transakcja " + t.getId() + " w " + t.getCurrency()
                        + " bez przeliczenia na PLN - nie wchodzi poprawnie do sum");
            }
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

        return new MonthlyReport(
                month,
                scope.name(),
                money(expenses),
                money(income),
                money(income - expenses),
                byCategory.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> money(e.getValue()),
                                (a, b) -> a, () -> new EnumMap<>(Category.class))),
                money(transfers),
                uncategorized,
                warnings);
    }
}
