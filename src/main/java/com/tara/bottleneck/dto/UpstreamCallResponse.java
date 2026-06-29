package com.tara.bottleneck.dto;

/**
 * What the upstream demo returns to the caller. The interesting field is
 * {@code upstreamElapsedMs}: nearly the entire request time is spent waiting on
 * the upstream call, which is exactly the story the trace tells.
 *
 * @param endpoint          which upstream path we hit (e.g. /get or /delay/3)
 * @param upstreamUrl       the URL echoed back by the upstream service
 * @param origin            the origin IP the upstream service saw
 * @param upstreamElapsedMs how long the upstream HTTP call took, in millis
 */
public record UpstreamCallResponse(String endpoint,
                                   String upstreamUrl,
                                   String origin,
                                   long upstreamElapsedMs) {
}
