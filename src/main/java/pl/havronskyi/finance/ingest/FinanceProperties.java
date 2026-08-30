package pl.havronskyi.finance.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "finance")
public record FinanceProperties(
        String baseCurrency,
        int transferMatchWindowDays,
        BigDecimal llmConfidenceThreshold,
        Anthropic anthropic,
        Pekao pekao
) {
    public record Anthropic(String apiKey, String model, int batchSize) {
    }

    /**
     * Column mapping lives in configuration, not in code - Pekao's export format
     * isn't publicly documented and may differ between products.
     */
    public record Pekao(
            String charset,
            String delimiter,
            String dateFormat,
            Map<String, String> columns
    ) {
    }
}
