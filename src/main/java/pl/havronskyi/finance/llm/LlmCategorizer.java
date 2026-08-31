package pl.havronskyi.finance.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.havronskyi.finance.domain.Category;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.ingest.FinanceProperties;
import pl.havronskyi.finance.repo.CategoryRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * The LLM only ever sees external expenses. It doesn't decide on internal transfers
 * or amounts - only on category. It always returns a top-3 with reasoning,
 * so the review queue has something to offer as buttons.
 */
@Service
public class LlmCategorizer {

    private static final Logger log = LoggerFactory.getLogger(LlmCategorizer.class);

    private static final String SYSTEM = """
            You classify Polish bank transactions into a fixed set of personal finance categories.

            Categories:
            %s

            Rules:
            - Answer with JSON only. No prose, no markdown fences.
            - Format: {"results":[{"id":<int>,"ranked":[{"category":"<CODE>","confidence":<0..1>,"reason":"<max 12 words>"}]}]}
            - Provide up to 3 ranked candidates per transaction, best first.
            - confidence is your honest probability the top choice is correct. Do not inflate it.
            - If the merchant is unrecognisable or the description is a generic transfer title,
              return your best guess with confidence below 0.5 rather than inventing certainty.
            - Merchant names are Polish. FRIDGE covers supermarkets (Biedronka, Lidl, Zabka, Carrefour,
              Auchan, Kaufland). RESTAURANTS covers on-site eating. DELIVERY covers Glovo, Pyszne, Bolt Food.
            - Never output a category outside the list.
            """;

    private final OllamaClient client;
    private final FinanceProperties props;
    private final CategoryRepository categories;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmCategorizer(OllamaClient client, FinanceProperties props, CategoryRepository categories) {
        this.client = client;
        this.props = props;
        this.categories = categories;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    public List<CategorySuggestion> classify(List<Txn> txns, IntConsumer onProgress, BooleanSupplier cancelled) {
        List<Category> active = categories.findByActiveTrueOrderByLabelAsc();
        String catalog = active.stream()
                .map(c -> "- " + c.getCode() + " (" + c.getLabel() + "): " + c.getDefinition())
                .collect(Collectors.joining("\n"));
        Set<String> validCodes = active.stream()
                .map(c -> c.getCode().toUpperCase())
                .collect(Collectors.toSet());

        List<CategorySuggestion> out = new ArrayList<>();
        int batchSize = Math.max(1, props.llm().batchSize());
        int processed = 0;

        for (int i = 0; i < txns.size(); i += batchSize) {
            if (cancelled.getAsBoolean()) {
                log.info("Kategoryzacja anulowana po {} z {}", processed, txns.size());
                break;
            }
            List<Txn> chunk = txns.subList(i, Math.min(txns.size(), i + batchSize));
            try {
                out.addAll(classifyChunk(chunk, catalog, validCodes));
            } catch (RuntimeException e) {
                // No answer != wrong category. These transactions go to the review queue.
                log.error("Batch {} nieudany, transakcje trafia do review: {}", i, e.getMessage());
            }
            processed += chunk.size();
            onProgress.accept(processed);
        }
        return out;
    }

    private List<CategorySuggestion> classifyChunk(List<Txn> chunk, String catalog, Set<String> validCodes) {
        String payload = chunk.stream()
                .map(t -> "{\"id\":%d,\"merchant\":\"%s\",\"description\":\"%s\",\"amount\":%s,\"currency\":\"%s\",\"date\":\"%s\"}"
                        .formatted(t.getId(),
                                escape(t.getMerchantNorm()),
                                escape(t.getDescription()),
                                new BigDecimal(t.getAmountMinor()).movePointLeft(2).toPlainString(),
                                t.getCurrency(),
                                t.getTxnDate()))
                .collect(Collectors.joining(",\n"));

        String system = SYSTEM.formatted(catalog);
        String raw = client.complete(system, "Transactions:\n[" + payload + "]");
        return parse(raw, validCodes);
    }

    List<CategorySuggestion> parse(String raw, Set<String> validCodes) {
        String cleaned = raw.trim()
                .replaceAll("^```(json)?", "")
                .replaceAll("```$", "")
                .trim();
        try {
            JsonNode root = mapper.readTree(cleaned);
            List<CategorySuggestion> result = new ArrayList<>();
            for (JsonNode node : root.path("results")) {
                long id = node.path("id").asLong();
                List<Suggestion> ranked = new ArrayList<>();
                for (JsonNode r : node.path("ranked")) {
                    String code = r.path("category").asText(null);
                    if (code == null) continue;
                    code = code.trim().toUpperCase();
                    if (!validCodes.contains(code)) continue;
                    ranked.add(new Suggestion(
                            code,
                            BigDecimal.valueOf(r.path("confidence").asDouble(0.0))
                                    .setScale(2, java.math.RoundingMode.HALF_UP),
                            r.path("reason").asText("")));
                }
                result.add(new CategorySuggestion(id, ranked));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Nieparsowalna odpowiedz modelu: " + cleaned, e);
        }
    }
}
