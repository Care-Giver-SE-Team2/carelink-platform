package sg.nus.carelink.report.infrastructure.persistence.entity;

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
 * JPA entity for table report_amendment (V8, kind since V19).
 *
 * <p>A note appended to a report - a correction or a follow-up. Rows are inserted and never
 * updated, so every column is {@code updatable = false}; a correction to a correction is
 * another row.
 *
 * <p>report_id is a plain id rather than an association to {@link ReportJpaEntity}, as
 * everywhere else in the schema's mappings: the adapter reads a report's corrections with
 * one query and nothing loads lazily behind its back.
 *
 * <p>The schema is owned by Flyway. Hibernate validates this mapping at start-up and never
 * alters the table.
 */
@Entity
@Table(name = "report_amendment")
public class ReportAmendmentJpaEntity {

	public enum Kind {
		CORRECTION, FOLLOW_UP
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "report_id", nullable = false, updatable = false)
	private Long reportId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false)
	private Kind kind = Kind.CORRECTION;

	@Column(name = "note", nullable = false, updatable = false, length = 1000)
	private String note;

	/** soft FK to app_user.id */
	@Column(name = "author_user_id", nullable = false, updatable = false)
	private Long authorUserId;

	/** Written by the application, on the same clock as the report it corrects. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public ReportAmendmentJpaEntity() {
		// JPA instantiates an entity through its no-argument constructor and then sets the
		// columns itself; the mapper does the same through the setters. Nothing to do here.
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getReportId() {
		return reportId;
	}

	public void setReportId(Long reportId) {
		this.reportId = reportId;
	}

	public Kind getKind() {
		return kind;
	}

	public void setKind(Kind kind) {
		this.kind = kind;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Long getAuthorUserId() {
		return authorUserId;
	}

	public void setAuthorUserId(Long authorUserId) {
		this.authorUserId = authorUserId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
