package sg.nus.carelink.profile.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.Credential;

/**
 * UC-MG06: the manager's certification register — one row per certificate that still matters,
 * what state it is in, and whether it needs the manager before it lapses.
 *
 * <p>A renewal chain shows as one row: a certificate is left out once a later submission
 * renews it, unless that submission was rejected, in which case the old certificate is still
 * the one the caregiver holds and comes back. A pending renewal is shown against the expiry
 * of the certificate it replaces, since that is the date it has to be published by.
 */
public final class CredentialRegisterPolicy {

	/** What the register shows in the State column. REMINDED = approved, within the warning window. */
	public enum State { SUBMITTED, REJECTED, REMINDED, PUBLISHED, EXPIRED, REVOKED }

	/**
	 * One register row.
	 *
	 * @param replaces the certificate a renewal replaces; null for a first submission
	 * @param watchedExpiry the date this row is racing, or null when nothing is due (no expiry,
	 *        a first submission or a revocation). A rejected renewal still races the expiry of
	 *        the certificate it would have replaced, since a valid renewal is due by then.
	 * @param daysUntilExpiry days from today to {@code watchedExpiry}, negative once past
	 * @param expiring true when the row needs the manager before, or because, it lapses. Never
	 *        for a rejection: the certificate it would have replaced is back as a row of its own
	 *        and counts there.
	 * @param coveredUntil the last day the caregiver is covered by this row for rostering:
	 *        the watched expiry, {@link Credential#PERMANENT} when it never expires, yesterday
	 *        when they do not hold it at all, or null for a rejected row (whose old certificate,
	 *        if any, is a row of its own)
	 */
	public record Entry(Credential credential, Credential replaces, State state, LocalDate watchedExpiry,
			Long daysUntilExpiry, boolean expiring, LocalDate coveredUntil) {
	}

	private final int warningDays;

	public CredentialRegisterPolicy(int warningDays) {
		if (warningDays < 0) {
			throw new IllegalArgumentException("Warning days must be non-negative");
		}
		this.warningDays = warningDays;
	}

	/** Submitted rows first, then by watched expiry (none last), then by id. */
	public List<Entry> register(List<Credential> credentials, LocalDate today) {
		Map<Long, Credential> byId = new HashMap<>();
		credentials.forEach(c -> byId.put(c.id(), c));
		Set<Long> superseded = new HashSet<>();
		for (Credential c : credentials) {
			Credential parent = c.renewsCredentialId() == null ? null : byId.get(c.renewsCredentialId());
			if (parent != null && Objects.equals(parent.caregiverId(), c.caregiverId())
					&& c.status() != Credential.Status.REJECTED) {
				superseded.add(parent.id());
			}
		}
		return credentials.stream()
				.filter(c -> !superseded.contains(c.id()))
				.map(c -> entry(c, c.renewsCredentialId() == null ? null : byId.get(c.renewsCredentialId()), today))
				.sorted(Comparator.comparing((Entry e) -> e.state() != State.SUBMITTED)
						.thenComparing(Entry::watchedExpiry, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(e -> e.credential().id()))
				.toList();
	}

	private Entry entry(Credential c, Credential replaces, LocalDate today) {
		State state = stateOf(c, today);
		LocalDate watched = switch (state) {
			case SUBMITTED, REJECTED -> replaces == null || !replaces.isApproved() ? null : dated(replaces.expiryDate());
			case REMINDED, PUBLISHED, EXPIRED -> dated(c.expiryDate());
			case REVOKED -> null;
		};
		Long days = watched == null ? null : ChronoUnit.DAYS.between(today, watched);
		boolean expiring = state != State.REJECTED && days != null && days <= warningDays;
		LocalDate coveredUntil = switch (state) {
			case REJECTED -> null;
			case REVOKED -> today.minusDays(1);
			case REMINDED, PUBLISHED, EXPIRED -> c.expiryDate();
			case SUBMITTED -> watched == null ? today.minusDays(1) : watched;
		};
		return new Entry(c, replaces, state, watched, days, expiring, coveredUntil);
	}

	private State stateOf(Credential c, LocalDate today) {
		return switch (c.status()) {
			case SUBMITTED -> State.SUBMITTED;
			case REJECTED -> State.REJECTED;
			case REVOKED -> State.REVOKED;
			case PUBLISHED, EXPIRING, EXPIRED -> {
				if (c.expiryDate().isBefore(today)) {
					yield State.EXPIRED;
				}
				yield ChronoUnit.DAYS.between(today, c.expiryDate()) <= warningDays ? State.REMINDED : State.PUBLISHED;
			}
		};
	}

	/** Null for the schema's "never expires" date, so it never counts as due. */
	private static LocalDate dated(LocalDate expiry) {
		return expiry == null || expiry.equals(Credential.PERMANENT) ? null : expiry;
	}
}
