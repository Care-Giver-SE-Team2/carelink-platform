package sg.nus.carelink.profile.infrastructure.persistence.entity;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable submission fields; the snapshot never follows later changes to the elder profile. */
@Entity
@Table(name = "care_service_application")
public class ServiceApplicationJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_family_member_id", nullable = false, updatable = false)
    private Long applicantFamilyMemberId;

    @Column(name = "elder_id", nullable = false, updatable = false)
    private Long elderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "elder_snapshot", nullable = false, updatable = false)
    private String elderSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "care_needs", nullable = false, updatable = false)
    private List<String> careNeeds;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ServiceApplicationJpaEntity() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicantFamilyMemberId() { return applicantFamilyMemberId; }
    public void setApplicantFamilyMemberId(Long applicantFamilyMemberId) { this.applicantFamilyMemberId = applicantFamilyMemberId; }
    public Long getElderId() { return elderId; }
    public void setElderId(Long elderId) { this.elderId = elderId; }
    public String getElderSnapshot() { return elderSnapshot; }
    public void setElderSnapshot(String elderSnapshot) { this.elderSnapshot = elderSnapshot; }
    public List<String> getCareNeeds() { return careNeeds; }
    public void setCareNeeds(List<String> careNeeds) { this.careNeeds = careNeeds; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
