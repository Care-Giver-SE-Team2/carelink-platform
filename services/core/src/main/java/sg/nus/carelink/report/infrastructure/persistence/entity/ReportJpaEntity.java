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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for table report.
 *
 * From manager.report. `audience` is what the redaction design problem
 * switches on, and the scope actually applied comes from
 * elder_family_binding.access_scope at generation time.
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
@Table(name = "report")
public class ReportJpaEntity {

	public enum Audience {
		FAMILY, REGULATOR, INTERNAL
	}

	public enum Status {
		DRAFT, PUBLISHED, ARCHIVED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	/** soft FK to app_user.id */
	@Column(name = "generated_by_user_id")
	private Long generatedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "audience", nullable = false)
	private Audience audience;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.DRAFT;

	/**
	 * rendered sections, already filtered for the audience.
	 *
	 * <p>Kept as a String: Hibernate passes a String through to a JSON column untouched, so the
	 * text written is the text stored. Turning ReportContent into that text, and back, is
	 * ReportContentJson's job in the adapter.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content")
	private String content;

	/**
	 * Written by the application rather than left to the column's DEFAULT CURRENT_TIMESTAMP.
	 * The database's clock is not the one the rest of a report is dated by - its session zone
	 * is not the connection zone the driver converts through - and a report whose createdAt
	 * came back null from the save that filed it would answer POST /generate without one.
	 */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public ReportJpaEntity() {
		// JPA instantiates an entity through its no-argument constructor and then sets the
		// columns itself; the mapper does the same through the setters. Nothing to do here.
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

	public Long getGeneratedByUserId() {
		return generatedByUserId;
	}

	public void setGeneratedByUserId(Long generatedByUserId) {
		this.generatedByUserId = generatedByUserId;
	}

	public Audience getAudience() {
		return audience;
	}

	public void setAudience(Audience audience) {
		this.audience = audience;
	}

	public LocalDate getPeriodStart() {
		return periodStart;
	}

	public void setPeriodStart(LocalDate periodStart) {
		this.periodStart = periodStart;
	}

	public LocalDate getPeriodEnd() {
		return periodEnd;
	}

	public void setPeriodEnd(LocalDate periodEnd) {
		this.periodEnd = periodEnd;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
