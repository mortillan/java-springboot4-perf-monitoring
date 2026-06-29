package com.tara.bottleneck.controller;

import com.tara.bottleneck.dto.RankingResponse;
import com.tara.bottleneck.dto.RankingSummary;
import com.tara.bottleneck.dto.SaleRankDto;
import com.tara.bottleneck.dto.SaleRankView;
import com.tara.bottleneck.service.SalesRankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demonstrates a "very slow query": a SINGLE SQL statement that is internally
 * expensive, as opposed to the N+1 demo (many cheap statements).
 *
 * Each request runs the same four steps (validate -> load -> summarize -> map).
 * With the OpenTelemetry agent attached, every step is its own span, so the
 * Grafana Cloud trace makes the bottleneck obvious:
 *
 *   - slow endpoint: the `load-ranking-slow` span (and its JDBC child) dwarfs
 *     everything else -- validate/summarize/map are slivers next to it.
 *   - fast endpoint: the same steps, but `load-ranking-fast` is tiny too.
 *
 * Watch the response headers as well:
 *   X-Sql-Statements -> 1 for BOTH endpoints (it's one query either way!)
 *   X-Elapsed-Ms     -> huge for the slow one, tiny for the fast one
 *
 * The takeaway: counting queries would NOT flag this bottleneck. You need
 * timing (and the trace waterfall) to find an expensive single query.
 */
@RestController
@RequestMapping("/api")
public class SaleController {

    private final SalesRankingService salesRankingService;

    public SaleController(SalesRankingService salesRankingService) {
        this.salesRankingService = salesRankingService;
    }

    /** Correlated subquery -> O(N^2) work inside one statement. */
    @GetMapping("/slow-query/sales-ranking")
    public RankingResponse rankingSlow() {
        salesRankingService.validateRequest();
        List<SaleRankView> rows = salesRankingService.loadRankingSlow();
        RankingSummary summary = salesRankingService.computeSummary(rows);
        List<SaleRankDto> ranking = salesRankingService.mapToDto(rows);
        return new RankingResponse(summary, ranking);
    }

    /** Window function -> O(N log N). Same result, a fraction of the cost. */
    @GetMapping("/fast-query/sales-ranking")
    public RankingResponse rankingFast() {
        salesRankingService.validateRequest();
        List<SaleRankView> rows = salesRankingService.loadRankingFast();
        RankingSummary summary = salesRankingService.computeSummary(rows);
        List<SaleRankDto> ranking = salesRankingService.mapToDto(rows);
        return new RankingResponse(summary, ranking);
    }
}
