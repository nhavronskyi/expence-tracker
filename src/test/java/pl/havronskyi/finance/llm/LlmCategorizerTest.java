package pl.havronskyi.finance.llm;

import org.junit.jupiter.api.Test;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.repo.CategoryRepository;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LlmCategorizerTest {

    private static final Set<String> VALID_CODES = Set.of("PAYMENTS", "APARTMENTS", "INVESTMENTS",
            "HEALTH", "SAVINGS");

    private final LlmCategorizer categorizer =
            new LlmCategorizer(mock(OllamaClient.class), mock(FinanceProperties.class), mock(CategoryRepository.class));

    @Test
    void parsesAWellFormedResponse() {
        String raw = """
                {"results":[{"id":2288,"ranked":[{"category":"HEALTH","confidence":0.91,"reason":"Lux Med clinic payment"}]}]}""";

        List<CategorySuggestion> result = categorizer.parse(raw, VALID_CODES);

        assertEquals(1, result.size());
        assertEquals(2288L, result.get(0).txnId());
        assertEquals("HEALTH", result.get(0).best().category());
    }

    /**
     * Reproduces a real production response where the model injected a stray '"""},' token
     * inside transaction 2287's "ranked" array. Before this fix, mapper.readTree(...) on the
     * whole response threw and every transaction's suggestion - including the well-formed
     * ones - was discarded, so the entire review queue showed no AI suggestions at all.
     */
    @Test
    void salvagesWellFormedEntriesWhenOneEntryIsCorrupted() {
        String strayToken = "\"" + "\"" + "\""; // the literal '"""' the model injected mid-array
        String raw = "{\"results\":["
                + "{\"id\":2286,\"ranked\":[{\"category\":\"PAYMENTS\",\"confidence\":0.62,\"reason\":\"late fee, generic non-housing charge\"}]},"
                + "{\"id\":2287,\"ranked\":[{\"category\":\"PAYMENTS\",\"confidence\":0.61,\"reason\":\"small late fee, generic\"}," + strayToken
                + "},{\"category\":\"APARTMENTS\",\"confidence\":0.26,\"reason\":\"possible housing charge\"}]},"
                + "{\"id\":2288,\"ranked\":[{\"category\":\"HEALTH\",\"confidence\":0.91,\"reason\":\"Lux Med clinic payment\"}]},"
                + "{\"id\":2295,\"ranked\":[{\"category\":\"INVESTMENTS\",\"confidence\":0.9,\"reason\":\"XTB brokerage transaction\"}]}"
                + "]}";

        List<CategorySuggestion> result = categorizer.parse(raw, VALID_CODES);

        assertEquals(3, result.size(), "the three well-formed entries should survive");
        assertTrue(result.stream().anyMatch(s -> s.txnId() == 2286L && "PAYMENTS".equals(s.best().category())));
        assertTrue(result.stream().anyMatch(s -> s.txnId() == 2288L && "HEALTH".equals(s.best().category())));
        assertTrue(result.stream().anyMatch(s -> s.txnId() == 2295L && "INVESTMENTS".equals(s.best().category())));
        assertTrue(result.stream().noneMatch(s -> s.txnId() == 2287L), "the corrupted entry cannot be recovered");
    }

    @Test
    void throwsWhenNothingCanBeSalvaged() {
        String raw = "not json at all";

        try {
            categorizer.parse(raw, VALID_CODES);
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Nieparsowalna odpowiedz modelu"));
        }
    }
}
