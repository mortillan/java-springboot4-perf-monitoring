package com.tara.bottleneck.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A flat table used to demonstrate a single, internally expensive query.
 * Unlike the N+1 demo (many cheap queries), this is ONE query that does a
 * lot of work inside the database.
 */
@Entity
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String region;

    private long amount;

    protected Sale() {
    }

    public Sale(String region, long amount) {
        this.region = region;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public long getAmount() {
        return amount;
    }
}
