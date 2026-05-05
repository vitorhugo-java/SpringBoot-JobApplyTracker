package com.jobtracker.entity;

import com.jobtracker.entity.enums.ApplicationStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "job_applications", indexes = {
        @Index(name = "idx_app_user_id", columnList = "user_id"),
        @Index(name = "idx_app_status", columnList = "status"),
        @Index(name = "idx_app_next_step", columnList = "next_step_date_time"),
        @Index(name = "idx_app_application_date", columnList = "application_date")
})
public class JobApplication {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "vacancy_name", length = 255)
    private String vacancyName;

    @Column(name = "recruiter_name", length = 255)
    private String recruiterName;

    @Column(name = "organization", nullable = true, length = 255)
    private String organization;

    @Column(name = "vacancy_link", length = 2048)
    private String vacancyLink;

    @Column(name = "application_date")
    private LocalDate applicationDate;

    @Column(name = "rh_accepted_connection", nullable = false)
    private boolean rhAcceptedConnection;

    @Column(name = "interview_scheduled", nullable = false)
    private boolean interviewScheduled;

    @Column(name = "next_step_date_time")
    private LocalDateTime nextStepDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 100, columnDefinition = "varchar(100)")
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = true, length = 100, columnDefinition = "varchar(100)")
    private ApplicationStatus previousStatus;

    @Column(name = "recruiter_dm_reminder_enabled", nullable = false)
    private boolean recruiterDmReminderEnabled;

    @Column(name = "recruiter_dm_sent_at")
    private LocalDateTime recruiterDmSentAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "application_created_xp_awarded", nullable = false)
    private boolean applicationCreatedXpAwarded;

    @Column(name = "recruiter_dm_xp_awarded", nullable = false)
    private boolean recruiterDmXpAwarded;

    @Column(name = "interview_progress_xp_awarded", nullable = false)
    private boolean interviewProgressXpAwarded;

    @Column(name = "note_added_xp_awarded", nullable = false)
    private boolean noteAddedXpAwarded;

    @Column(name = "offer_won_xp_awarded", nullable = false)
    private boolean offerWonXpAwarded;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "drive_vacancy_folder_id", length = 255)
    private String driveVacancyFolderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVacancyName() { return vacancyName; }
    public void setVacancyName(String vacancyName) { this.vacancyName = vacancyName; }

    public String getRecruiterName() { return recruiterName; }
    public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getVacancyLink() { return vacancyLink; }
    public void setVacancyLink(String vacancyLink) { this.vacancyLink = vacancyLink; }

    public LocalDate getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDate applicationDate) { this.applicationDate = applicationDate; }

    public boolean isRhAcceptedConnection() { return rhAcceptedConnection; }
    public void setRhAcceptedConnection(boolean rhAcceptedConnection) { this.rhAcceptedConnection = rhAcceptedConnection; }

    public boolean isInterviewScheduled() { return interviewScheduled; }
    public void setInterviewScheduled(boolean interviewScheduled) { this.interviewScheduled = interviewScheduled; }

    public LocalDateTime getNextStepDateTime() { return nextStepDateTime; }
    public void setNextStepDateTime(LocalDateTime nextStepDateTime) { this.nextStepDateTime = nextStepDateTime; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public ApplicationStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(ApplicationStatus previousStatus) { this.previousStatus = previousStatus; }

    public boolean isRecruiterDmReminderEnabled() { return recruiterDmReminderEnabled; }
    public void setRecruiterDmReminderEnabled(boolean recruiterDmReminderEnabled) { this.recruiterDmReminderEnabled = recruiterDmReminderEnabled; }

    public LocalDateTime getRecruiterDmSentAt() { return recruiterDmSentAt; }
    public void setRecruiterDmSentAt(LocalDateTime recruiterDmSentAt) { this.recruiterDmSentAt = recruiterDmSentAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isApplicationCreatedXpAwarded() { return applicationCreatedXpAwarded; }
    public void setApplicationCreatedXpAwarded(boolean applicationCreatedXpAwarded) { this.applicationCreatedXpAwarded = applicationCreatedXpAwarded; }

    public boolean isRecruiterDmXpAwarded() { return recruiterDmXpAwarded; }
    public void setRecruiterDmXpAwarded(boolean recruiterDmXpAwarded) { this.recruiterDmXpAwarded = recruiterDmXpAwarded; }

    public boolean isInterviewProgressXpAwarded() { return interviewProgressXpAwarded; }
    public void setInterviewProgressXpAwarded(boolean interviewProgressXpAwarded) { this.interviewProgressXpAwarded = interviewProgressXpAwarded; }

    public boolean isNoteAddedXpAwarded() { return noteAddedXpAwarded; }
    public void setNoteAddedXpAwarded(boolean noteAddedXpAwarded) { this.noteAddedXpAwarded = noteAddedXpAwarded; }

    public boolean isOfferWonXpAwarded() { return offerWonXpAwarded; }
    public void setOfferWonXpAwarded(boolean offerWonXpAwarded) { this.offerWonXpAwarded = offerWonXpAwarded; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }

    public String getDriveVacancyFolderId() { return driveVacancyFolderId; }
    public void setDriveVacancyFolderId(String driveVacancyFolderId) { this.driveVacancyFolderId = driveVacancyFolderId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
