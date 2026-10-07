package sg.nus.carelink.incident.infrastructure.persistence.entity;

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
 * JPA entity for table spot_check.
 *
 * Merges manager.spot_check + family.home_inspection_consents.
 *
 * DECISION 11  These were the two ends of one flow: the manager proposes a
 *              check, the family approves it. Two tables would have meant two
 *              statuses that could disagree with each other.
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
@Table(name = "spot_check")
public class SpotCheckJpaEntity {

	public enum ApprovalStatus {
		PENDING_APPROVAL, APPROVED, REJECTED
	}

	public enum Result {
		MEETS_STANDARD, NEEDS_IMPROVEMENT
	}

	public enum Outcome {
		COMPLETED, CAREGIVER_NO_SHOW, WITHDRAWN
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	@Column(name = "caregiver_id")
	private Long caregiverId;

	@Column(name = "visit_id")
	private Long visitId;

	/** soft FK to app_user.id */
	@Column(name = "raised_by_user_id")
	private Long raisedByUserId;

	@Column(name = "approving_family_member_id")
	private Long approvingFamilyMemberId;

	@Column(name = "proposed_time", nullable = false)
	private LocalDateTime proposedTime;

	@Column(name = "reason", length = 255)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(name = "approval_status", nullable = false)
	private ApprovalStatus approvalStatus = ApprovalStatus.PENDING_APPROVAL;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "finding", length = 500)
	private String finding;

	/** caregiver analysis: the caregiver may respond to a finding */
	@Column(name = "caregiver_response", length = 500)
	private String caregiverResponse;

	@Column(name = "checked_at")
	private LocalDateTime checkedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	/** the conclusion recorded on site (V13) */
	@Enumerated(EnumType.STRING)
	@Column(name = "result")
	private Result result;

	/** how the request ended; null while it is open, and for a declined one (V13) */
	@Enumerated(EnumType.STRING)
	@Column(name = "outcome")
	private Outcome outcome;

	/** why the family declined, or why the manager withdrew (V13) */
	@Column(name = "closing_reason", length = 255)
	private String closingReason;

	/** the missed-visit incident when the caregiver did not turn up (V13) */
	@Column(name = "incident_id")
	private Long incidentId;

	public SpotCheckJpaEntity() {
	}

	public Result getResult() {
		return result;
	}

	public void setResult(Result result) {
		this.result = result;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public void setOutcome(Outcome outcome) {
		this.outcome = outcome;
	}

	public String getClosingReason() {
		return closingReason;
	}

	public void setClosingReason(String closingReason) {
		this.closingReason = closingReason;
	}

	public Long getIncidentId() {
		return incidentId;
	}

	public void setIncidentId(Long incidentId) {
		this.incidentId = incidentId;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
	}

	public Long getVisitId() {
		return visitId;
	}

	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}

	public Long getRaisedByUserId() {
		return raisedByUserId;
	}

	public void setRaisedByUserId(Long raisedByUserId) {
		this.raisedByUserId = raisedByUserId;
	}

	public Long getApprovingFamilyMemberId() {
		return approvingFamilyMemberId;
	}

	public void setApprovingFamilyMemberId(Long approvingFamilyMemberId) {
		this.approvingFamilyMemberId = approvingFamilyMemberId;
	}

	public LocalDateTime getProposedTime() {
		return proposedTime;
	}

	public void setProposedTime(LocalDateTime proposedTime) {
		this.proposedTime = proposedTime;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public ApprovalStatus getApprovalStatus() {
		return approvalStatus;
	}

	public void setApprovalStatus(ApprovalStatus approvalStatus) {
		this.approvalStatus = approvalStatus;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getFinding() {
		return finding;
	}

	public void setFinding(String finding) {
		this.finding = finding;
	}

	public String getCaregiverResponse() {
		return caregiverResponse;
	}

	public void setCaregiverResponse(String caregiverResponse) {
		this.caregiverResponse = caregiverResponse;
	}

	public LocalDateTime getCheckedAt() {
		return checkedAt;
	}

	public void setCheckedAt(LocalDateTime checkedAt) {
		this.checkedAt = checkedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
