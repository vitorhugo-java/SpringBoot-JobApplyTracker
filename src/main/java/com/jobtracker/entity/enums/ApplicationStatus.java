package com.jobtracker.entity.enums;

public enum ApplicationStatus {
    RH("RH"),
    FIZ_A_RH_AGUARDANDO_ATUALIZACAO("Fiz a RH - Aguardando Atualização"),
    FIZ_A_HIRING_MANAGER_AGUARDANDO_ATUALIZACAO("Fiz a Hiring Manager - Aguardando Atualização"),
    TESTE_TECNICO("Teste Técnico"),
    FIZ_TESTE_TECNICO_AGUARDANDO_ATUALIZACAO("Fiz teste Técnico - aguardando atualização"),
    RH_NEGOCIACAO("RH (Negociação)");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ApplicationStatus fromDisplayName(String displayName) {
        for (ApplicationStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + displayName);
    }
}
