package pl.havronskyi.finance.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import pl.havronskyi.finance.ingest.FinanceProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AnthropicClient {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String VERSION = "2023-06-01";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final FinanceProperties props;

    public AnthropicClient(FinanceProperties props) {
        this.props = props;
    }

    /**
     * Returns the model's raw response text (we expect JSON).
     */
    public String complete(String system, String userPrompt) {
        var cfg = props.anthropic();
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY nie ustawiony");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", cfg.model());
        body.put("max_tokens", 4096);
        body.put("system", system);
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", userPrompt);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("content-type", "application/json")
                    .header("x-api-key", cfg.apiKey())
                    .header("anthropic-version", VERSION)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Anthropic API " + response.statusCode() + ": " + response.body());
            }

            JsonNode content = mapper.readTree(response.body()).path("content");
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return text.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Przerwane wywolanie Anthropic API", e);
        } catch (Exception e) {
            throw new IllegalStateException("Blad wywolania Anthropic API", e);
        }
    }
}
