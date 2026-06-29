package com.tara.bottleneck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used to call the upstream HTTP API in the
 * "upstream bottleneck" demo.
 *
 * The base URL points at postman-echo.com by default, which exposes the same
 * handy endpoints as httpbin but is far more reliable under repeated calls:
 *   GET /get          -> returns immediately (the "fast upstream")
 *   GET /delay/{secs} -> sleeps server-side before responding (the "slow upstream")
 *
 * (We moved off the public httpbin.org because it frequently returns 503s when
 * hit repeatedly. Override with demo.upstream.base-url to point anywhere else,
 * e.g. a local stub.)
 *
 * When the OpenTelemetry Java agent is attached, every outbound call made
 * through this client is auto-instrumented as a CLIENT span, so the slow
 * upstream call shows up as the dominant bar in the Grafana Cloud trace.
 */
@Configuration
public class UpstreamClientConfig {

    @Bean
    public RestClient upstreamRestClient(@Value("${demo.upstream.base-url:https://postman-echo.com}") String baseUrl) {
        return RestClient.create(baseUrl);
    }
}
