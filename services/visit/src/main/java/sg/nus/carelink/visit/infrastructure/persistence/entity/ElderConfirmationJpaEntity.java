package sg.nus.carelink.visit.infrastructure.persistence.entity;

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

import java.time.LocalDateTime;

/**
 * JPA entity for table elder_confirmation.
 *
 * Merges elder.service_feedback with the caregiver analysis's ElderConfirmation,
 * revised after the elder-module owner's review ("elder_confirmation design
 * recommendation", 2026-09-05).
 *
 * DECISION 16  The elder's confirmation is its own record, not two columns on
 *              visit, and it records ONLY the elder's own act. The caregiver
 *              analysis: "submitted by the Elder independently; the Caregiver
 *              may not confirm on their behalf". The elder-module review went
 *              further and removed confirmed_by: a caregiver-recorded verbal
 *              acknowledgement and a timeout closure are not confirmations and
 *              must never be stored as one. A row exists only once the elder
 *              has answered, so there is no NOT_RESPONDED state here.
 *                caregiver checks out        -> visit COMPLETED (awaiting the elder)
 *                elder answers YES           -> row CONFIRMED (+ rating, comment)
 *                elder answers NO            -> row DISPUTED, incident opened
 *                elder never answers         -> visit AUTO_CLOSED, no row here
 *                caregiver notes a verbal OK -> visit_state_transition / audit_log
 *              If a family proxy is ever allowed, add confirmation_source
 *              (ELDER_SELF / FAMILY_PROXY) and confirmed_by_user_id at that time.
 *              EL01 rates one visit here; FM09 rates a period in caregiver_review.
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
@Table(name = "elder_confirmation")
public class ElderConfirmationJpaEntity {

	public enum ConfirmationStatus {
		CONFIRMED, DISPUTED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "visit_id", nullable = false, unique = true)
	private Long visitId;

	/** the elder who answered; must equal visit.elder_id */
	@Column(name = "elder_id", nullable = false)
	private Long elderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "confirmation_status", nullable = false)
	private ConfirmationStatus confirmationStatus;

	/** EL01: the elder rates this one visit */
	@Column(name = "rating")
	private Byte rating;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "comment")
	private String comment;

	@Column(name = "confirmed_at", nullable = false)
	private LocalDateTime confirmedAt;

	public ElderConfirmationJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getVisitId() {
		return visitId;
	}

	public void setVisitId(Long visitId) {
		this.visitId = visitId;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}

	public ConfirmationStatus getConfirmationStatus() {
		return confirmationStatus;
	}

	public void setConfirmationStatus(ConfirmationStatus confirmationStatus) {
		this.confirmationStatus = confirmationStatus;
	}

	public Byte getRating() {
		return rating;
	}

	public void setRating(Byte rating) {
		this.rating = rating;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public void setConfirmedAt(LocalDateTime confirmedAt) {
		this.confirmedAt = confirmedAt;
	}
}
