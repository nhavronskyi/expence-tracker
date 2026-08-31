package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.Account;
import pl.havronskyi.finance.pipeline.ImportService;
import pl.havronskyi.finance.repo.AccountRepository;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accounts;
    private final ImportService importService;

    public AccountController(AccountRepository accounts, ImportService importService) {
        this.accounts = accounts;
        this.importService = importService;
    }

    @GetMapping
    public List<AccountDto> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return accounts.findAll().stream()
                .filter(a -> includeInactive || a.isActive())
                .sorted(Comparator.comparing(Account::getLabel))
                .map(this::toDto)
                .toList();
    }

    /**
     * Registering an account is also the moment any already-imported transaction pointing
     * at its IBAN (imported before this account existed) can finally be recognized as an
     * internal transfer - so every creation re-runs the same reclassification the manual
     * "Reclassify internal transfers" button triggers, keeping totals right without a
     * separate step to remember.
     */
    @PostMapping
    public ResponseEntity<NewAccountResponse> create(@RequestBody NewAccountRequest req) {
        if (req.label() == null || req.label().isBlank() || req.scope() == null || req.type() == null) {
            return ResponseEntity.badRequest().build();
        }

        Account a = new Account();
        a.setLabel(req.label().trim());
        a.setIban(normalizeIban(req.iban()));
        a.setScope(req.scope());
        a.setType(req.type());
        a.setCurrency(normalizeCurrency(req.currency()));
        a.setActive(true);

        return ResponseEntity.ok(saveAndReclassify(a));
    }

    /**
     * Editing an account's IBAN has the same retroactive-correctness need as registering
     * a new one: a typo'd IBAN gets fixed here, and any transaction that was waiting on it
     * (imported before the fix) is caught by the same reclassification create() already
     * runs. This is also how deactivate/reactivate happens - one PATCH, no separate
     * toggle endpoint, since "active" is just another editable field.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<NewAccountResponse> update(@PathVariable Long id, @RequestBody UpdateAccountRequest req) {
        if (req.label() == null || req.label().isBlank() || req.scope() == null || req.type() == null) {
            return ResponseEntity.badRequest().build();
        }
        Account a = accounts.findById(id).orElse(null);
        if (a == null) {
            return ResponseEntity.notFound().build();
        }

        String iban = normalizeIban(req.iban());
        if (iban != null && accounts.findByIban(iban).filter(other -> !other.getId().equals(id)).isPresent()) {
            return ResponseEntity.status(409).build();
        }

        a.setLabel(req.label().trim());
        a.setIban(iban);
        a.setScope(req.scope());
        a.setType(req.type());
        a.setCurrency(normalizeCurrency(req.currency()));
        a.setActive(req.active());

        return ResponseEntity.ok(saveAndReclassify(a));
    }

    private NewAccountResponse saveAndReclassify(Account a) {
        Account saved = accounts.save(a);
        int reclassified = importService.reclassifyTransfers();
        return new NewAccountResponse(toDto(saved), reclassified);
    }

    private static String normalizeIban(String iban) {
        return iban == null || iban.isBlank() ? null : iban.trim();
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "PLN" : currency.trim().toUpperCase();
    }

    private AccountDto toDto(Account a) {
        return new AccountDto(a.getId(), a.getLabel(), a.getIban(), a.getScope(), a.getType(), a.getCurrency(),
                a.isActive());
    }
}
