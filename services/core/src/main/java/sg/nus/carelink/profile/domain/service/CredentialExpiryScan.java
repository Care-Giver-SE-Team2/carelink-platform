package sg.nus.carelink.profile.domain.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import sg.nus.carelink.profile.domain.model.Credential;

/**
 * SYS01: the daily qualification-expiry scan. Moves each published certificate on to EXPIRING
 * or EXPIRED as its date comes up (Credential#lapseOn), and decides who is told about each move.
 * The stored status is what makes the scan announce each step once: tomorrow's run finds the
 * certificate already EXPIRING and has nothing new to say until it lapses.
 *
 * <p>Who hears depends on the renewal, read the same way the register reads it: a later
 * submission that names this certificate and is not rejected.
 * <ul>
 * <li>No renewal (or only a rejected one): the caregiver, who has to get one, and the managers,
 *     who will lose them from visits that need it.</li>
 * <li>A renewal waiting for review: the managers only. The caregiver has done their part; it is
 *     the review that has to happen before the date.</li>
 * <li>A renewal already approved: nobody. The status still moves, since it is true of this
 *     certificate, but the caregiver holds a valid one.</li>
 * </ul>
 */
public final class CredentialExpiryScan {

	public enum Audience { NOBODY, MANAGERS, CAREGIVER_AND_MANAGERS }

	/** One certificate whose stored status moves today, and who is told. */
	public record Lapse(Credential credential, Audience audience) {

		/** True when the certificate has run out, false when it is only within the warning window. */
		public boolean expired() {
			return credential.status() == Credential.Status.EXPIRED;
		}
	}

	private final int warningDays;

	public CredentialExpiryScan(int warningDays) {
		if (warningDays < 0) {
			throw new IllegalArgumentException("Warning days must be non-negative");
		}
		this.warningDays = warningDays;
	}

	/** Every certificate whose status moves today, in the order given. */
	public List<Lapse> scan(List<Credential> credentials, LocalDate today) {
		List<Lapse> lapses = new ArrayList<>();
		for (Credential c : credentials) {
			c.lapseOn(today, warningDays).ifPresent(next -> lapses.add(new Lapse(next, audience(c, credentials))));
		}
		return lapses;
	}

	private static Audience audience(Credential c, List<Credential> credentials) {
		List<Credential> renewals = credentials.stream()
				.filter(r -> c.id().equals(r.renewsCredentialId()))
				.filter(r -> Objects.equals(c.caregiverId(), r.caregiverId()))
				.filter(r -> r.status() != Credential.Status.REJECTED && r.status() != Credential.Status.REVOKED)
				.toList();
		if (renewals.stream().anyMatch(Credential::isApproved)) {
			return Audience.NOBODY;
		}
		if (renewals.stream().anyMatch(Credential::awaitsReview)) {
			return Audience.MANAGERS;
		}
		return Audience.CAREGIVER_AND_MANAGERS;
	}
}
