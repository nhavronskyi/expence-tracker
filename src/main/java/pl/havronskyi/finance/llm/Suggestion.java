package pl.havronskyi.finance.llm;

import pl.havronskyi.finance.domain.Category;

import java.math.BigDecimal;

public record Suggestion(Category category, BigDecimal confidence, String reason) {
}
