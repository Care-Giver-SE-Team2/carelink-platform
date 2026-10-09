package sg.nus.carelink.rostering.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA entity for table rostering_candidate.
 *
 * Every caregiver considered for every visit in the run, including those a
 * HARD constraint excluded. "Suggestion 1 of 4" is option_rank 1..4.
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
@Table(name = "rostering_candidate")
public class RosteringCandidateJpaEntity {

	public enum Outcome {
		SELECTED, SUGGESTED, EXCLUDED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "rostering_run_id", nullable = false)
	private Long rosteringRunId;

	@Column(name = "visit_id", nullable = false)
	private Long visitId;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	/** null when excluded */
	@Column(name = "option_rank")
	private Integer optionRank;

	/** objective score; its meaning depends on rostering_run.objective */
	@Column(name = "score", precision = 6, scale = 2)
	private BigDecimal score;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false)
	private Outcome outcome;

	/** rostering_constraint.code of the HARD rule that excluded this candidate */
	@Column(name = "excluded_by_code", length = 40)
	private String excludedByCode;

	/** why this candidate ranks where it does, in words (V12) */
	@Column(name = "match_reason", length = 120)
	private String matchReason;

	public RosteringCandidateJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getRosteringRunId() {
		return rosteringRunId;
	}

	public void setRosteringRunId(Long rosteringRunId) {
		this.rosteringRunId = rosteringRunId;
	}

	public Long getVisitId() {
		return visitId;
	}

	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
	}

	public Integer getOptionRank() {
		return optionRank;
	}

	public void setOptionRank(Integer optionRank) {
		this.optionRank = optionRank;
	}

	public BigDecimal getScore() {
		return score;
	}

	public void setScore(BigDecimal score) {
		this.score = score;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public void setOutcome(Outcome outcome) {
		this.outcome = outcome;
	}

	public String getExcludedByCode() {
		return excludedByCode;
	}

	public void setExcludedByCode(String excludedByCode) {
		this.excludedByCode = excludedByCode;
	}

	public String getMatchReason() {
		return matchReason;
	}

	public void setMatchReason(String matchReason) {
		this.matchReason = matchReason;
	}
}
