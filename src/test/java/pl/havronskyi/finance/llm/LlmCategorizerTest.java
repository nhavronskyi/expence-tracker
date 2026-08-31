package pl.havronskyi.finance.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.repo.CategoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private static Txn expense(long id) {
        Txn t = new Txn();
        ReflectionTestUtils.setField(t, "id", id);
        t.setWorkspaceId(1L);
        t.setKind(TxnKind.EXPENSE);
        t.setAmountMinor(-1000);
        t.setCurrency("PLN");
        return t;
    }

    /**
     * Batch size is 10% of the pending count (min 1): amortizes the fixed system-prompt cost
     * across a chunk of transactions while still surfacing progress multiple times per import.
     */
    @ParameterizedTest
    @CsvSource({"100,10", "70,7", "37,3", "5,1"})
    void batchesAsTenPercentOfPendingCount(int total, int expectedBatchSize) {
        OllamaClient client = mock(OllamaClient.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        when(categories.findByWorkspaceIdAndActiveTrueOrderByLabelAsc(1L)).thenReturn(List.of());
        when(client.complete(anyString(), anyString())).thenReturn("{\"results\":[]}");
        LlmCategorizer categorizer = new LlmCategorizer(client, mock(FinanceProperties.class), categories);

        List<Txn> txns = new ArrayList<>();
        for (long i = 1; i <= total; i++) txns.add(expense(i));
        categorizer.classify(txns, 1L, p -> {}, () -> false);

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(client, org.mockito.Mockito.atLeastOnce()).complete(anyString(), userPrompt.capture());
        List<String> calls = userPrompt.getAllValues();

        int sum = 0;
        for (int i = 0; i < calls.size(); i++) {
            int chunkSize = calls.get(i).split("\"id\":").length - 1;
            sum += chunkSize;
            boolean isLast = i == calls.size() - 1;
            if (!isLast) {
                assertEquals(expectedBatchSize, chunkSize, "non-final batch should be exactly 10% of the total");
            } else {
                assertTrue(chunkSize > 0 && chunkSize <= expectedBatchSize, "final batch should be a partial or full batch");
            }
        }
        assertEquals(total, sum, "every transaction should be sent exactly once across all batches");
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
