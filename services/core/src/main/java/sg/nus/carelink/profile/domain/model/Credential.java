package sg.nus.carelink.profile.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Domain model for credential.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record Credential(
		Long id,
		Long caregiverId,
		Long credentialTypeId,
		Long reviewedByUserId,
		String certificateNo,
		String issuingBody,
		LocalDate validFrom,
		LocalDate expiryDate,
		Credential.Status status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		Long renewsCredentialId,
		String reviewNote,
		LocalDateTime reviewedAt) {

	/** Expiry date the schema gives a credential that never expires. */
	public static final LocalDate PERMANENT = LocalDate.of(9999, 12, 31);

	/** A credential that has not been reviewed: no note and no review time yet. */
	public Credential(Long id, Long caregiverId, Long credentialTypeId, Long reviewedByUserId, String certificateNo,
			String issuingBody, LocalDate validFrom, LocalDate expiryDate, Status status, LocalDateTime createdAt,
			LocalDateTime updatedAt, Long renewsCredentialId) {
		this(id, caregiverId, credentialTypeId, reviewedByUserId, certificateNo, issuingBody, validFrom, expiryDate,
				status, createdAt, updatedAt, renewsCredentialId, null, null);
	}

	/** True while it waits for a manager: submitted from the caregiver app and not yet reviewed. */
	public boolean awaitsReview() {
		return status == Status.SUBMITTED;
	}

	/** Published and not revoked, whether or not it has run out since. */
	public boolean isApproved() {
		return status == Status.PUBLISHED || status == Status.EXPIRING || status == Status.EXPIRED;
	}

	/**
	 * UC-MG06: the manager accepts the certificate. A renewal must replace a certificate of the
	 * same caregiver and type; the replaced one keeps its own status and dates, and is superseded
	 * because this one now renews it.
	 *
	 * @param replaced the certificate named by {@code renewsCredentialId}, or null for a new one
	 * @throws BusinessRuleViolation CREDENTIAL_NOT_SUBMITTED if it is not waiting for review, or
	 *         RENEWAL_MISMATCH if it renews another caregiver's certificate or another type
	 */
	public Credential publish(Credential replaced, Long reviewerId, LocalDateTime at) {
		requireAwaitingReview();
		if (renewsCredentialId != null && (replaced == null || !renewsCredentialId.equals(replaced.id())
				|| !Objects.equals(caregiverId, replaced.caregiverId())
				|| !Objects.equals(credentialTypeId, replaced.credentialTypeId()))) {
			throw new BusinessRuleViolation("RENEWAL_MISMATCH",
					"Credential " + id + " does not renew a certificate of the same caregiver and type");
		}
		return reviewed(Status.PUBLISHED, reviewerId, null, at);
	}

	/**
	 * Refuses the certificate, whether it is wrong or only unreadable. The caregiver is told why,
	 * so a reason is required.
	 */
	public Credential reject(Long reviewerId, String reason, LocalDateTime at) {
		requireAwaitingReview();
		if (blankToNull(reason) == null) {
			throw new BusinessRuleViolation("REJECT_REASON_REQUIRED", "A reason is required to reject a certificate");
		}
		return reviewed(Status.REJECTED, reviewerId, reason.strip(), at);
	}

	/**
	 * SYS01: the status the daily expiry scan stores for this certificate today. A published
	 * certificate becomes EXPIRING once it is within {@code warningDays} of its expiry date and
	 * EXPIRED the day after it; a certificate that was not seen in the window (published late,
	 * or the scan did not run) goes straight to EXPIRED. Only ever forwards, and never for a
	 * certificate that is not approved or never expires.
	 *
	 * @param today current date in Asia/Singapore
	 * @return this certificate with its new status, or empty when today changes nothing
	 */
	public Optional<Credential> lapseOn(LocalDate today, int warningDays) {
		if ((status != Status.PUBLISHED && status != Status.EXPIRING) || PERMANENT.equals(expiryDate)) {
			return Optional.empty();
		}
		Status next = expiryDate.isBefore(today) ? Status.EXPIRED
				: !expiryDate.isAfter(today.plusDays(warningDays)) ? Status.EXPIRING
				: status;
		return next == status ? Optional.empty() : Optional.of(withStatus(next));
	}

	private Credential withStatus(Status next) {
		return new Credential(id, caregiverId, credentialTypeId, reviewedByUserId, certificateNo, issuingBody,
				validFrom, expiryDate, next, createdAt, updatedAt, renewsCredentialId, reviewNote, reviewedAt);
	}

	private void requireAwaitingReview() {
		if (!awaitsReview()) {
			throw new BusinessRuleViolation("CREDENTIAL_NOT_SUBMITTED",
					"Credential " + id + " is " + status + " and no longer waiting for review");
		}
	}

	private Credential reviewed(Status outcome, Long reviewerId, String note, LocalDateTime at) {
		return new Credential(id, caregiverId, credentialTypeId, reviewerId, certificateNo, issuingBody, validFrom,
				expiryDate, outcome, createdAt, updatedAt, renewsCredentialId, note, at);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	/**
	 * Projects a public credential status without changing the stored review state.
	 * Expiry dates include the named day; future validFrom remains a separate display constraint.
	 *
	 * @param today Current date in Asia/Singapore
	 * @return Public status, or empty for credentials still in or turned away by review
	 * @author Wang Zhili
	 */
	public Optional<Status> publicStatusOn(LocalDate today) {
		return switch (status) {
			case SUBMITTED, REJECTED -> Optional.empty();
			case REVOKED, EXPIRED -> Optional.of(status);
			case PUBLISHED, EXPIRING -> Optional.of(expiryDate.isBefore(today) ? Status.EXPIRED : status);
		};
	}

	public enum Status {
		SUBMITTED, PUBLISHED, REJECTED, EXPIRING, EXPIRED, REVOKED
	}
}
