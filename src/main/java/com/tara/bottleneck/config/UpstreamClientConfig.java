package com.tara.bottleneck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used to call the upstream HTTP API in the
 * "upstream bottleneck" demo.
 *
 * The base URL points at httpbin.org by default, which exposes handy endpoints:
 *   GET /get          -> returns immediately (the "fast upstream")
 *   GET /delay/{secs} -> sleeps server-side before responding (the "slow upstream")
 *
 * When the OpenTelemetry Java agent is attached, every outbound call made
 * through this client is auto-instrumented as a CLIENT span, so the slow
 * upstream call shows up as the dominant bar in the Grafana Cloud trace.
 */
@Configuration
public class UpstreamClientConfig {

    @Bean
    public RestClient upstreamRestClient(@Value("${demo.upstream.base-url:https://httpbin.org}") String baseUrl) {
        return RestClient.create(baseUrl);
    }
}
