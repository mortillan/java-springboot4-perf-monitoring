package com.tara.bottleneck.controller;

import com.tara.bottleneck.dto.UpstreamCallResponse;
import com.tara.bottleneck.service.UpstreamApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates an UPSTREAM API bottleneck: the work isn't slow in our code, it's
 * slow because we're waiting on a downstream HTTP dependency.
 *
 * Each request runs the same three steps (validate -> call upstream -> process).
 * With the OpenTelemetry agent attached, the outbound HTTP call is its own
 * auto-instrumented CLIENT span, so the Grafana Cloud trace makes it obvious:
 *
 *   - slow endpoint: the `call-upstream` span (and its nested HTTP client span
 *     to httpbin /delay/N) dwarfs everything else.
 *   - fast endpoint: the same steps, but the upstream call is tiny.
 *
 * The takeaway: our application code is fast. The trace shows we're simply
 * blocked waiting on the upstream API -- that's where to look for the fix.
 */
@RestController
@RequestMapping("/api")
public class UpstreamController {

    private final UpstreamApiService upstreamApiService;
    private final int slowDelaySeconds;

    public UpstreamController(UpstreamApiService upstreamApiService,
                              @Value("${demo.upstream.slow-delay-seconds:3}") int slowDelaySeconds) {
        this.upstreamApiService = upstreamApiService;
        this.slowDelaySeconds = slowDelaySeconds;
    }

    /**
     * THE BOTTLENECK (slow upstream).
     * Calls httpbin's /delay/{seconds} endpoint, which sleeps server-side before
     * responding. Our code does nothing slow -- we're just waiting.
     */
    @GetMapping("/slow/upstream")
    public UpstreamCallResponse upstreamSlow() {
        upstreamApiService.validateRequest();
        UpstreamCallResponse raw = upstreamApiService.callUpstream("/delay/" + slowDelaySeconds);
        return upstreamApiService.processResponse(raw);
    }

    /**
     * THE FAST PATH.
     * Calls httpbin's /get endpoint, which returns immediately. Same three
     * steps, but now the upstream call is a sliver in the trace.
     */
    @GetMapping("/fast/upstream")
    public UpstreamCallResponse upstreamFast() {
        upstreamApiService.validateRequest();
        UpstreamCallResponse raw = upstreamApiService.callUpstream("/get");
        return upstreamApiService.processResponse(raw);
    }
}
