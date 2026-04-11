package com.example.WebChat.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Configuration
public class DownstreamHttpClientConfig {
    @Bean
    public ClientHttpRequestInterceptor requestIdClientHttpRequestInterceptor() {
        return this::intercept;
    }

    @Bean
    public RestTemplateCustomizer requestIdRestTemplateCustomizer(ClientHttpRequestInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    @Bean
    public RestClient.Builder restClientBuilder(ClientHttpRequestInterceptor interceptor) {
        return RestClient.builder().requestInterceptor(interceptor);
    }

    private ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String requestId = RequestTracking.currentRequestId();
        if (requestId != null && !requestId.isBlank()) {
            request.getHeaders().set(RequestTracking.REQUEST_ID_HEADER, requestId);
        }

        long start = System.nanoTime();
        String method = request.getMethod().name();
        String url = request.getURI().getScheme() + "://" + request.getURI().getHost() + request.getURI().getPath();
        if (log.isDebugEnabled()) {
            log.debug("downstream_call_start requestId={} method={} url={}", requestId, method, url);
        }

        try {
            ClientHttpResponse response = execution.execute(request, body);
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.debug(
                    "downstream_call_end requestId={} method={} url={} status={} durationMs={}",
                    requestId,
                    method,
                    url,
                    response.getStatusCode().value(),
                    durationMs
            );
            return response;
        } catch (IOException | RuntimeException ex) {
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.warn(
                    "downstream_call_fail requestId={} method={} url={} durationMs={} exception={}",
                    requestId,
                    method,
                    url,
                    durationMs,
                    ex.getClass().getSimpleName()
            );
            throw ex;
        }
    }
}
