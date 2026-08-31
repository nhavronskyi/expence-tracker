package pl.havronskyi.finance.llm;

import java.math.BigDecimal;

public record Suggestion(String category, BigDecimal confidence, String reason) {
}
