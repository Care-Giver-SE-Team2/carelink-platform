package sg.nus.carelink.report.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * JPA entity for table value_added_service_request.
 *
 * Merges elder.value_added_service_request + family.value_added_service_orders.
 *
 * DECISION 12  One table, not two. The elder requests (UC-EL02) and the family
 *              approves (UC-FM08) — the same row, two states. Two tables would
 *              have made "which request did this order come from" a join
 *              nobody had modelled.
 *
 * DECISION 13  No price column. The proposal puts billing and settlement out
 *              of scope, and elder.value_added_service carried a price.
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
@Table(name = "value_added_service_request")
public class ValueAddedServiceRequestJpaEntity {

	public enum Status {
		PENDING_APPROVAL, APPROVED, REJECTED, DISPATCHED, COMPLETED, CANCELLED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	@Column(name = "value_added_service_id", nullable = false)
	private Long valueAddedServiceId;

	/** null when the elder requested it directly */
	@Column(name = "requested_by_family_member_id")
	private Long requestedByFamilyMemberId;

	@Column(name = "approving_family_member_id")
	private Long approvingFamilyMemberId;

	/** set once dispatched as a visit */
	@Column(name = "visit_id")
	private Long visitId;

	@Column(name = "requested_schedule")
	private LocalDateTime requestedSchedule;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "special_instructions")
	private String specialInstructions;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.PENDING_APPROVAL;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public ValueAddedServiceRequestJpaEntity() {
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

	public Long getValueAddedServiceId() {
		return valueAddedServiceId;
	}

	public void setValueAddedServiceId(Long valueAddedServiceId) {
		this.valueAddedServiceId = valueAddedServiceId;
	}

	public Long getRequestedByFamilyMemberId() {
		return requestedByFamilyMemberId;
	}

	public void setRequestedByFamilyMemberId(Long requestedByFamilyMemberId) {
		this.requestedByFamilyMemberId = requestedByFamilyMemberId;
	}

	public Long getApprovingFamilyMemberId() {
		return approvingFamilyMemberId;
	}

	public void setApprovingFamilyMemberId(Long approvingFamilyMemberId) {
		this.approvingFamilyMemberId = approvingFamilyMemberId;
	}

	public Long getVisitId() {
		return visitId;
	}

	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}

	public LocalDateTime getRequestedSchedule() {
		return requestedSchedule;
	}

	public void setRequestedSchedule(LocalDateTime requestedSchedule) {
		this.requestedSchedule = requestedSchedule;
	}

	public String getSpecialInstructions() {
		return specialInstructions;
	}

	public void setSpecialInstructions(String specialInstructions) {
		this.specialInstructions = specialInstructions;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(LocalDateTime decidedAt) {
		this.decidedAt = decidedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
