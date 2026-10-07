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
 * JPA entity for table rostering_candidate_check.
 *
 * The "constraints checked" panel on screen 1b, one row per rule per
 * candidate: Dialect match PASS, Certification valid PASS, Continuity
 * "2 prior visits", Daily hours cap "6.5 / 8.0".
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
@Table(name = "rostering_candidate_check")
public class RosteringCandidateCheckJpaEntity {

	public enum Result {
		PASS, FAIL, NOT_APPLICABLE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "rostering_candidate_id", nullable = false)
	private Long rosteringCandidateId;

	@Column(name = "rostering_constraint_id", nullable = false)
	private Long rosteringConstraintId;

	@Enumerated(EnumType.STRING)
	@Column(name = "result", nullable = false)
	private Result result;

	/** what the screen shows next to the result */
	@Column(name = "detail", length = 100)
	private String detail;

	public RosteringCandidateCheckJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getRosteringCandidateId() {
		return rosteringCandidateId;
	}

	public void setRosteringCandidateId(Long rosteringCandidateId) {
		this.rosteringCandidateId = rosteringCandidateId;
	}

	public Long getRosteringConstraintId() {
		return rosteringConstraintId;
	}

	public void setRosteringConstraintId(Long rosteringConstraintId) {
		this.rosteringConstraintId = rosteringConstraintId;
	}

	public Result getResult() {
		return result;
	}

	public void setResult(Result result) {
		this.result = result;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}
}
