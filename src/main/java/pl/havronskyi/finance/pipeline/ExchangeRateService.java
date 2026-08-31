package pl.havronskyi.finance.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * NBP Table A mid rates. Deliberately not sharing the RestClient.Builder bean from
 * OllamaCloudConfig - that one carries an Ollama Cloud Bearer auth header that has no
 * business going to a public NBP endpoint.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final int LOOKBACK_DAYS = 7;

    private final RestClient rest = RestClient.builder()
            .baseUrl("https://api.nbp.pl")
            .build();

    private final ConcurrentMap<String, BigDecimal> cache = new ConcurrentHashMap<>();

    /**
     * Converts a minor-unit amount in the given currency to minor-unit PLN using the most
     * recent NBP mid rate published on or before txnDate. Returns null (never guesses) if no
     * rate can be found - the caller leaves amountPlnMinor unset, and the stats warning stays
     * accurate.
     */
    public Long toPlnMinor(String currency, LocalDate txnDate, long amountMinor) {
        BigDecimal rate = midRateOnOrBefore(currency, txnDate);
        if (rate == null) return null;
        return BigDecimal.valueOf(amountMinor)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BigDecimal midRateOnOrBefore(String currency, LocalDate date) {
        String key = currency.toUpperCase() + "@" + date;
        BigDecimal cached = cache.get(key);
        if (cached != null) return cached;

        LocalDate from = date.minusDays(LOOKBACK_DAYS);
        try {
            JsonNode root = rest.get()
                    .uri("/api/exchangerates/rates/a/{code}/{from}/{to}/?format=json",
                            currency.toLowerCase(), from, date)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) return null;

            JsonNode rates = root.path("rates");
            if (!rates.isArray() || rates.isEmpty()) return null;

            JsonNode latest = rates.get(rates.size() - 1);
            BigDecimal mid = new BigDecimal(latest.path("mid").asText());
            cache.put(key, mid);
            return mid;
        } catch (RestClientException e) {
            log.warn("Brak kursu NBP dla {} w oknie do {}: {}", currency, date, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Nie udalo sie sparsowac kursu NBP dla {} na {}: {}", currency, date, e.getMessage());
            return null;
        }
    }
}
