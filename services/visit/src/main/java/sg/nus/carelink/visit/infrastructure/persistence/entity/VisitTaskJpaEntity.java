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
 * JPA entity for table visit_task.
 *
 * From the caregiver analysis (VisitTaskExecution). The caregiver ticks tasks off
 * during the visit; each row is one care_plan_node of type TASK as executed on
 * this visit. UC-CG03, UC-CG05.
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
@Table(name = "visit_task")
public class VisitTaskJpaEntity {

	public enum Status {
		PENDING, DONE, SKIPPED, REFUSED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "visit_id", nullable = false)
	private Long visitId;

	/** soft FK to care_plan_node.id; the TASK this executes */
	@Column(name = "care_plan_node_id")
	private Long carePlanNodeId;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.PENDING;

	@Column(name = "outcome", length = 255)
	private String outcome;

	@Column(name = "caregiver_note", length = 500)
	private String caregiverNote;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	public VisitTaskJpaEntity() {
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

	public Long getCarePlanNodeId() {
		return carePlanNodeId;
	}

	public void setCarePlanNodeId(Long carePlanNodeId) {
		this.carePlanNodeId = carePlanNodeId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getOutcome() {
		return outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public String getCaregiverNote() {
		return caregiverNote;
	}

	public void setCaregiverNote(String caregiverNote) {
		this.caregiverNote = caregiverNote;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}
}
