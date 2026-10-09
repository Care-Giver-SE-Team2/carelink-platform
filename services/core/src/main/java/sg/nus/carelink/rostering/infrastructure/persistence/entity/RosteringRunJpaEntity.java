package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity for table rostering_run.
 *
 * One click of "Re-roster" or "find a caregiver". A run covers one or many
 * visits: an absence vacates several at once (screen 4c re-rosters seven).
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
@Table(name = "rostering_run")
public class RosteringRunJpaEntity {

	public enum TriggerType {
		NEW_VISIT, ABSENCE, MANUAL
	}

	public enum Objective {
		CONTINUITY, TRAVEL_TIME, EVEN_WORKLOAD, COST
	}

	public enum Status {
		PROPOSED, COMMITTED, DISCARDED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false)
	private TriggerType triggerType;

	/** the absence that vacated the visits, when trigger_type = ABSENCE */
	@Column(name = "absence_id")
	private Long absenceId;

	/** the Strategy that ran; screen 4c objective tabs */
	@Enumerated(EnumType.STRING)
	@Column(name = "objective", nullable = false)
	private Objective objective;

	/** soft FK to app_user.id */
	@Column(name = "requested_by_user_id")
	private Long requestedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.PROPOSED;

	@Column(name = "visits_total", nullable = false)
	private Integer visitsTotal = 0;

	@Column(name = "visits_covered", nullable = false)
	private Integer visitsCovered = 0;

	@Column(name = "continuity_kept", nullable = false)
	private Integer continuityKept = 0;

	@Column(name = "added_travel_km", precision = 6, scale = 1)
	private BigDecimal addedTravelKm;

	@Column(name = "ran_at", nullable = false)
	private LocalDateTime ranAt;

	@Column(name = "committed_at")
	private LocalDateTime committedAt;

	public RosteringRunJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public TriggerType getTriggerType() {
		return triggerType;
	}

	public void setTriggerType(TriggerType triggerType) {
		this.triggerType = triggerType;
	}

	public Long getAbsenceId() {
		return absenceId;
	}

	public void setAbsenceId(Long absenceId) {
		this.absenceId = absenceId;
	}

	public Objective getObjective() {
		return objective;
	}

	public void setObjective(Objective objective) {
		this.objective = objective;
	}

	public Long getRequestedByUserId() {
		return requestedByUserId;
	}

	public void setRequestedByUserId(Long requestedByUserId) {
		this.requestedByUserId = requestedByUserId;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Integer getVisitsTotal() {
		return visitsTotal;
	}

	public void setVisitsTotal(Integer visitsTotal) {
		this.visitsTotal = visitsTotal;
	}

	public Integer getVisitsCovered() {
		return visitsCovered;
	}

	public void setVisitsCovered(Integer visitsCovered) {
		this.visitsCovered = visitsCovered;
	}

	public Integer getContinuityKept() {
		return continuityKept;
	}

	public void setContinuityKept(Integer continuityKept) {
		this.continuityKept = continuityKept;
	}

	public BigDecimal getAddedTravelKm() {
		return addedTravelKm;
	}

	public void setAddedTravelKm(BigDecimal addedTravelKm) {
		this.addedTravelKm = addedTravelKm;
	}

	public LocalDateTime getRanAt() {
		return ranAt;
	}

	public void setRanAt(LocalDateTime ranAt) {
		this.ranAt = ranAt;
	}

	public LocalDateTime getCommittedAt() {
		return committedAt;
	}

	public void setCommittedAt(LocalDateTime committedAt) {
		this.committedAt = committedAt;
	}
}
