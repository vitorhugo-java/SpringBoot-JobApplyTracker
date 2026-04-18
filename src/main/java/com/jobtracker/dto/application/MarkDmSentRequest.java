package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to mark that DM was sent to the recruiter")
public record MarkDmSentRequest() {}
