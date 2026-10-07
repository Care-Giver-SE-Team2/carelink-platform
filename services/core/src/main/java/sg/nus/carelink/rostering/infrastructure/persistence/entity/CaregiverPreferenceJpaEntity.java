package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity for table caregiver_preference.
 *
 * From the caregiver analysis (WorkPreference). UC-CG02: what the caregiver would
 * prefer. Rostering reads it as a soft preference; it never overrides a hard
 * constraint such as an expired credential or an approved absence.
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
@Table(name = "caregiver_preference")
public class CaregiverPreferenceJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "caregiver_id", nullable = false, unique = true)
	private Long caregiverId;

	/** comma-separated */
	@Column(name = "preferred_service_types", length = 255)
	private String preferredServiceTypes;

	/** comma-separated */
	@Column(name = "preferred_sectors", length = 255)
	private String preferredSectors;

	/** e.g. MON 08:00-12:00;WED 13:00-17:00 */
	@Column(name = "preferred_time_windows", length = 255)
	private String preferredTimeWindows;

	@Column(name = "max_visits_per_day")
	private Integer maxVisitsPerDay;

	/** screen 1b checks "daily hours cap" against this */
	@Column(name = "max_hours_per_day", precision = 4, scale = 1)
	private BigDecimal maxHoursPerDay;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public CaregiverPreferenceJpaEntity() {
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

	public String getPreferredServiceTypes() {
		return preferredServiceTypes;
	}

	public void setPreferredServiceTypes(String preferredServiceTypes) {
		this.preferredServiceTypes = preferredServiceTypes;
	}

	public String getPreferredSectors() {
		return preferredSectors;
	}

	public void setPreferredSectors(String preferredSectors) {
		this.preferredSectors = preferredSectors;
	}

	public String getPreferredTimeWindows() {
		return preferredTimeWindows;
	}

	public void setPreferredTimeWindows(String preferredTimeWindows) {
		this.preferredTimeWindows = preferredTimeWindows;
	}

	public Integer getMaxVisitsPerDay() {
		return maxVisitsPerDay;
	}

	public void setMaxVisitsPerDay(Integer maxVisitsPerDay) {
		this.maxVisitsPerDay = maxVisitsPerDay;
	}

	public BigDecimal getMaxHoursPerDay() {
		return maxHoursPerDay;
	}

	public void setMaxHoursPerDay(BigDecimal maxHoursPerDay) {
		this.maxHoursPerDay = maxHoursPerDay;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
