package pl.havronskyi.finance.web;

import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.domain.AccountType;

public record AccountDto(Long id, String label, String iban, AccountScope scope, AccountType type, String currency) {
}
