package pl.havronskyi.finance.stats;

import org.junit.jupiter.api.Test;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;
import pl.havronskyi.finance.repo.TxnRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private static final Long WORKSPACE_ID = 1L;

    private static Txn transfer(long accountId, long amountMinor, UUID transferGroup) {
        Txn t = new Txn();
        t.setWorkspaceId(WORKSPACE_ID);
        t.setAccountId(accountId);
        t.setTxnDate(LocalDate.of(2026, 8, 15));
        t.setAmountMinor(amountMinor);
        t.setCurrency("PLN");
        t.setKind(TxnKind.INTERNAL_TRANSFER);
        t.setTransferGroup(transferGroup);
        t.setDedupKey("txn-" + accountId + "-" + amountMinor);
        return t;
    }

    @Test
    void pairedTransferAcrossDifferentAccountsDoesNotWarnAsOrphaned() {
        UUID group = UUID.randomUUID();
        // Both legs of the same transfer, on two different accounts (e.g. formerly
        // different PERSONAL/BUSINESS scopes) - already paired via transferGroup.
        Txn outgoing = transfer(1L, -50000, group);
        Txn incoming = transfer(2L, 50000, group);

        TxnRepository txns = mock(TxnRepository.class);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(txns.findByWorkspaceIdAndTxnDateBetween(WORKSPACE_ID, from, to)).thenReturn(List.of(outgoing, incoming));

        StatsService service = new StatsService(txns);
        PeriodReport report = service.forRange(WORKSPACE_ID, from, to);

        assertTrue(report.warnings().isEmpty(),
                "Paired transfer across accounts must not be reported as orphaned");
        assertEquals(0, report.totalExpenses().compareTo(java.math.BigDecimal.ZERO));
        assertEquals(0, report.totalIncome().compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    void unpairedTransferStillWarnsAsOrphaned() {
        Txn outgoing = transfer(1L, -50000, null);

        TxnRepository txns = mock(TxnRepository.class);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(txns.findByWorkspaceIdAndTxnDateBetween(WORKSPACE_ID, from, to)).thenReturn(List.of(outgoing));

        StatsService service = new StatsService(txns);
        PeriodReport report = service.forRange(WORKSPACE_ID, from, to);

        assertEquals(1, report.warnings().size());
        assertTrue(report.warnings().get(0).contains("bez pary"));
    }
}
