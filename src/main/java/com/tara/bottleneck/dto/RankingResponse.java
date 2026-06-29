package com.tara.bottleneck.dto;

import java.util.List;

/**
 * Final payload: a small summary plus the ranked rows. Wrapping the data this
 * way gives the request a few distinct steps (validate, load, summarize, map)
 * so the trace can show that only the DB load is expensive.
 */
public record RankingResponse(RankingSummary summary, List<SaleRankDto> ranking) {
}
