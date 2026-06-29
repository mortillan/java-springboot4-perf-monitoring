package com.tara.bottleneck.service;

import com.tara.bottleneck.dto.UpstreamCallResponse;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Demonstrates a bottleneck that lives OUTSIDE this service: a slow upstream
 * HTTP API. The request is split into named steps so each becomes its own span:
 *
 *   validate-request -> ~0 ms
 *   call-upstream    -> HUGE for the slow path (the auto-instrumented HTTP
 *                       CLIENT span nests here), tiny for the fast path
 *   process-response -> a few ms
 *
 * The takeaway for the demo: the app's own code is fast. The trace waterfall
 * makes it obvious that we're just sitting and waiting on the upstream call,
 * so the fix isn't in our code -- it's the upstream dependency.
 *
 * NOTE: {@code @WithSpan} and the outbound HTTP client span only appear when the
 * OpenTelemetry Java agent is attached (see scripts/run-with-tracing.sh).
 */
@Service
public class UpstreamApiService {

    private final RestClient upstreamRestClient;

    public UpstreamApiService(RestClient upstreamRestClient) {
        this.upstreamRestClient = upstreamRestClient;
    }

    /** Cheap "is this request well-formed?" work. Always near-instant. */
    @WithSpan("validate-request")
    public void validateRequest() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("request interrupted");
        }
    }

    /**
     * Makes the outbound HTTP call. The OpenTelemetry agent wraps this in a
     * CLIENT span, so the time spent waiting on the upstream is plainly visible
     * (and, for the slow path, dominant) in the trace.
     */
    @WithSpan("call-upstream")
    @SuppressWarnings("unchecked")
    public UpstreamCallResponse callUpstream(String path) {
        long start = System.nanoTime();
        Map<String, Object> body = upstreamRestClient.get()
                .uri(path)
                .retrieve()
                .body(Map.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return toResponse(path, body, elapsedMs);
    }

    /** Cheap in-memory mapping of the upstream payload to our response DTO. */
    @WithSpan("process-response")
    public UpstreamCallResponse processResponse(UpstreamCallResponse raw) {
        return raw;
    }

    private UpstreamCallResponse toResponse(String path, Map<String, Object> body, long elapsedMs) {
        String url = body == null ? null : String.valueOf(body.get("url"));
        String origin = body == null ? null : String.valueOf(body.get("origin"));
        return new UpstreamCallResponse(path, url, origin, elapsedMs);
    }
}
