package sg.nus.carelink.visit.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity for table visit_assignment.
 *
 * From the caregiver analysis (RosterAssignment). visit.caregiver_id above is the
 * CURRENT assignee, kept there because every roster query needs it. This table is
 * the history: who was assigned, who was replaced after an absence, and why.
 *
 * DECISION 15  Caregiver-to-visit is many-to-many over time, one-to-one at any
 *              instant. The caregiver analysis is right that a re-roster must not
 *              erase the previous assignment. So the current one is denormalised
 *              onto visit and the full history lives here.
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
@Table(name = "visit_assignment")
public class VisitAssignmentJpaEntity {

	public enum Status {
		ACTIVE, REPLACED, CANCELLED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "visit_id", nullable = false)
	private Long visitId;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	/** soft FK to app_user.id */
	@Column(name = "assigned_by_user_id")
	private Long assignedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.ACTIVE;

	/** why this assignment was made or replaced */
	@Column(name = "reason", length = 255)
	private String reason;

	@Column(name = "assigned_at", nullable = false)
	private LocalDateTime assignedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	/** soft FK to rostering_candidate.id: the run and option this assignment came from; null when assigned by hand */
	@Column(name = "rostering_candidate_id")
	private Long rosteringCandidateId;

	public VisitAssignmentJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getVisitId() {
		return visitId;
	}

	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
	}

	public Long getAssignedByUserId() {
		return assignedByUserId;
	}

	public void setAssignedByUserId(Long assignedByUserId) {
		this.assignedByUserId = assignedByUserId;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public void setAssignedAt(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(LocalDateTime endedAt) {
		this.endedAt = endedAt;
	}

	public Long getRosteringCandidateId() {
		return rosteringCandidateId;
	}

	public void setRosteringCandidateId(Long rosteringCandidateId) {
		this.rosteringCandidateId = rosteringCandidateId;
	}
}
