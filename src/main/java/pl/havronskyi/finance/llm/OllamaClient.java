package pl.havronskyi.finance.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import pl.havronskyi.finance.ingest.FinanceProperties;

@Component
public class OllamaClient {

    private final ChatClient chatClient;
    private final FinanceProperties props;

    public OllamaClient(ChatClient.Builder chatClientBuilder, FinanceProperties props) {
        this.chatClient = chatClientBuilder.build();
        this.props = props;
    }

    /**
     * Returns the model's raw response text (we expect JSON).
     */
    public String complete(String system, String userPrompt) {
        if (props.llm().apiKey() == null || props.llm().apiKey().isBlank()) {
            throw new IllegalStateException("OLLAMA_API_KEY nie ustawiony");
        }

        try {
            return chatClient.prompt()
                    .system(system)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            throw new IllegalStateException("Blad wywolania Ollama Cloud API", e);
        }
    }
}
