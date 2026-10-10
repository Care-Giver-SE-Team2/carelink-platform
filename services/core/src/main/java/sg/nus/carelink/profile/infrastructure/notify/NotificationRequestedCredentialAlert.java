package sg.nus.carelink.profile.infrastructure.notify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.repository.CredentialExpiryAlert;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Audience;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Lapse;
import sg.nus.carelink.shared.security.Role;

/**
 * Sends SYS01's expiry alerts as {@code NotificationRequested} events, one per recipient, in the
 * scan's transaction; notification keeps each one for its recipient's in-app inbox. This was one
 * of the classes that wrote rows into {@code notification} directly, and it is the worked example
 * for the rest: the port it implements and the code that calls it did not change.
 *
 * <p>Reads {@code app_user}/{@code user_role} for the managers with a plain statement: both
 * tables stay in core with it.
 */
@Component
class NotificationRequestedCredentialAlert implements CredentialExpiryAlert {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private static final String MANAGERS = """
			select u.id from app_user u
			join user_role r on r.user_id = u.id
			where u.enabled = true and r.role = :role
			""";

	private final JdbcClient jdbc;
	private final Events events;
	private final Clock clock;

	NotificationRequestedCredentialAlert(JdbcClient jdbc, Events events, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public int lapsed(Lapse lapse, Caregiver caregiver, String credentialName) {
		if (lapse.audience() == Audience.NOBODY) {
			return 0;
		}
		String eventType = lapse.expired() ? "CREDENTIAL_EXPIRED" : "CREDENTIAL_EXPIRING";
		String on = lapse.credential().expiryDate().format(DATE);
		Long credentialId = lapse.credential().id();
		int told = 0;

		if (lapse.audience() == Audience.CAREGIVER_AND_MANAGERS && caregiver.userId() != null) {
			write(caregiver.userId(), eventType,
					lapse.expired()
							? "Your %s certificate expired on %s".formatted(credentialName, on)
							: "Your %s certificate expires on %s".formatted(credentialName, on),
					"Upload the renewed certificate in the caregiver app so a manager can review it.",
					credentialId);
			told++;
		}

		String title = lapse.expired()
				? "%s's %s expired on %s".formatted(caregiver.fullName(), credentialName, on)
				: "%s's %s expires on %s".formatted(caregiver.fullName(), credentialName, on);
		String body = lapse.audience() == Audience.MANAGERS
				? "A renewal is waiting for your review on the Certifications tab."
				: lapse.expired()
						? "No renewal has been submitted. Visits that need it show as at risk on the Certifications tab."
						: "No renewal has been submitted yet.";
		for (Long managerId : managers()) {
			write(managerId, eventType, title, body, credentialId);
			told++;
		}
		return told;
	}

	private List<Long> managers() {
		return jdbc.sql(MANAGERS).param("role", Role.MANAGER.name()).query(Long.class).list();
	}

	private void write(Long userId, String eventType, String title, String body, Long credentialId) {
		events.publish(NotificationRequested.TYPE, new NotificationRequested(userId, eventType, "IN_APP",
				trim(title, 150), body, "CREDENTIAL", credentialId, null, SingaporeTime.of(LocalDateTime.now(clock))));
	}

	private static String trim(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}
}
