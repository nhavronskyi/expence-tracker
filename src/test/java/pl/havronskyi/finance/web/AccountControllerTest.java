package pl.havronskyi.finance.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import pl.havronskyi.finance.domain.Account;
import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.domain.AccountType;
import pl.havronskyi.finance.pipeline.ImportService;
import pl.havronskyi.finance.repo.AccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {

    private static final Long WORKSPACE_ID = 1L;

    private static Account account(long id, String iban) {
        Account a = new Account();
        ReflectionTestUtils.setField(a, "id", id);
        a.setWorkspaceId(WORKSPACE_ID);
        a.setIban(iban);
        a.setLabel("acc-" + id);
        a.setScope(AccountScope.PERSONAL);
        a.setType(AccountType.CURRENT);
        a.setCurrency("PLN");
        a.setActive(true);
        return a;
    }

    private static UpdateAccountRequest request(String iban) {
        return new UpdateAccountRequest("Savings", iban, AccountScope.PERSONAL, AccountType.CURRENT, "PLN", true);
    }

    /**
     * Fixing a typo'd IBAN must have the same retroactive-correctness effect as
     * registering a brand-new account - any transaction that was waiting on it gets
     * reclassified in the same request.
     */
    @Test
    void updatingIbanTriggersReclassification() {
        Account existing = account(1L, null);
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(1L)).thenReturn(Optional.of(existing));
        when(accounts.findByWorkspaceIdAndIban(WORKSPACE_ID, "NEWIBAN")).thenReturn(Optional.empty());
        when(accounts.save(existing)).thenReturn(existing);

        ImportService importService = mock(ImportService.class);
        when(importService.reclassifyTransfers(WORKSPACE_ID)).thenReturn(2);

        AccountController controller = new AccountController(accounts, importService);
        ResponseEntity<NewAccountResponse> res = controller.update(WORKSPACE_ID, 1L, request("NEWIBAN"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(2, res.getBody().reclassifiedTransfers());
        assertEquals("NEWIBAN", existing.getIban());
        verify(importService).reclassifyTransfers(WORKSPACE_ID);
    }

    @Test
    void updatingToIbanUsedByAnotherAccountReturnsConflict() {
        Account existing = account(1L, "OLDIBAN");
        Account other = account(2L, "TAKENIBAN");
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(1L)).thenReturn(Optional.of(existing));
        when(accounts.findByWorkspaceIdAndIban(WORKSPACE_ID, "TAKENIBAN")).thenReturn(Optional.of(other));

        ImportService importService = mock(ImportService.class);
        AccountController controller = new AccountController(accounts, importService);
        ResponseEntity<NewAccountResponse> res = controller.update(WORKSPACE_ID, 1L, request("TAKENIBAN"));

        assertEquals(409, res.getStatusCode().value());
        verify(importService, never()).reclassifyTransfers(WORKSPACE_ID);
    }

    @Test
    void updatingUnknownAccountReturnsNotFound() {
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        AccountController controller = new AccountController(accounts, mock(ImportService.class));
        ResponseEntity<NewAccountResponse> res = controller.update(WORKSPACE_ID, 99L, request("IBAN"));

        assertEquals(404, res.getStatusCode().value());
    }

    @Test
    void updatingAccountFromAnotherWorkspaceReturnsNotFound() {
        Account existing = account(1L, "OLDIBAN");
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findById(1L)).thenReturn(Optional.of(existing));

        AccountController controller = new AccountController(accounts, mock(ImportService.class));
        ResponseEntity<NewAccountResponse> res = controller.update(999L, 1L, request("NEWIBAN"));

        assertEquals(404, res.getStatusCode().value());
    }
}
