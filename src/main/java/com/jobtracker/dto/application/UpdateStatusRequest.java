package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update the status of a job application")
public record UpdateStatusRequest(
        @Schema(description = "New application status", example = "Teste Técnico",
                allowableValues = {"RH", "Fiz a RH - Aguardando Atualização", "Fiz a Hiring Manager - Aguardando Atualização", "Teste Técnico", "Fiz teste Técnico - aguardando atualização", "RH (Negociação)", "Rejeitado", "Tarde demais", "Ghosting"})
        String status
) {}
