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
 * JPA entity for table caregiver_review.
 *
 * Merges family.periodic_caregiver_reviews with elder.renewal_decision.
 *
 * DECISION 14  The renewal decision is a column here, not a table of its own.
 *              elder.renewal_decision and family.periodic_caregiver_reviews
 *              recorded the same judgement twice; a periodic review that ends
 *              in a renewal decision is one act, not two.
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
@Table(name = "caregiver_review")
public class CaregiverReviewJpaEntity {

	public enum RenewalDecision {
		RENEW_CURRENT, REQUEST_CHANGE, CANCEL_SERVICE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "family_member_id", nullable = false)
	private Long familyMemberId;

	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "overall_rating", nullable = false)
	private Byte overallRating;

	@Column(name = "punctuality_score")
	private Byte punctualityScore;

	@Column(name = "care_quality_score")
	private Byte careQualityScore;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "feedback_notes")
	private String feedbackNotes;

	@Enumerated(EnumType.STRING)
	@Column(name = "renewal_decision", nullable = false)
	private RenewalDecision renewalDecision = RenewalDecision.RENEW_CURRENT;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public CaregiverReviewJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getFamilyMemberId() {
		return familyMemberId;
	}

	public void setFamilyMemberId(Long familyMemberId) {
		this.familyMemberId = familyMemberId;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public Long getCaregiverId() {
		return caregiverId;
	}

	public void setCaregiverId(Long caregiverId) {
		this.caregiverId = caregiverId;
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

	public Byte getOverallRating() {
		return overallRating;
	}

	public void setOverallRating(Byte overallRating) {
		this.overallRating = overallRating;
	}

	public Byte getPunctualityScore() {
		return punctualityScore;
	}

	public void setPunctualityScore(Byte punctualityScore) {
		this.punctualityScore = punctualityScore;
	}

	public Byte getCareQualityScore() {
		return careQualityScore;
	}

	public void setCareQualityScore(Byte careQualityScore) {
		this.careQualityScore = careQualityScore;
	}

	public String getFeedbackNotes() {
		return feedbackNotes;
	}

	public void setFeedbackNotes(String feedbackNotes) {
		this.feedbackNotes = feedbackNotes;
	}

	public RenewalDecision getRenewalDecision() {
		return renewalDecision;
	}

	public void setRenewalDecision(RenewalDecision renewalDecision) {
		this.renewalDecision = renewalDecision;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
