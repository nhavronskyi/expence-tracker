package pl.havronskyi.finance.web;

public record NewAccountResponse(AccountDto account, int reclassifiedTransfers) {
}
