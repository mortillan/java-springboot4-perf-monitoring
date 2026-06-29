package com.tara.bottleneck.controller;

import com.tara.bottleneck.dto.AuthorDto;
import com.tara.bottleneck.dto.BookDto;
import com.tara.bottleneck.model.Author;
import com.tara.bottleneck.repository.AuthorRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Two endpoints that return the EXACT same JSON, but with very different
 * database cost. This is the heart of the brownbag demo.
 *
 * Watch the console (SQL logs) and the response headers:
 *   X-Sql-Statements  -> how many SQL statements this request executed
 *   X-Elapsed-Ms      -> how long it took
 */
@RestController
@RequestMapping("/api")
public class AuthorController {

    private final AuthorRepository authorRepository;

    public AuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    /**
     * THE BOTTLENECK (N+1 problem).
     * 1 query to load all authors, then 1 extra query per author to lazily
     * load that author's books. With 50 authors that is 1 + 50 = 51 queries.
     */
    @GetMapping("/slow/authors")
    @Transactional(readOnly = true)
    public List<AuthorDto> getAuthorsSlow() {
        List<Author> authors = authorRepository.findAll();
        return authors.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * THE FIX.
     * A single query with a JOIN FETCH loads authors and books together.
     * 50 authors = 1 query, regardless of how much data grows.
     */
    @GetMapping("/fast/authors")
    @Transactional(readOnly = true)
    public List<AuthorDto> getAuthorsFast() {
        List<Author> authors = authorRepository.findAllWithBooks();
        return authors.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Mapping the books here is what triggers the lazy load in the slow path.
     * In the fast path the books are already in memory, so no extra queries.
     */
    private AuthorDto toDto(Author author) {
        List<BookDto> books = author.getBooks().stream()
                .map(book -> new BookDto(book.getId(), book.getTitle(), book.getPublicationYear()))
                .toList();
        return new AuthorDto(author.getId(), author.getName(), author.getCountry(), books.size(), books);
    }
}
