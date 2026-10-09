package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for table absence_report.
 *
 * From manager.absence_report. CG02 writes it, MG04 reads it.
 *
 * <p>Generated from V2__care_domain.sql as a starting point; edit freely, it will not
 * be regenerated. Same shape as identity's AppUserJpaEntity: no domain logic here,
 * references to other aggregates are plain ids (DECISION 5 in the schema), so no
 * module depends on another module's persistence classes. The domain model that
 * carries the business rules lives in the module's domain.model package; the mapper
 * between the two is in persistence.adapter.
 *
 * <p>The schema is owned by Flyway. Hibernate validates this mapping at start-up
 * and never alters the table.
 */
@Entity
@Table(name = "absence_report")
public class AbsenceReportJpaEntity {

	public enum Type {
		SICK, ANNUAL, EMERGENCY, OTHER
	}

	public enum Status {
		PENDING, APPROVED, REJECTED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	/** soft FK to app_user.id */
	@Column(name = "reviewed_by_user_id")
	private Long reviewedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false)
	private Type type = Type.OTHER;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "reason", length = 255)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.PENDING;

	/** UC-MG04 step 7: the manager confirmed every vacated visit is accounted for (V12) */
	@Column(name = "coverage_confirmed_at")
	private LocalDateTime coverageConfirmedAt;

	/** soft FK to app_user.id (V12) */
	@Column(name = "coverage_confirmed_by_user_id")
	private Long coverageConfirmedByUserId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public AbsenceReportJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
	}

	public Long getReviewedByUserId() {
		return reviewedByUserId;
	}

	public void setReviewedByUserId(Long reviewedByUserId) {
		this.reviewedByUserId = reviewedByUserId;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public LocalDateTime getCoverageConfirmedAt() {
		return coverageConfirmedAt;
	}

	public void setCoverageConfirmedAt(LocalDateTime coverageConfirmedAt) {
		this.coverageConfirmedAt = coverageConfirmedAt;
	}

	public Long getCoverageConfirmedByUserId() {
		return coverageConfirmedByUserId;
	}

	public void setCoverageConfirmedByUserId(Long coverageConfirmedByUserId) {
		this.coverageConfirmedByUserId = coverageConfirmedByUserId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
