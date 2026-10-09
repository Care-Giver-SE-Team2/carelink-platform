package sg.nus.carelink.profile.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA entity for table intake_application.
 *
 * From family.intake_applications.
 *
 * DECISION 19  Application and master record are two rows, deliberately.
 *              The application holds what the family claimed, at the time
 *              they claimed it, and stays even when rejected; the elder row
 *              is created only on approval, from the (possibly corrected)
 *              application. elder_id is filled at that moment so the two are
 *              linked afterwards. The same "request first, record later"
 *              shape recurs wherever one thing creates another:
 *                intake_application.elder_id            filled on approval
 *                value_added_service_request.visit_id    filled on dispatch
 *                visit_assignment.rostering_candidate_id filled on commit
 *                credential.renews_credential_id         new row on renewal
 *              In every case the later id is NULL until the event happens.
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
@Table(name = "intake_application")
public class IntakeApplicationJpaEntity {

	public enum MobilityLevel {
		INDEPENDENT, ASSISTIVE_CANE, WHEELCHAIR_BEDBOUND
	}

	public enum Status {
		SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "applicant_family_member_id", nullable = false)
	private Long applicantFamilyMemberId;

	@Column(name = "target_elder_name", nullable = false, length = 100)
	private String targetElderName;

	@Column(name = "target_elder_age")
	private Integer targetElderAge;

	@Column(name = "target_address", nullable = false, length = 255)
	private String targetAddress;

	@Column(name = "postal_code", nullable = false, length = 10)
	private String postalCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "mobility_level", nullable = false)
	private MobilityLevel mobilityLevel = MobilityLevel.INDEPENDENT;

	@Column(name = "preferred_dialects", length = 100)
	private String preferredDialects;

	/** requested tasks, e.g. ["BATHING","VITALS"] */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "care_needs")
	private List<String> careNeeds;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	@Column(name = "medical_notes")
	private String medicalNotes;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.SUBMITTED;

	/** soft FK to app_user.id */
	@Column(name = "reviewed_by_user_id")
	private Long reviewedByUserId;

	@Column(name = "review_remarks", length = 255)
	private String reviewRemarks;

	/**
	 * Store creation time in UTC regardless of the database session time zone. Both times are read
	 * and written as LocalDateTime, not through a Timestamp, which Connector/J would shift between
	 * connectionTimeZone and a JVM running in another zone (staging: Asia/Singapore and UTC).
	 */
	@Generated(sql = "UTC_TIMESTAMP()")
	@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	/** set on approval: the elder record created from this application; null while pending or rejected */
	@Column(name = "elder_id")
	private Long elderId;

	public IntakeApplicationJpaEntity() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getApplicantFamilyMemberId() {
		return applicantFamilyMemberId;
	}

	public void setApplicantFamilyMemberId(Long applicantFamilyMemberId) {
		this.applicantFamilyMemberId = applicantFamilyMemberId;
	}

	public String getTargetElderName() {
		return targetElderName;
	}

	public void setTargetElderName(String targetElderName) {
		this.targetElderName = targetElderName;
	}

	public Integer getTargetElderAge() {
		return targetElderAge;
	}

	public void setTargetElderAge(Integer targetElderAge) {
		this.targetElderAge = targetElderAge;
	}

	public String getTargetAddress() {
		return targetAddress;
	}

	public void setTargetAddress(String targetAddress) {
		this.targetAddress = targetAddress;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public MobilityLevel getMobilityLevel() {
		return mobilityLevel;
	}

	public void setMobilityLevel(MobilityLevel mobilityLevel) {
		this.mobilityLevel = mobilityLevel;
	}

	public String getPreferredDialects() {
		return preferredDialects;
	}

	public void setPreferredDialects(String preferredDialects) {
		this.preferredDialects = preferredDialects;
	}

	public List<String> getCareNeeds() {
		return careNeeds;
	}

	public void setCareNeeds(List<String> careNeeds) {
		this.careNeeds = careNeeds;
	}

	public String getMedicalNotes() {
		return medicalNotes;
	}

	public void setMedicalNotes(String medicalNotes) {
		this.medicalNotes = medicalNotes;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Long getReviewedByUserId() {
		return reviewedByUserId;
	}

	public void setReviewedByUserId(Long reviewedByUserId) {
		this.reviewedByUserId = reviewedByUserId;
	}

	public String getReviewRemarks() {
		return reviewRemarks;
	}

	public void setReviewRemarks(String reviewRemarks) {
		this.reviewRemarks = reviewRemarks;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getReviewedAt() {
		return reviewedAt;
	}

	public void setReviewedAt(LocalDateTime reviewedAt) {
		this.reviewedAt = reviewedAt;
	}

	public Long getElderId() {
		return elderId;
	}

	public void setElderId(Long elderId) {
		this.elderId = elderId;
	}
}
