package pl.havronskyi.finance.web;

import pl.havronskyi.finance.domain.AccountScope;
import pl.havronskyi.finance.domain.AccountType;

public record NewAccountRequest(String iban, String label, AccountScope scope, AccountType type, String currency) {
}
