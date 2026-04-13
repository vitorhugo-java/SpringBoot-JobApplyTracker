package com.jobtracker.dto.application;

import java.util.List;

public record ApplicationPageResponse(
        List<ApplicationResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {}
