package pl.havronskyi.finance.pipeline;

import org.junit.jupiter.api.Test;
import pl.havronskyi.finance.domain.Account;
import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.domain.AccountType;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.ingest.StatementParser;
import pl.havronskyi.finance.llm.LlmCategorizer;
import pl.havronskyi.finance.repo.AccountRepository;
import pl.havronskyi.finance.repo.ImportBatchRepository;
import pl.havronskyi.finance.repo.RawTransactionRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportServiceTest {

    private static final Long WORKSPACE_ID = 1L;

    private static Account account(String iban) {
        Account a = new Account();
        a.setWorkspaceId(WORKSPACE_ID);
        a.setIban(iban);
        a.setLabel("acc");
        a.setScope(AccountScope.PERSONAL);
        a.setType(AccountType.CURRENT);
        a.setCurrency("PLN");
        return a;
    }

    private static Txn expense(long accountId, long amountMinor, String counterpartyIban, String category) {
        Txn t = new Txn();
        t.setWorkspaceId(WORKSPACE_ID);
        t.setAccountId(accountId);
        t.setTxnDate(LocalDate.of(2026, 8, 5));
        t.setAmountMinor(amountMinor);
        t.setCurrency("PLN");
        t.setCounterpartyIban(counterpartyIban);
        t.setKind(amountMinor >= 0 ? TxnKind.INCOME : TxnKind.EXPENSE);
        t.setCategory(category);
        t.setDedupKey("txn-" + accountId + "-" + amountMinor + "-" + counterpartyIban);
        return t;
    }

    /**
     * Reproduces the exact bug that motivated this method: a transaction imported before its
     * counterparty account was registered stays a plain EXPENSE forever unless reclassified.
     */
    @Test
    void reclassifiesTxnWhoseCounterpartyIsNowAKnownAccount() {
        String savingsIban = "68124023821111001143214426";
        Account savings = account(savingsIban);

        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(savings));

        TxnRepository txns = mock(TxnRepository.class);
        Txn misclassified = expense(2L, -300000, savingsIban, "SAVINGS");
        when(txns.findByWorkspaceIdAndKindIn(WORKSPACE_ID, List.of(TxnKind.EXPENSE, TxnKind.INCOME)))
                .thenReturn(List.of(misclassified));
        when(txns.findTransferCandidates(WORKSPACE_ID, 300000L, 2L,
                misclassified.getTxnDate().minusDays(3), misclassified.getTxnDate().plusDays(3)))
                .thenReturn(List.of());

        FinanceProperties props = new FinanceProperties("PLN", 3, BigDecimal.valueOf(0.8), null, null);
        TransferMatcher transferMatcher = new TransferMatcher(accounts, txns, props);

        ImportService importService = new ImportService(
                mock(StatementParser.class),
                mock(ImportBatchRepository.class),
                mock(RawTransactionRepository.class),
                txns,
                mock(ReviewItemRepository.class),
                mock(MerchantNormalizer.class),
                transferMatcher,
                mock(RuleEngine.class),
                mock(LlmCategorizer.class),
                props,
                mock(ExchangeRateService.class),
                mock(ImportJobRegistry.class));

        int reclassified = importService.reclassifyTransfers(WORKSPACE_ID);

        assertEquals(1, reclassified);
        assertEquals(TxnKind.INTERNAL_TRANSFER, misclassified.getKind());
        assertNull(misclassified.getCategory());
    }
}
