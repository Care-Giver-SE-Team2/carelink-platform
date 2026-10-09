package sg.nus.carelink.careplan.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for table care_plan.
 *
 * From manager.care_plan, with supersedes_plan_id and published_at restored from
 * the supervisor's screen-2a ERD. Screen 2a shows the version history ("v4 vitals
 * to daily, v3 grooming added"), which is what the supersedes chain records.
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
@Table(name = "care_plan")
public class CarePlanJpaEntity {

	public enum Status {
		DRAFT, PUBLISHED, SUPERSEDED, STOPPED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	/** soft FK to app_user.id */
	@Column(name = "created_by_user_id")
	private Long createdByUserId;

	/** the previous version this one replaced */
	@Column(name = "supersedes_plan_id")
	private Long supersedesPlanId;

	@Column(name = "version", nullable = false)
	private Integer version = 1;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.DRAFT;

	/** rolled up from care_plan_node, not entered by hand */
	@Column(name = "total_hours", precision = 6, scale = 2)
	private BigDecimal totalHours;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	/** when care under this plan begins; required to publish */
	@Column(name = "start_date")
	private LocalDate startDate;

	/** from this date the plan is no longer active */
	@Column(name = "stop_effective_date")
	private LocalDate stopEffectiveDate;

	@Column(name = "stop_reason", length = 500)
	private String stopReason;

	/** soft FK to app_user.id */
	@Column(name = "stopped_by_user_id")
	private Long stoppedByUserId;

	@Column(name = "stopped_at")
	private LocalDateTime stoppedAt;

	public CarePlanJpaEntity() {
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

	public Long getCreatedByUserId() {
		return createdByUserId;
	}

	public void setCreatedByUserId(Long createdByUserId) {
		this.createdByUserId = createdByUserId;
	}

	public Long getSupersedesPlanId() {
		return supersedesPlanId;
	}

	public void setSupersedesPlanId(Long supersedesPlanId) {
		this.supersedesPlanId = supersedesPlanId;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public BigDecimal getTotalHours() {
		return totalHours;
	}

	public void setTotalHours(BigDecimal totalHours) {
		this.totalHours = totalHours;
	}

	public LocalDateTime getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(LocalDateTime publishedAt) {
		this.publishedAt = publishedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getStopEffectiveDate() {
		return stopEffectiveDate;
	}

	public void setStopEffectiveDate(LocalDate stopEffectiveDate) {
		this.stopEffectiveDate = stopEffectiveDate;
	}

	public String getStopReason() {
		return stopReason;
	}

	public void setStopReason(String stopReason) {
		this.stopReason = stopReason;
	}

	public Long getStoppedByUserId() {
		return stoppedByUserId;
	}

	public void setStoppedByUserId(Long stoppedByUserId) {
		this.stoppedByUserId = stoppedByUserId;
	}

	public LocalDateTime getStoppedAt() {
		return stoppedAt;
	}

	public void setStoppedAt(LocalDateTime stoppedAt) {
		this.stoppedAt = stoppedAt;
	}
}
