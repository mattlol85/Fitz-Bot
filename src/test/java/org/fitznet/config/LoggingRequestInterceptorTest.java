package org.fitznet.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoggingRequestInterceptorTest {

    private LoggingRequestInterceptor interceptor;

    @Mock
    private ClientHttpRequestExecution execution;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new LoggingRequestInterceptor();
    }

    @Test
    void testInterceptor_PassesThroughResponse() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://localhost:7878/api/v3/movie/lookup?term=Dune"));
        request.getHeaders().set("X-Api-Key", "secret-key");

        byte[] responseBody = "{\"title\":\"Dune\"}".getBytes(StandardCharsets.UTF_8);
        MockClientHttpResponse mockResponse = new MockClientHttpResponse(responseBody, HttpStatus.OK);

        when(execution.execute(any(), any())).thenReturn(mockResponse);

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(execution, times(1)).execute(any(), any());
    }

    @Test
    void testInterceptor_WithRequestBody() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
                URI.create("http://localhost:7878/api/v3/movie"));
        request.getHeaders().set("X-Api-Key", "secret-key");
        request.getHeaders().set("Content-Type", "application/json");

        byte[] requestBody = "{\"tmdbId\":438631}".getBytes(StandardCharsets.UTF_8);
        byte[] responseBody = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        MockClientHttpResponse mockResponse = new MockClientHttpResponse(responseBody, HttpStatus.CREATED);

        when(execution.execute(any(), any())).thenReturn(mockResponse);

        ClientHttpResponse response = interceptor.intercept(request, requestBody, execution);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void testInterceptor_PropagatesIOException() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://localhost:7878/api/v3/queue/details"));

        when(execution.execute(any(), any())).thenThrow(new IOException("Connection refused"));

        assertThrows(IOException.class,
                () -> interceptor.intercept(request, new byte[0], execution));
    }

    @Test
    void testInterceptor_EmptyResponseBody() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://localhost:8989/api/v3/queue/details"));
        request.getHeaders().set("X-Api-Key", "sonarr-secret");

        MockClientHttpResponse mockResponse = new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        when(execution.execute(any(), any())).thenReturn(mockResponse);

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
