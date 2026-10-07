package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * JPA entity for table caregiver_availability.
 *
 * From the caregiver analysis (Availability). Day-by-day windows the caregiver
 * can be rostered into. Combined with absence_report to decide who is free.
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
@Table(name = "caregiver_availability")
public class CaregiverAvailabilityJpaEntity {

	public enum Status {
		AVAILABLE, UNAVAILABLE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	@Column(name = "available_date", nullable = false)
	private LocalDate availableDate;

	@Column(name = "available_start", nullable = false)
	private LocalTime availableStart;

	@Column(name = "available_end", nullable = false)
	private LocalTime availableEnd;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.AVAILABLE;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public CaregiverAvailabilityJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
	}

	public LocalDate getAvailableDate() {
		return availableDate;
	}

	public void setAvailableDate(LocalDate availableDate) {
		this.availableDate = availableDate;
	}

	public LocalTime getAvailableStart() {
		return availableStart;
	}

	public void setAvailableStart(LocalTime availableStart) {
		this.availableStart = availableStart;
	}

	public LocalTime getAvailableEnd() {
		return availableEnd;
	}

	public void setAvailableEnd(LocalTime availableEnd) {
		this.availableEnd = availableEnd;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
