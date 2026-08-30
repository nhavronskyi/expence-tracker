package pl.havronskyi.finance.llm;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.havronskyi.finance.ingest.FinanceProperties;

import java.time.Duration;

/**
 * Spring AI's Ollama auto-configuration picks up a {@link RestClient.Builder} bean from the
 * context instead of building its own default one - this is how the Bearer token for Ollama
 * Cloud gets attached, since Spring AI has no built-in api-key/headers property for Ollama.
 */
@Configuration
public class OllamaCloudConfig {

    @Bean
    public RestClient.Builder ollamaRestClientBuilder(FinanceProperties props) {
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(10))
                        .withReadTimeout(Duration.ofSeconds(120)));

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(props.llm().apiKey());
                    return execution.execute(request, body);
                });
    }
}
