package com.example.contactscrawler.api.dto;

import java.util.List;

/**
 * Страница результатов.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Нарезка уже отсортированного списка. */
    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        long total = all.size();
        int from = (int) Math.min((long) page * size, total);
        int to = (int) Math.min((long) from + size, total);
        int pages = (int) ((total + size - 1) / size);
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, total, pages);
    }
}
