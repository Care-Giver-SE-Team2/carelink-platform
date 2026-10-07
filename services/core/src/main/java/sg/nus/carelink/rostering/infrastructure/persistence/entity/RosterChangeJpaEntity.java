package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for table roster_change (V12): what became of one visit an absence vacated.
 *
 * <p>Same shape as the other entities: no domain logic, plain ids for other aggregates, and the
 * schema owned by Flyway. Every timestamp is written by the application from its own clock.
 */
@Entity
@Table(name = "roster_change")
public class RosterChangeJpaEntity {

	public enum Status {
		AWAITING_FAMILY, UNCOVERED, RESOLVED
	}

	public enum Outcome {
		REPLACED, RESCHEDULED, SKIPPED, WITHDRAWN
	}

	public enum DecidedBy {
		FAMILY, DEFAULT_PLAN, MANAGER
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "absence_id", nullable = false)
	private Long absenceId;

	@Column(name = "visit_id", nullable = false)
	private Long visitId;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	@Column(name = "original_caregiver_id", nullable = false)
	private Long originalCaregiverId;

	@Column(name = "visit_start", nullable = false)
	private LocalDateTime visitStart;

	@Column(name = "visit_end")
	private LocalDateTime visitEnd;

	@Column(name = "rostering_run_id")
	private Long rosteringRunId;

	@Column(name = "proposed_caregiver_id")
	private Long proposedCaregiverId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome")
	private Outcome outcome;

	@Enumerated(EnumType.STRING)
	@Column(name = "decided_by")
	private DecidedBy decidedBy;

	@Column(name = "decided_by_user_id")
	private Long decidedByUserId;

	@Column(name = "assigned_caregiver_id")
	private Long assignedCaregiverId;

	@Column(name = "rescheduled_visit_id")
	private Long rescheduledVisitId;

	@Column(name = "incident_id")
	private Long incidentId;

	@Column(name = "respond_by")
	private LocalDateTime respondBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "note", length = 255)
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public RosterChangeJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getAbsenceId() {
		return absenceId;
	}

	public void setAbsenceId(Long absenceId) {
		this.absenceId = absenceId;
	}

	public Long getVisitId() {
		return visitId;
	}

	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public Long getOriginalCaregiverId() {
		return originalCaregiverId;
	}

	public void setOriginalCaregiverId(Long originalCaregiverId) {
		this.originalCaregiverId = originalCaregiverId;
	}

	public LocalDateTime getVisitStart() {
		return visitStart;
	}

	public void setVisitStart(LocalDateTime visitStart) {
		this.visitStart = visitStart;
	}

	public LocalDateTime getVisitEnd() {
		return visitEnd;
	}

	public void setVisitEnd(LocalDateTime visitEnd) {
		this.visitEnd = visitEnd;
	}

	public Long getRosteringRunId() {
		return rosteringRunId;
	}

	public void setRosteringRunId(Long rosteringRunId) {
		this.rosteringRunId = rosteringRunId;
	}

	public Long getProposedCaregiverId() {
		return proposedCaregiverId;
	}

	public void setProposedCaregiverId(Long proposedCaregiverId) {
		this.proposedCaregiverId = proposedCaregiverId;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public void setOutcome(Outcome outcome) {
		this.outcome = outcome;
	}

	public DecidedBy getDecidedBy() {
		return decidedBy;
	}

	public void setDecidedBy(DecidedBy decidedBy) {
		this.decidedBy = decidedBy;
	}

	public Long getDecidedByUserId() {
		return decidedByUserId;
	}

	public void setDecidedByUserId(Long decidedByUserId) {
		this.decidedByUserId = decidedByUserId;
	}

	public Long getAssignedCaregiverId() {
		return assignedCaregiverId;
	}

	public void setAssignedCaregiverId(Long assignedCaregiverId) {
		this.assignedCaregiverId = assignedCaregiverId;
	}

	public Long getRescheduledVisitId() {
		return rescheduledVisitId;
	}

	public void setRescheduledVisitId(Long rescheduledVisitId) {
		this.rescheduledVisitId = rescheduledVisitId;
	}

	public Long getIncidentId() {
		return incidentId;
	}

	public void setIncidentId(Long incidentId) {
		this.incidentId = incidentId;
	}

	public LocalDateTime getRespondBy() {
		return respondBy;
	}

	public void setRespondBy(LocalDateTime respondBy) {
		this.respondBy = respondBy;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
