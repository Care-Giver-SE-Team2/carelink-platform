package sg.nus.carelink.visit.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * JPA entity for table visit.
 *
 * Merges manager.visit + elder.service_order.
 *
 * DECISION 9  These were the same thing under two names. manager.visit is a
 *             scheduled home visit; elder.service_order is a service with a
 *             caregiver, a time window and a completion confirmation. Keeping
 *             both would mean the caregiver closing a visit while the elder
 *             confirms an order, with nothing joining them.
 *
 *             The merged status list is elder's, which is the one the State
 *             design problem is built on, plus manager's EXCEPTION outcome.
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
@Table(name = "visit")
public class VisitJpaEntity {

	public enum Status {
		SCHEDULED, ARRIVED, IN_PROGRESS, COMPLETED, VERIFIED, AUTO_CLOSED, EXCEPTION, CANCELLED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	/** null while unassigned */
	@Column(name = "caregiver_id")
	private Long caregiverId;

	/** soft FK to care_plan_node.id; which plan task this fulfils */
	@Column(name = "care_plan_node_id")
	private Long carePlanNodeId;

	/** set when this visit was re-rostered because of an absence */
	@Column(name = "absence_id")
	private Long absenceId;

	@Column(name = "service_type", length = 50)
	private String serviceType;

	@Column(name = "scheduled_start", nullable = false)
	private LocalDateTime scheduledStart;

	@Column(name = "scheduled_end")
	private LocalDateTime scheduledEnd;

	@Column(name = "checked_in_at")
	private LocalDateTime checkedInAt;

	@Column(name = "checked_out_at")
	private LocalDateTime checkedOutAt;

	/** COMPLETED = checked out, awaiting the elder; VERIFIED = elder confirmed and supervisor verified; AUTO_CLOSED = the elder never answered (DECISION 16) */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.SCHEDULED;

	/** when the current state must have advanced by; SYS03 infers a missed check-in from it */
	@Column(name = "state_deadline")
	private LocalDateTime stateDeadline;

	/** soft FK to care_plan.id: the plan VERSION in force when this visit was created (story A6). Superseded versions are never edited, so pointing at the row is the snapshot */
	@Column(name = "care_plan_id")
	private Long carePlanId;

	/** optimistic lock for concurrent state changes */
	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public VisitJpaEntity() {
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

	public Long getCarePlanNodeId() {
		return carePlanNodeId;
	}

	public void setCarePlanNodeId(Long carePlanNodeId) {
		this.carePlanNodeId = carePlanNodeId;
	}

	public Long getAbsenceId() {
		return absenceId;
	}

	public void setAbsenceId(Long absenceId) {
		this.absenceId = absenceId;
	}

	public String getServiceType() {
		return serviceType;
	}

	public void setServiceType(String serviceType) {
		this.serviceType = serviceType;
	}

	public LocalDateTime getScheduledStart() {
		return scheduledStart;
	}

	public void setScheduledStart(LocalDateTime scheduledStart) {
		this.scheduledStart = scheduledStart;
	}

	public LocalDateTime getScheduledEnd() {
		return scheduledEnd;
	}

	public void setScheduledEnd(LocalDateTime scheduledEnd) {
		this.scheduledEnd = scheduledEnd;
	}

	public LocalDateTime getCheckedInAt() {
		return checkedInAt;
	}

	public void setCheckedInAt(LocalDateTime checkedInAt) {
		this.checkedInAt = checkedInAt;
	}

	public LocalDateTime getCheckedOutAt() {
		return checkedOutAt;
	}

	public void setCheckedOutAt(LocalDateTime checkedOutAt) {
		this.checkedOutAt = checkedOutAt;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public LocalDateTime getStateDeadline() {
		return stateDeadline;
	}

	public void setStateDeadline(LocalDateTime stateDeadline) {
		this.stateDeadline = stateDeadline;
	}

	public Long getCarePlanId() {
		return carePlanId;
	}

	public void setCarePlanId(Long carePlanId) {
		this.carePlanId = carePlanId;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
