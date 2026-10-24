package com.riseai.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class PageResponse<T> {
    private int currentPage;
    private int pageSize;
    private int totalPages;
    private long totalElements;
    private List<T> data;

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalPages(), page.getTotalElements(), page.getContent());
    }
}
