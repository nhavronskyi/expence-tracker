package pl.havronskyi.finance.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.havronskyi.finance.domain.CategorySource;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.repo.AccountRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DELIBERATELY WITHOUT AN LLM.
 *
 * Internal transfers determine the correctness of every monthly total. A language
 * model gives no guarantee of global consistency - it might call a transfer internal
 * once and not the next time, and the totals would stop adding up with no error
 * signal. So this is governed by the registry of your own IBANs and amount pairing.
 * The LLM only ever receives external expenses.
 */
@Service
public class TransferMatcher {

    private static final Logger log = LoggerFactory.getLogger(TransferMatcher.class);

    private final AccountRepository accounts;
    private final TxnRepository txns;
    private final FinanceProperties props;

    public TransferMatcher(AccountRepository accounts, TxnRepository txns, FinanceProperties props) {
        this.accounts = accounts;
        this.txns = txns;
        this.props = props;
    }

    /**
     * Step 1 - deterministic: the counterparty is one of my own accounts.
     * Works immediately, even when the other leg hasn't been imported yet.
     */
    public boolean markIfOwnIban(Txn txn) {
        String iban = normalize(txn.getCounterpartyIban());
        if (iban.isBlank()) return false;

        Set<String> ownIbans = accounts.findAll().stream()
                .map(a -> normalize(a.getIban()))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (ownIbans.contains(iban)) {
            txn.setKind(TxnKind.INTERNAL_TRANSFER);
            txn.setCategorySource(CategorySource.IBAN);
            txn.setNeedsReview(false);
            return true;
        }
        return false;
    }

    /**
     * Step 2 - pairing legs. Opposite amounts on two of my own accounts
     * within a window of a few days get a shared transferGroup.
     * Needed because credit card exports often don't include the other side's IBAN.
     */
    public void pairLegs(List<Txn> batch) {
        int window = props.transferMatchWindowDays();

        for (Txn txn : batch) {
            if (txn.getTransferGroup() != null) continue;
            if (txn.getKind() != TxnKind.INTERNAL_TRANSFER) continue;

            List<Txn> candidates = txns.findTransferCandidates(
                    -txn.getAmountMinor(),
                    txn.getAccountId(),
                    txn.getTxnDate().minusDays(window),
                    txn.getTxnDate().plusDays(window));

            if (candidates.isEmpty()) {
                // One leg without a pair: either the other statement hasn't been
                // uploaded yet, or this isn't actually an internal transfer. We don't guess.
                log.info("Przelew wewnetrzny bez pary: txn={} kwota={} data={}",
                        txn.getId(), txn.getAmountMinor(), txn.getTxnDate());
                continue;
            }
            if (candidates.size() > 1) {
                log.warn("Niejednoznaczne parowanie ({} kandydatow) dla txn={} - do reczej decyzji",
                        candidates.size(), txn.getId());
                txn.setNeedsReview(true);
                continue;
            }

            Txn other = candidates.get(0);
            UUID group = UUID.randomUUID();
            txn.setTransferGroup(group);
            other.setTransferGroup(group);
            other.setKind(TxnKind.INTERNAL_TRANSFER);
            other.setCategorySource(CategorySource.IBAN);
            txns.save(other);
        }
    }

    private static String normalize(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s", "").toUpperCase();
    }
}
