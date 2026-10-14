package sg.nus.carelink.openplatform.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * One application to join the Open Platform (PT01). It is PENDING until a CareLink manager decides,
 * then APPROVED, when the provider it becomes is created, or REJECTED, with the reason the provider is
 * shown. A decided application is never decided again; a rejected provider applies anew.
 *
 * <p>Must not import JPA or Spring Data; ArchUnit rejects the build if it does.
 *
 * @param portalUserId the portal user's {@code sub}, which its tokens carry: who may read this application
 * @param reviewedBy core's account of the manager who decided; null while pending
 * @param providerId the provider it became; null unless approved
 */
public record ProviderApplication(
		Long id,
		ApplicationDetails details,
		String portalUserId,
		Status status,
		String reason,
		Instant submittedAt,
		Instant reviewedAt,
		Long reviewedBy,
		Long providerId) {

	public enum Status {
		PENDING, APPROVED, REJECTED
	}

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private static final Pattern REFERENCE = Pattern.compile("APP-(\\d{4})-(\\d{5,})");

	public ProviderApplication {
		Objects.requireNonNull(details, "details");
		Objects.requireNonNull(portalUserId, "portalUserId");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(submittedAt, "submittedAt");
		if ((status == Status.APPROVED) != (providerId != null)) {
			throw new IllegalArgumentException("An application names its provider exactly when it is approved");
		}
	}

	/** A new application, waiting for a manager. */
	public static ProviderApplication submitted(ApplicationDetails details, String portalUserId, Instant now) {
		return new ProviderApplication(null, details, portalUserId, Status.PENDING, null, now, null, null, null);
	}

	/**
	 * What the provider quotes: {@code APP-2026-00417}, the year it was submitted in Singapore and the
	 * id. Only a kept application has one.
	 */
	public String reference() {
		Objects.requireNonNull(id, "Only a kept application has a reference");
		return "APP-%d-%05d".formatted(submittedAt.atZone(SINGAPORE).getYear(), id);
	}

	/** The id a reference names, or empty when it is not one. */
	public static Optional<Long> idOf(String reference) {
		Matcher matcher = REFERENCE.matcher(reference == null ? "" : reference.strip());
		return matcher.matches() ? Optional.of(Long.parseLong(matcher.group(2))) : Optional.empty();
	}

	/** A pending or approved application holds its email: nobody may apply with it again. */
	public boolean holdsItsEmail() {
		return status != Status.REJECTED;
	}

	public ProviderApplication approved(long newProviderId, long managerId, Instant at) {
		requirePending();
		return new ProviderApplication(id, details, portalUserId, Status.APPROVED, null, submittedAt, at, managerId,
				newProviderId);
	}

	public ProviderApplication rejected(String why, long managerId, Instant at) {
		requirePending();
		if (why == null || why.isBlank()) {
			throw new IllegalArgumentException("A rejection gives the provider its reason");
		}
		return new ProviderApplication(id, details, portalUserId, Status.REJECTED, why.strip(), submittedAt, at,
				managerId, null);
	}

	private void requirePending() {
		if (status != Status.PENDING) {
			throw new BusinessRuleViolation("ALREADY_DECIDED", "This application has already been decided.");
		}
	}
}
