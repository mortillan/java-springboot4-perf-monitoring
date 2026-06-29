package com.tara.bottleneck.config;

import com.tara.bottleneck.model.Author;
import com.tara.bottleneck.model.Book;
import com.tara.bottleneck.model.Sale;
import com.tara.bottleneck.repository.AuthorRepository;
import com.tara.bottleneck.repository.SaleRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds the in-memory H2 database on startup.
 *  - 50 authors with 5-10 books each   -> powers the N+1 demo
 *  - {@code demo.sales-count} sale rows -> powers the slow-query demo
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final int AUTHOR_COUNT = 50;

    private final AuthorRepository authorRepository;
    private final SaleRepository saleRepository;
    private final int salesCount;

    public DataSeeder(AuthorRepository authorRepository,
                      SaleRepository saleRepository,
                      @Value("${demo.sales-count:8000}") int salesCount) {
        this.authorRepository = authorRepository;
        this.saleRepository = saleRepository;
        this.salesCount = salesCount;
    }

    /**
     * Wrapped in a single span so all the startup inserts/DDL nest under ONE
     * "seed-data" trace instead of producing ~60 separate root-span traces.
     * (Seeding runs at boot, outside any HTTP request, so without a parent span
     * the agent would start a fresh trace per JDBC call.)
     */
    @Override
    @WithSpan("seed-data")
    public void run(String... args) {
        seedAuthors();
        seedSales();
    }

    @WithSpan("seed-authors")
    private void seedAuthors() {
        if (authorRepository.count() > 0) {
            return;
        }

        String[] countries = {"USA", "UK", "Japan", "Philippines", "Germany", "Brazil"};

        for (int a = 1; a <= AUTHOR_COUNT; a++) {
            Author author = new Author("Author " + a, countries[a % countries.length]);

            int bookCount = ThreadLocalRandom.current().nextInt(5, 11);
            for (int b = 1; b <= bookCount; b++) {
                int year = ThreadLocalRandom.current().nextInt(1990, 2026);
                author.addBook(new Book("Book " + a + "-" + b, year));
            }
            authorRepository.save(author);
        }

        log.info("Seeded {} authors. Try GET /api/slow/authors vs GET /api/fast/authors", AUTHOR_COUNT);
    }

    @WithSpan("seed-sales")
    private void seedSales() {
        if (saleRepository.count() > 0) {
            return;
        }

        String[] regions = {"NCR", "Luzon", "Visayas", "Mindanao"};
        List<Sale> sales = new ArrayList<>(salesCount);

        for (int i = 0; i < salesCount; i++) {
            String region = regions[i % regions.length];
            long amount = ThreadLocalRandom.current().nextLong(1_000, 1_000_000);
            sales.add(new Sale(region, amount));
        }
        saleRepository.saveAll(sales);

        log.info("Seeded {} sales. Try GET /api/slow-query/sales-ranking vs GET /api/fast-query/sales-ranking",
                salesCount);
    }
}
