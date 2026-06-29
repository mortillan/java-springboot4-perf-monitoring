package com.tara.bottleneck.dto;

/**
 * Interface-based projection for the sales-ranking queries.
 * Spring Data maps the native query columns (by alias) onto these getters.
 */
public interface SaleRankView {

    Long getId();

    String getRegion();

    Long getAmount();

    Long getSalesRank();
}
