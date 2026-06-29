package com.tara.bottleneck.dto;

/**
 * Aggregate stats computed in-memory from the ranking rows. Computing this is
 * the "compute summary" step in the trace -- a quick pass over the results.
 */
public record RankingSummary(int rowCount, long totalAmount, long averageAmount, long maxAmount) {
}
