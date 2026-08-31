package pl.havronskyi.finance.pipeline;

import org.junit.jupiter.api.Test;
import pl.havronskyi.finance.domain.Account;
import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.domain.AccountType;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.repo.AccountRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferMatcherTest {

    private static Account account(String iban) {
        Account a = new Account();
        a.setIban(iban);
        a.setLabel("acc");
        a.setScope(AccountScope.PERSONAL);
        a.setType(AccountType.CURRENT);
        a.setCurrency("PLN");
        return a;
    }

    private static Txn expenseTo(String counterpartyIban) {
        Txn t = new Txn();
        t.setAccountId(1L);
        t.setTxnDate(LocalDate.of(2026, 8, 5));
        t.setAmountMinor(-1000);
        t.setCurrency("PLN");
        t.setCounterpartyIban(counterpartyIban);
        t.setDedupKey("txn-" + counterpartyIban);
        return t;
    }

    /**
     * An IBAN registered as someone's own account in workspace B must not make a
     * workspace A transaction to that same IBAN look like an internal transfer.
     */
    @Test
    void doesNotMatchAnIbanRegisteredInAnotherWorkspace() {
        String iban = "68124023821111001143214426";
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findByWorkspaceId(1L)).thenReturn(List.of());
        when(accounts.findByWorkspaceId(2L)).thenReturn(List.of(account(iban)));

        TransferMatcher matcher = new TransferMatcher(accounts, mock(TxnRepository.class),
                new FinanceProperties("PLN", 3, BigDecimal.valueOf(0.8), null, null));

        Txn t = expenseTo(iban);
        assertFalse(matcher.markIfOwnIban(t, 1L));
    }

    @Test
    void matchesAnIbanRegisteredInTheSameWorkspace() {
        String iban = "68124023821111001143214426";
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findByWorkspaceId(1L)).thenReturn(List.of(account(iban)));

        TransferMatcher matcher = new TransferMatcher(accounts, mock(TxnRepository.class),
                new FinanceProperties("PLN", 3, BigDecimal.valueOf(0.8), null, null));

        Txn t = expenseTo(iban);
        assertTrue(matcher.markIfOwnIban(t, 1L));
        assertTrue(t.getKind() == TxnKind.INTERNAL_TRANSFER);
    }
}
