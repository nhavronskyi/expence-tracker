package pl.havronskyi.finance.web;

import org.springframework.web.bind.annotation.GetMapping;
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
                .map(a -> new AccountDto(a.getId(), a.getLabel(), a.getIban(), a.getScope(), a.getType(), a.getCurrency()))
                .toList();
    }
}
