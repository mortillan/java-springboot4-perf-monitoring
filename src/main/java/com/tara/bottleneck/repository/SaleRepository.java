package com.tara.bottleneck.repository;

import com.tara.bottleneck.dto.SaleRankView;
import com.tara.bottleneck.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /**
     * THE BOTTLENECK: a correlated subquery.
     *
     * For EVERY row in `sale`, the database re-runs the inner
     * "COUNT(*) ... WHERE s2.amount >= s.amount" against the WHOLE table.
     * That is N x N work. With 8,000 rows that is ~64 million comparisons
     * for a single HTTP request.
     *
     * Note: this is ONE SQL statement (X-Sql-Statements: 1), but it is
     * internally enormous. Query COUNT alone won't catch it -- you need timing
     * and EXPLAIN. That contrast is the whole lesson.
     */
    @Query(value = """
            SELECT s.id      AS id,
                   s.region  AS region,
                   s.amount  AS amount,
                   (SELECT COUNT(*) FROM sale s2 WHERE s2.amount >= s.amount) AS salesRank
            FROM sale s
            ORDER BY s.amount DESC
            """, nativeQuery = true)
    List<SaleRankView> findRankingSlow();

    /**
     * THE FIX: a window function.
     *
     * RANK() OVER (ORDER BY amount DESC) computes the same ranking by sorting
     * the table once (N log N) instead of re-scanning it per row (N x N).
     * Same result, a fraction of the work.
     */
    @Query(value = """
            SELECT s.id      AS id,
                   s.region  AS region,
                   s.amount  AS amount,
                   RANK() OVER (ORDER BY s.amount DESC) AS salesRank
            FROM sale s
            ORDER BY s.amount DESC
            """, nativeQuery = true)
    List<SaleRankView> findRankingFast();
}
