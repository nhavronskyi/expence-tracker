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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public List<CategorySuggestion> classify(List<Txn> txns, Long workspaceId, IntConsumer onProgress,
                                              BooleanSupplier cancelled) {
        List<Category> active = categories.findByWorkspaceIdAndActiveTrueOrderByLabelAsc(workspaceId);
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

    private static final Pattern ENTRY_START =
            Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\\d+\\s*,\\s*\"ranked\"\\s*:\\s*\\[");
    private static final Pattern ENTRY_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    List<CategorySuggestion> parse(String raw, Set<String> validCodes) {
        String cleaned = raw.trim()
                .replaceAll("^```(json)?", "")
                .replaceAll("```$", "")
                .trim();
        try {
            List<CategorySuggestion> result = new ArrayList<>();
            for (JsonNode node : mapper.readTree(cleaned).path("results")) {
                result.add(toSuggestion(node, validCodes));
            }
            return result;
        } catch (Exception e) {
            List<CategorySuggestion> salvaged = salvageEntries(cleaned, validCodes);
            if (salvaged.isEmpty()) {
                throw new IllegalStateException("Nieparsowalna odpowiedz modelu: " + cleaned, e);
            }
            log.warn("Czesciowo uszkodzona odpowiedz modelu, odzyskano {} wpisow: {}",
                    salvaged.size(), e.getMessage());
            return salvaged;
        }
    }

    private CategorySuggestion toSuggestion(JsonNode node, Set<String> validCodes) {
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
        return new CategorySuggestion(id, ranked);
    }

    /**
     * The model occasionally emits one malformed entry inside an otherwise valid response
     * (a stray token breaking that entry's "ranked" array). Parsing the whole response as one
     * JSON tree used to discard every transaction's answer when this happened; this recovers
     * every entry that is independently well-formed instead of throwing all of them away.
     */
    private List<CategorySuggestion> salvageEntries(String cleaned, Set<String> validCodes) {
        List<Integer> anchors = new ArrayList<>();
        Matcher starts = ENTRY_START.matcher(cleaned);
        while (starts.find()) {
            anchors.add(starts.start());
        }

        List<CategorySuggestion> result = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            int open = anchors.get(i);
            int cap = (i + 1 < anchors.size()) ? anchors.get(i + 1) : cleaned.length();
            int end = findMatchingEnd(cleaned, open, cap);
            if (end < 0) continue;
            String chunk = cleaned.substring(open, end + 1);
            try {
                result.add(toSuggestion(mapper.readTree(chunk), validCodes));
            } catch (Exception ex) {
                Matcher idMatch = ENTRY_ID.matcher(chunk);
                log.warn("Pomijam nieparsowalny wpis kategoryzacji (id={}): {}",
                        idMatch.find() ? idMatch.group(1) : "?", ex.getMessage());
            }
        }
        return result;
    }

    /**
     * Index of the '}' that closes the JSON object starting at {@code open}, treating string
     * contents (including escaped quotes) as opaque so stray brackets inside a corrupted string
     * don't affect bracket depth. Bounded by {@code cap} so one malformed entry can never bleed
     * into the next entry's span.
     */
    private static int findMatchingEnd(String s, int open, int cap) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = open; i < cap; i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
