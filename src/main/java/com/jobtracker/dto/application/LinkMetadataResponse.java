package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rich link preview metadata extracted from a URL")
public record LinkMetadataResponse(
        @Schema(description = "Page title", example = "Senior Backend Engineer - TechCorp")
        String title,

        @Schema(description = "Page description", example = "Join our backend team and work on scalable microservices")
        String description,

        @Schema(description = "Open Graph image URL", example = "https://example.com/og-image.jpg")
        String image,

        @Schema(description = "Domain name", example = "linkedin.com")
        String domain
) {}
