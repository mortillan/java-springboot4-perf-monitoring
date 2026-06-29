package com.tara.bottleneck.dto;

import java.util.List;

public record AuthorDto(
        Long id,
        String name,
        String country,
        int bookCount,
        List<BookDto> books) {
}
