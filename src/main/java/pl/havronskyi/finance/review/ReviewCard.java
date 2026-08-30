package pl.havronskyi.finance.review;

public record ReviewCard(Long reviewId, Long txnId, String question, String suggestionsJson) {
}
