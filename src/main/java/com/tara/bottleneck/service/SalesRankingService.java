package com.tara.bottleneck.service;

import com.tara.bottleneck.dto.RankingSummary;
import com.tara.bottleneck.dto.SaleRankDto;
import com.tara.bottleneck.dto.SaleRankView;
import com.tara.bottleneck.repository.SaleRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The request is split into small, named steps. Each {@code @WithSpan} method
 * becomes its own span in the distributed trace, so the waterfall in Grafana
 * Cloud reads like a story:
 *
 *   validate-request   -> ~0 ms
 *   load-ranking-slow  -> HUGE  (the correlated-subquery JDBC span nests here)
 *   compute-summary    -> a few ms
 *   map-to-dto         -> a few ms
 *
 * The point of the demo: everything except the DB load is negligible, so the
 * one long bar is unmistakably the bottleneck to optimize.
 *
 * NOTE: {@code @WithSpan} only produces spans when the OpenTelemetry Java agent
 * is attached (see scripts/run-with-tracing.sh). Without the agent it is a no-op.
 */
@Service
public class SalesRankingService {

    private final SaleRepository saleRepository;

    public SalesRankingService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    /** Cheap "is this request well-formed?" work. Always near-instant. */
    @WithSpan("validate-request")
    public void validateRequest() {
        // Stand-in for auth checks / param validation. Intentionally trivial:
        // in the trace this should be an essentially zero-width bar.
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("request interrupted");
        }
    }

    /** Runs the O(N^2) correlated subquery. The JDBC span lives under this span. */
    @WithSpan("load-ranking-slow")
    public List<SaleRankView> loadRankingSlow() {
        return saleRepository.findRankingSlow();
    }

    /** Runs the O(N log N) window-function query. Same shape, tiny cost. */
    @WithSpan("load-ranking-fast")
    public List<SaleRankView> loadRankingFast() {
        return saleRepository.findRankingFast();
    }

    /** One in-memory pass over the rows -> aggregate stats. Cheap CPU work. */
    @WithSpan("compute-summary")
    public RankingSummary computeSummary(List<SaleRankView> rows) {
        long total = 0;
        long max = 0;
        for (SaleRankView row : rows) {
            long amount = row.getAmount() == null ? 0L : row.getAmount();
            total += amount;
            if (amount > max) {
                max = amount;
            }
        }
        long average = rows.isEmpty() ? 0L : total / rows.size();
        return new RankingSummary(rows.size(), total, average, max);
    }

    /** Maps query projections to response DTOs. Cheap CPU work. */
    @WithSpan("map-to-dto")
    public List<SaleRankDto> mapToDto(List<SaleRankView> rows) {
        return rows.stream()
                .map(row -> new SaleRankDto(row.getId(), row.getRegion(), row.getAmount(), row.getSalesRank()))
                .toList();
    }
}
