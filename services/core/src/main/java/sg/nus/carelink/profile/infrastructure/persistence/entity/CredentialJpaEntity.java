package sg.nus.carelink.profile.infrastructure.persistence.entity;

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

/**
 * JPA entity for table credential.
 *
 * From manager.credential, now pointing at credential_type. SYS01 scans
 * expiry_date daily; screen 2d shows the 30-day warning threshold.
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
@Table(name = "credential")
public class CredentialJpaEntity {

	public enum Status {
		SUBMITTED, PUBLISHED, REJECTED, EXPIRING, EXPIRED, REVOKED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "caregiver_id", nullable = false)
	private Long caregiverId;

	@Column(name = "credential_type_id", nullable = false)
	private Long credentialTypeId;

	/** soft FK to app_user.id */
	@Column(name = "reviewed_by_user_id")
	private Long reviewedByUserId;

	@Column(name = "review_note", length = 500)
	private String reviewNote;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "certificate_no", length = 100)
	private String certificateNo;

	@Column(name = "issuing_body", length = 150)
	private String issuingBody;

	@Column(name = "valid_from")
	private LocalDate validFrom;

	/** default = permanent; the daily expiry scan never reaches it */
	@Column(name = "expiry_date", nullable = false)
	private LocalDate expiryDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private Status status = Status.SUBMITTED;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	/** FK self: the credential this one replaces on renewal; null for a first submission */
	@Column(name = "renews_credential_id")
	private Long renewsCredentialId;

	public CredentialJpaEntity() {
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

	public Long getCredentialTypeId() {
		return credentialTypeId;
	}

	public void setCredentialTypeId(Long credentialTypeId) {
		this.credentialTypeId = credentialTypeId;
	}

	public Long getReviewedByUserId() {
		return reviewedByUserId;
	}

	public void setReviewedByUserId(Long reviewedByUserId) {
		this.reviewedByUserId = reviewedByUserId;
	}

	public String getReviewNote() {
		return reviewNote;
	}

	public void setReviewNote(String reviewNote) {
		this.reviewNote = reviewNote;
	}

	public LocalDateTime getReviewedAt() {
		return reviewedAt;
	}

	public void setReviewedAt(LocalDateTime reviewedAt) {
		this.reviewedAt = reviewedAt;
	}

	public String getCertificateNo() {
		return certificateNo;
	}

	public void setCertificateNo(String certificateNo) {
		this.certificateNo = certificateNo;
	}

	public String getIssuingBody() {
		return issuingBody;
	}

	public void setIssuingBody(String issuingBody) {
		this.issuingBody = issuingBody;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(LocalDate validFrom) {
		this.validFrom = validFrom;
	}

	public LocalDate getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(LocalDate expiryDate) {
		this.expiryDate = expiryDate;
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

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Long getRenewsCredentialId() {
		return renewsCredentialId;
	}

	public void setRenewsCredentialId(Long renewsCredentialId) {
		this.renewsCredentialId = renewsCredentialId;
	}
}
