package com.atcsafety.restapi.application;

import java.util.List;

/**
 * Custom pagination wrapper returned by list endpoints.
 *
 * <p>Uses explicit field names ({@code totalElements}, {@code totalPages}, {@code page},
 * {@code size}) rather than the default Spring Data {@code Page} JSON structure, so
 * the API contract is independent of framework serialization choices.
 */
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
