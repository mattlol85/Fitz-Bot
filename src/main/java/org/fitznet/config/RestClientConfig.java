package org.fitznet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Configuration for REST client beans.
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates a RestTemplate bean for making HTTP requests.
     *
     * <p>Wrapped in a {@link BufferingClientHttpRequestFactory} so that the
     * {@link LoggingRequestInterceptor} can read the response body for
     * logging without consuming the stream that the caller also needs.
     *
     * @return RestTemplate instance with request/response logging
     */
    /** Connect timeout for Radarr/Sonarr calls (ms). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Read timeout for Radarr/Sonarr calls (ms). These calls run on the JDA gateway thread,
     *  so an unbounded read could stall the bot's Discord connection. */
    private static final int READ_TIMEOUT_MS = 15_000;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));
        restTemplate.setInterceptors(List.of(new LoggingRequestInterceptor()));
        return restTemplate;
    }
}

