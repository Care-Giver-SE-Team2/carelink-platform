package sg.nus.carelink.rostering.infrastructure.notify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.RosterChangeAlert;

/**
 * Writes UC-MG04's messages as rows in {@code notification}, the in-app inbox, the same way the
 * incident module's alerts do: PENDING until somebody builds the sender (SUP-02).
 *
 * <p>Reads three tables it does not own - {@code elder_family_binding} with {@code family_member},
 * {@code elder} and {@code caregiver} - with plain statements that take an id, to find the
 * accounts to address. Reading is not owning; no other module changes for this.
 *
 * <p>The absent caregiver is told their visit is taken care of without being told by whom or why
 * they are away: the family learns who is coming, not what is wrong with the person who is not.
 */
@Component
class NotificationTableRosterAlert implements RosterChangeAlert {

	/** Family members whose binding to the elder is active now; the moment comes from the clock. */
	private static final String BOUND_FAMILY = """
			select f.user_id from elder_family_binding b
			join family_member f on f.id = b.family_member_id
			where b.elder_id = :elderId
			  and b.status = 'ACTIVE'
			  and (b.expires_at is null or b.expires_at > :now)
			  and f.user_id is not null
			""";

	private static final String ELDER_ACCOUNT = "select user_id from elder where id = :elderId and user_id is not null";

	private static final String CAREGIVER_ACCOUNT = "select user_id from caregiver where id = :caregiverId";

	private static final String INSERT = """
			insert into notification
			    (recipient_user_id, event_type, channel, title, body, resource_type, resource_id, status, created_at)
			values (:userId, :eventType, 'IN_APP', :title, :body, 'ROSTER_CHANGE', :changeId, 'PENDING', :createdAt)
			""";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

	private final JdbcClient jdbc;
	private final Clock clock;

	NotificationTableRosterAlert(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void offered(RosterChange change, Notice notice) {
		String when = change.visitStart().format(WHEN);
		for (Long family : boundFamily(change.elderId())) {
			write(family, "ROSTER_CHANGE_OFFERED", "Your caregiver is away for the visit on " + when,
					"%s's visit on %s needs another caregiver. We suggest %s. Keep them, pick another, move the visit "
							+ "or skip it by %s; if we hear nothing, %s will come."
							.formatted(elder(notice), when, notice.caregiverName(), change.respondBy().format(WHEN),
									notice.caregiverName()),
					change.id());
		}
	}

	@Override
	public void coordinating(RosterChange change, Notice notice) {
		String when = change.visitStart().format(WHEN);
		for (Long family : boundFamily(change.elderId())) {
			write(family, "ROSTER_CHANGE_COORDINATING", "We are finding a caregiver for " + when,
					"Nobody is free yet for %s's visit on %s. A manager is handling it and will let you know."
							.formatted(elder(notice), when),
					change.id());
		}
		caregiverAccount(change.originalCaregiverId()).ifPresent(user -> write(user, "VISIT_RELEASED",
				"Your visit on %s is being covered".formatted(when),
				"Nothing more is needed from you for this visit.", change.id()));
	}

	@Override
	public void settled(RosterChange change, Notice notice) {
		if (change.outcome() == null) {
			return;
		}
		String was = change.visitStart().format(WHEN);
		String why = notice.why() == null ? "" : " " + notice.why() + ".";
		switch (change.outcome()) {
			case REPLACED -> {
				tellFamily(change, "%s will visit %s on %s".formatted(notice.caregiverName(), elder(notice), was),
						"The visit goes ahead with %s.%s".formatted(notice.caregiverName(), why));
				tellElder(change, "%s will visit you on %s".formatted(notice.caregiverName(), was),
						"Your usual caregiver is away, so %s is coming instead.".formatted(notice.caregiverName()));
				tellNewCaregiver(change, notice, was);
			}
			case RESCHEDULED -> {
				String now = notice.newStart() == null ? "a new time" : notice.newStart().format(WHEN);
				tellFamily(change, "%s's visit has moved to %s".formatted(elder(notice), now),
						"It was due on %s; %s will come at the new time.".formatted(was, notice.caregiverName()));
				tellElder(change, "Your visit has moved to " + now,
						"%s will come then instead of on %s.".formatted(notice.caregiverName(), was));
				tellNewCaregiver(change, notice, now);
			}
			case SKIPPED -> {
				tellFamily(change, "The visit on %s is skipped".formatted(was),
						"As you asked, nobody will come for this visit.");
				tellElder(change, "No visit on " + was, "Your family asked to skip this visit.");
			}
			case WITHDRAWN -> {
				return;
			}
		}
		caregiverAccount(change.originalCaregiverId()).ifPresent(user -> write(user, "VISIT_RELEASED",
				"Your visit on %s is taken care of".formatted(was),
				"Nothing more is needed from you for this visit.", change.id()));
	}

	// -------------------------------------------------------------------- the three ends ---

	private void tellFamily(RosterChange change, String title, String body) {
		boundFamily(change.elderId()).forEach(user -> write(user, "ROSTER_CHANGE_SETTLED", title, body, change.id()));
	}

	private void tellElder(RosterChange change, String title, String body) {
		jdbc.sql(ELDER_ACCOUNT).param("elderId", change.elderId()).query(Long.class).optional()
				.ifPresent(user -> write(user, "VISIT_CHANGED", title, body, change.id()));
	}

	private void tellNewCaregiver(RosterChange change, Notice notice, String when) {
		caregiverAccount(notice.caregiverId()).ifPresent(user -> write(user, "VISIT_ASSIGNED",
				"New visit: %s on %s".formatted(elder(notice), when),
				"You are covering for a colleague who is away. The visit is on your schedule.", change.id()));
	}

	// -------------------------------------------------------------------- queries ---

	private List<Long> boundFamily(Long elderId) {
		return jdbc.sql(BOUND_FAMILY)
				.param("elderId", elderId)
				.param("now", Timestamp.valueOf(LocalDateTime.now(clock)))
				.query(Long.class)
				.list();
	}

	private Optional<Long> caregiverAccount(Long caregiverId) {
		if (caregiverId == null) {
			return Optional.empty();
		}
		return jdbc.sql(CAREGIVER_ACCOUNT).param("caregiverId", caregiverId).query(Long.class).optional()
				.filter(Objects::nonNull);
	}

	private void write(Long userId, String eventType, String title, String body, Long changeId) {
		jdbc.sql(INSERT)
				.param("userId", userId)
				.param("eventType", eventType)
				.param("title", trim(title, 150))
				.param("body", trim(body, 1000))
				.param("changeId", changeId)
				.param("createdAt", Timestamp.valueOf(LocalDateTime.now(clock)))
				.update();
	}

	private static String elder(Notice notice) {
		return notice.elderName() == null ? "The elder" : notice.elderName();
	}

	private static String trim(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}
}
