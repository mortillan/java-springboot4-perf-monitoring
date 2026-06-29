package com.tara.bottleneck.dto;

/**
 * Plain value object returned to clients. Building these from the raw query
 * projections is the "map to DTO" step in the trace -- real work, but cheap.
 */
public record SaleRankDto(Long id, String region, Long amount, Long salesRank) {
}
