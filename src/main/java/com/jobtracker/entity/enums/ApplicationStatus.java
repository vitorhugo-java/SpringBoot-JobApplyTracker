package com.jobtracker.entity.enums;

public enum ApplicationStatus {
    RH("RH", "Initial HR outreach stage."),
    ENTREVISTA_MARCADA("Entrevista marcada", "An interview has been scheduled."),
    FIZ_A_RH_AGUARDANDO_ATUALIZACAO("Fiz a RH - Aguardando Atualização", "HR contact has happened and a reply is pending."),
    FIZ_A_HIRING_MANAGER_AGUARDANDO_ATUALIZACAO("Fiz a Hiring Manager - Aguardando Atualização", "Hiring manager contact has happened and a reply is pending."),
    TESTE_TECNICO("Teste Técnico", "Technical test phase."),
    FIZ_TESTE_TECNICO_AGUARDANDO_ATUALIZACAO("Fiz teste Técnico - aguardando atualização", "Technical test has been sent and feedback is pending."),
    RH_NEGOCIACAO("RH (Negociação)", "Recruiter negotiation or offer discussion."),
    REJEITADO("Rejeitado", "Application was rejected."),
    TARDE_DEMAIS("Tarde demais", "The opportunity was missed or closed too late."),
    GHOSTING("Ghosting", "No response after follow-up attempts.");

    private final String displayName;
    private final String description;

    ApplicationStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
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
