package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.Account;
import pl.havronskyi.finance.repo.AccountRepository;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AccountDto> list() {
        return accounts.findAll().stream()
                .filter(Account::isActive)
                .sorted(Comparator.comparing(Account::getLabel))
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<AccountDto> create(@RequestBody NewAccountRequest req) {
        if (req.label() == null || req.label().isBlank() || req.scope() == null || req.type() == null) {
            return ResponseEntity.badRequest().build();
        }

        Account a = new Account();
        a.setLabel(req.label().trim());
        a.setIban(req.iban() == null || req.iban().isBlank() ? null : req.iban().trim());
        a.setScope(req.scope());
        a.setType(req.type());
        a.setCurrency(req.currency() == null || req.currency().isBlank() ? "PLN" : req.currency().trim().toUpperCase());
        a.setActive(true);

        return ResponseEntity.ok(toDto(accounts.save(a)));
    }

    private AccountDto toDto(Account a) {
        return new AccountDto(a.getId(), a.getLabel(), a.getIban(), a.getScope(), a.getType(), a.getCurrency());
    }
}
