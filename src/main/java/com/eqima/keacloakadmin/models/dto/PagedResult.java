package com.eqima.keacloakadmin.models.dto;

import java.util.List;

public record PagedResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    long totalPages
) {
}
