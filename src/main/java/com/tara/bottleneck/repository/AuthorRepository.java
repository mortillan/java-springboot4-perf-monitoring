package com.tara.bottleneck.repository;

import com.tara.bottleneck.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    /**
     * The FIX: one query that eagerly fetches authors AND their books via a
     * single SQL join. No matter how many authors exist, this is 1 round-trip.
     */
    @Query("select distinct a from Author a left join fetch a.books")
    List<Author> findAllWithBooks();
}
