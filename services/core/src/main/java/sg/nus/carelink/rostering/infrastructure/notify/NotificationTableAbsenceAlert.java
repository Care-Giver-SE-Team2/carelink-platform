package sg.nus.carelink.rostering.infrastructure.notify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.repository.AbsenceAlert;

/**
 * Writes UC-MG04's absence notice as rows in {@code notification}, the in-app inbox every
 * manager's bell reads. The managers are found the way the incident notifier finds them: every
 * enabled account with the MANAGER role.
 */
@Component
class NotificationTableAbsenceAlert implements AbsenceAlert {

	private static final String MANAGERS = """
			select u.id from app_user u join user_role r on r.user_id = u.id
			where r.role = 'MANAGER' and u.enabled = true
			""";

	private static final String INSERT = """
			insert into notification
			    (recipient_user_id, event_type, channel, title, body, resource_type, resource_id, status, created_at)
			values (:userId, :eventType, 'IN_APP', :title, :body, 'ABSENCE', :absenceId, 'PENDING', :createdAt)
			""";

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

	private final JdbcClient jdbc;
	private final Clock clock;

	NotificationTableAbsenceAlert(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void requested(AbsenceReport absence, String caregiverName) {
		String who = caregiverName == null ? "A caregiver" : caregiverName;
		String title = "%s asks for leave: %s".formatted(who, days(absence.startDate(), absence.endDate()));
		String body = "%s leave%s. Approve or reject it on the Absences screen; approving it vacates their visits"
				+ " those days for re-rostering.";
		String reason = absence.reason() == null || absence.reason().isBlank() ? "" : ": " + absence.reason().strip();
		tellManagers("ABSENCE_REQUESTED", absence, title, body.formatted(type(absence.type()), reason));
	}

	@Override
	public void visitsRerostered(AbsenceReport absence, String caregiverName, Rerostered what) {
		String who = caregiverName == null ? "a caregiver" : caregiverName;
		String title = "New visits on %s's leave were re-rostered: %s".formatted(who, days(absence.startDate(),
				absence.endDate()));
		int total = what.offered() + what.settled() + what.uncovered();
		List<String> parts = new ArrayList<>();
		if (what.offered() > 0) {
			parts.add(what.offered() + " offered to families");
		}
		if (what.settled() > 0) {
			parts.add(what.settled() + " given to the best replacement");
		}
		if (what.uncovered() > 0) {
			parts.add(what.uncovered() + " left uncovered with an incident");
		}
		String body = ("Since coverage was confirmed, the roster added visits on these days, and the nightly run"
				+ " re-rostered %d visit%s: %s. Review them on the Absences screen, reassign any you want to, then"
				+ " confirm coverage again.")
				.formatted(total, total == 1 ? "" : "s", String.join(", ", parts));
		tellManagers("ABSENCE_VISITS_REROSTERED", absence, title, body);
	}

	private void tellManagers(String eventType, AbsenceReport absence, String title, String body) {
		List<Long> managers = jdbc.sql(MANAGERS).query(Long.class).list();
		Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
		for (Long manager : managers) {
			jdbc.sql(INSERT)
					.param("userId", manager)
					.param("eventType", eventType)
					.param("title", trim(title, 150))
					.param("body", trim(body, 1000))
					.param("absenceId", absence.id())
					.param("createdAt", now)
					.update();
		}
	}

	private static String days(LocalDate start, LocalDate end) {
		return start.equals(end) ? start.format(DAY) : start.format(DAY) + " to " + end.format(DAY);
	}

	private static String type(AbsenceReport.Type type) {
		return switch (type == null ? AbsenceReport.Type.OTHER : type) {
			case SICK -> "Sick";
			case ANNUAL -> "Annual";
			case EMERGENCY -> "Emergency";
			case OTHER -> "Other";
		};
	}

	private static String trim(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}
}
