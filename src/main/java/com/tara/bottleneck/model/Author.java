package com.tara.bottleneck.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String country;

    /**
     * LAZY on purpose: this is what makes the N+1 problem possible.
     * Touching author.getBooks() outside of a fetch join triggers a
     * separate SELECT per author.
     */
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Book> books = new ArrayList<>();

    protected Author() {
    }

    public Author(String name, String country) {
        this.name = name;
        this.country = country;
    }

    public void addBook(Book book) {
        books.add(book);
        book.setAuthor(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public List<Book> getBooks() {
        return books;
    }
}
