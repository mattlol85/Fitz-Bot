package org.fitznet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Logs all outbound API requests and their responses.
 *
 * <p>Summary lines (method, URL, status, duration) are logged at INFO.
 * Request/response bodies are logged at DEBUG to avoid noise in production.
 * The {@code X-Api-Key} header value is masked to prevent secret leakage.
 */
@Slf4j
public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final int MAX_BODY_LOG_BYTES = 4096;
    private static final String MASKED = "***";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        long start = System.currentTimeMillis();

        logRequest(request, body);

        ClientHttpResponse response = execution.execute(request, body);

        long elapsed = System.currentTimeMillis() - start;
        logResponse(request, response, elapsed);

        return response;
    }

    // ── request ──────────────────────────────────────────────────────────────────

    private void logRequest(HttpRequest request, byte[] body) {
        log.info("→ {} {}", request.getMethod(), request.getURI());

        if (log.isDebugEnabled()) {
            log.debug("  Request headers: {}", maskedHeaders(request.getHeaders().toSingleValueMap()));

            if (body != null && body.length > 0) {
                String bodyStr = truncate(new String(body, StandardCharsets.UTF_8));
                log.debug("  Request body: {}", bodyStr);
            }
        }
    }

    // ── response ─────────────────────────────────────────────────────────────────

    private void logResponse(HttpRequest request, ClientHttpResponse response, long elapsedMs) throws IOException {
        int statusCode = response.getStatusCode().value();

        log.info("← {} {} → {} {} ({}ms)",
                request.getMethod(), request.getURI(),
                statusCode, response.getStatusText(),
                elapsedMs);

        if (log.isDebugEnabled()) {
            log.debug("  Response headers: {}", response.getHeaders().toSingleValueMap());

            byte[] responseBody = readBody(response.getBody());
            if (responseBody.length > 0) {
                log.debug("  Response body: {}", truncate(new String(responseBody, StandardCharsets.UTF_8)));
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Map<String, String> maskedHeaders(Map<String, String> headers) {
        headers.replaceAll((key, value) -> "X-Api-Key".equalsIgnoreCase(key) ? MASKED : value);
        return headers;
    }

    private byte[] readBody(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int bytesRead;
        while ((bytesRead = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private String truncate(String text) {
        if (text.length() <= MAX_BODY_LOG_BYTES) return text;
        return text.substring(0, MAX_BODY_LOG_BYTES) + " … [truncated]";
    }
}
