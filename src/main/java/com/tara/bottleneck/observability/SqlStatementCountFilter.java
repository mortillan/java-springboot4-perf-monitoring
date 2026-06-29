package com.tara.bottleneck.observability;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Makes the bottleneck VISIBLE. For every HTTP request it measures how many
 * JDBC statements Hibernate executed and how long the request took, then adds
 * them as response headers and logs a one-line summary.
 *
 * NOTE: Hibernate Statistics are global counters. The per-request number is
 * accurate when requests are handled one at a time (perfect for a live demo).
 * Under heavy concurrency the count is a rough indicator, not an exact value.
 */
@Component
public class SqlStatementCountFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlStatementCountFilter.class);

    private final Statistics statistics;

    public SqlStatementCountFilter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long statementsBefore = statistics.getPrepareStatementCount();
        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            long sqlStatements = statistics.getPrepareStatementCount() - statementsBefore;
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

            wrappedResponse.setHeader("X-Sql-Statements", Long.toString(sqlStatements));
            wrappedResponse.setHeader("X-Elapsed-Ms", Long.toString(elapsedMs));

            log.info("{} {} -> {} SQL statement(s) in {} ms",
                    request.getMethod(), request.getRequestURI(), sqlStatements, elapsedMs);

            wrappedResponse.copyBodyToResponse();
        }
    }
}
