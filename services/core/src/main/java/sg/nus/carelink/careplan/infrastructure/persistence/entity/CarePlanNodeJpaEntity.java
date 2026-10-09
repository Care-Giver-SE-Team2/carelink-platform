package sg.nus.carelink.careplan.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for table care_plan_node.
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
@Table(name = "care_plan_node")
public class CarePlanNodeJpaEntity {

	public enum EvidenceType {
		NONE, CHECKLIST, PHOTO, READING
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "care_plan_id", nullable = false)
	private Long carePlanId;

	/** display-only grouping label; no business meaning */
	@Column(name = "group_name", length = 150)
	private String groupName;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	/** e.g. MON,WED,FRI or DAILY */
	@Column(name = "schedule_days", length = 30)
	private String scheduleDays;

	/** hours; TASK nodes only */
	@Column(name = "duration_per_visit", precision = 5, scale = 2)
	private BigDecimal durationPerVisit;

	/** TASK: computed; SUB_PLAN: sum of children */
	@Column(name = "weekly_hours", precision = 6, scale = 2)
	private BigDecimal weeklyHours;

	/** what the caregiver must capture to close the task */
	@Enumerated(EnumType.STRING)
	@Column(name = "evidence_type", nullable = false)
	private EvidenceType evidenceType = EvidenceType.NONE;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder = 0;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	/** The task's per-day schedule (care_plan_node_visit), saved and deleted with the task. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "care_plan_node_id", nullable = false)
	private List<CarePlanNodeVisitJpaEntity> visits = new ArrayList<>();

	public CarePlanNodeJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCarePlanId() {
		return carePlanId;
	}

	public void setCarePlanId(Long carePlanId) {
		this.carePlanId = carePlanId;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getScheduleDays() {
		return scheduleDays;
	}

	public void setScheduleDays(String scheduleDays) {
		this.scheduleDays = scheduleDays;
	}

	public BigDecimal getDurationPerVisit() {
		return durationPerVisit;
	}

	public void setDurationPerVisit(BigDecimal durationPerVisit) {
		this.durationPerVisit = durationPerVisit;
	}

	public BigDecimal getWeeklyHours() {
		return weeklyHours;
	}

	public void setWeeklyHours(BigDecimal weeklyHours) {
		this.weeklyHours = weeklyHours;
	}

	public EvidenceType getEvidenceType() {
		return evidenceType;
	}

	public void setEvidenceType(EvidenceType evidenceType) {
		this.evidenceType = evidenceType;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(Integer displayOrder) {
		this.displayOrder = displayOrder;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public List<CarePlanNodeVisitJpaEntity> getVisits() {
		return visits;
	}

	public void setVisits(List<CarePlanNodeVisitJpaEntity> visits) {
		this.visits = visits;
	}
}
