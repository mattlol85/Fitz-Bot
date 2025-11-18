package org.fitznet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for REST client beans.
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates a RestTemplate bean for making HTTP requests.
     *
     * @return RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

