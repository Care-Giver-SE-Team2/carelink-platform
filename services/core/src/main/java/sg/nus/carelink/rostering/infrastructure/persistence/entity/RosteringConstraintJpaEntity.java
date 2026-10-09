package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for table rostering_constraint.
 *
 * The rule set, data-driven so a rule can be switched off or its threshold
 * changed without a release (screen 1b: "configurable").
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
@Table(name = "rostering_constraint")
public class RosteringConstraintJpaEntity {

	public enum Kind {
		HARD, SOFT
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** CERTIFICATION_VALID, DAILY_VISIT_CAP, SECTOR_BAND, DIALECT_MATCH, CONTINUITY, TRAVEL_DISTANCE, DAILY_HOURS_CAP */
	@Column(name = "code", nullable = false, length = 40, unique = true)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	/** HARD excludes the candidate; SOFT only changes the score */
	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false)
	private Kind kind;

	/** threshold shown on screen 4c: 8 visits, 5 km, 8.0 hours */
	@Column(name = "parameter_value", length = 50)
	private String parameterValue;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	public RosteringConstraintJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Kind getKind() {
		return kind;
	}

	public void setKind(Kind kind) {
		this.kind = kind;
	}

	public String getParameterValue() {
		return parameterValue;
	}

	public void setParameterValue(String parameterValue) {
		this.parameterValue = parameterValue;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
