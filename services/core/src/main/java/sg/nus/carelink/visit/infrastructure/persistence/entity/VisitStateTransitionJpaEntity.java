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
 * JPA entity for table visit_state_transition.
 *
 * From the caregiver analysis (VisitStateTransition). Every attempt to move the
 * visit between states, INCLUDING the ones the state machine rejected. Story C8
 * asks for exactly this: "rejected transition attempts logged separately from the
 * legal history". It is the evidence the State design problem is judged on.
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
@Table(name = "visit_state_transition")
public class VisitStateTransitionJpaEntity {

	public enum Result {
		APPLIED, REJECTED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "visit_id", nullable = false)
	private Long visitId;

	@Column(name = "from_state", nullable = false, length = 20)
	private String fromState;

	@Column(name = "to_state", nullable = false, length = 20)
	private String toState;

	/** soft FK to app_user.id; null when the system acted */
	@Column(name = "actor_user_id")
	private Long actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "result", nullable = false)
	private Result result;

	@Column(name = "rejection_reason", length = 255)
	private String rejectionReason;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	public VisitStateTransitionJpaEntity() {
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

	public String getFromState() {
		return fromState;
	}

	public void setFromState(String fromState) {
		this.fromState = fromState;
	}

	public String getToState() {
		return toState;
	}

	public void setToState(String toState) {
		this.toState = toState;
	}

	public Long getActorUserId() {
		return actorUserId;
	}

	public void setActorUserId(Long actorUserId) {
		this.actorUserId = actorUserId;
	}

	public Result getResult() {
		return result;
	}

	public void setResult(Result result) {
		this.result = result;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(LocalDateTime occurredAt) {
		this.occurredAt = occurredAt;
	}
}
