package com.luna.aggarly.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast,
        boolean hasNext,
        boolean hasPrevious
) {
    public static PaginationMeta fromPage(Page<?> page) {
        if (page == null) {
            return new PaginationMeta(0, 0, 0, 0, true, true, false, false);
        }
        return new PaginationMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );
    }

    public static PaginationMeta of(int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PaginationMeta(
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                page >= totalPages - 1,
                page < totalPages - 1,
                page > 0
        );
    }
}
