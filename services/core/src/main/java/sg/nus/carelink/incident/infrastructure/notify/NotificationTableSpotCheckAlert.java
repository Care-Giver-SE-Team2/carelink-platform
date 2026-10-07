package sg.nus.carelink.incident.infrastructure.notify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.domain.repository.SpotCheckAlert;

/**
 * Writes UC-MG08's messages as rows in {@code notification}, PENDING like every other alert in
 * this module until somebody builds the sender (SUP-02). The family is found through their
 * active bindings, the caregiver through their account, with the same plain statements
 * {@link NotificationTableAlert} uses.
 */
@Component
class NotificationTableSpotCheckAlert implements SpotCheckAlert {

	private static final String BOUND_FAMILY = """
			select f.user_id from elder_family_binding b
			join family_member f on f.id = b.family_member_id
			where b.elder_id = :elderId
			  and b.status = 'ACTIVE'
			  and (b.expires_at is null or b.expires_at > :now)
			  and f.user_id is not null
			""";

	private static final String CAREGIVER_ACCOUNT = "select user_id from caregiver where id = :caregiverId";

	private static final String INSERT = """
			insert into notification
			    (recipient_user_id, event_type, channel, title, body, resource_type, resource_id, status, created_at)
			values (:userId, :eventType, 'IN_APP', :title, :body, 'SPOT_CHECK', :checkId, 'PENDING', :createdAt)
			""";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

	private final JdbcClient jdbc;
	private final Clock clock;

	NotificationTableSpotCheckAlert(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void approvalRequested(SpotCheck check, Names names) {
		String when = check.proposedTime().format(WHEN);
		for (Long family : boundFamily(check.elderId())) {
			write(family, "SPOT_CHECK_REQUESTED", "May a manager watch the visit on %s?".formatted(when),
					("To check the quality of care, a manager would like to be present at %s's visit on %s: %s. "
							+ "Nobody comes to watch unless you agree.").formatted(elder(names), when, check.reason()),
					check.id());
		}
	}

	@Override
	public void familyAnswered(SpotCheck check, Names names) {
		if (check.raisedByUserId() == null) {
			return;
		}
		boolean approved = check.approvalStatus() == SpotCheck.ApprovalStatus.APPROVED;
		write(check.raisedByUserId(), approved ? "SPOT_CHECK_APPROVED" : "SPOT_CHECK_DECLINED",
				"Spot check on %s %s".formatted(check.proposedTime().format(WHEN), approved ? "approved" : "declined"),
				approved
						? "%s's family agreed. Record the conclusion on the day.".formatted(elder(names))
						: "%s's family declined: %s".formatted(elder(names), check.closingReason()),
				check.id());
	}

	@Override
	public void concluded(SpotCheck check, Names names) {
		String when = check.proposedTime().format(WHEN);
		String result = check.result() == SpotCheck.Result.MEETS_STANDARD ? "met the standard" : "needs improvement";
		for (Long family : boundFamily(check.elderId())) {
			write(family, "SPOT_CHECK_CONCLUDED", "Spot check on %s: %s".formatted(when, result),
					check.finding() == null ? "The conclusion is on the spot checks page." : check.finding(), check.id());
		}
		jdbc.sql(CAREGIVER_ACCOUNT).param("caregiverId", check.caregiverId()).query(Long.class).optional()
				.ifPresent(user -> write(user, "SPOT_CHECK_CONCLUDED",
						"Your visit on %s was spot-checked: %s".formatted(when, result),
						"You can read the notes and respond to them.", check.id()));
	}

	@Override
	public void withdrawn(SpotCheck check, Names names) {
		for (Long family : boundFamily(check.elderId())) {
			write(family, "SPOT_CHECK_WITHDRAWN",
					"The spot check on %s is not going ahead".formatted(check.proposedTime().format(WHEN)),
					check.closingReason(), check.id());
		}
	}

	private List<Long> boundFamily(Long elderId) {
		return jdbc.sql(BOUND_FAMILY)
				.param("elderId", elderId)
				.param("now", Timestamp.valueOf(LocalDateTime.now(clock)))
				.query(Long.class)
				.list();
	}

	private void write(Long userId, String eventType, String title, String body, Long checkId) {
		jdbc.sql(INSERT)
				.param("userId", userId)
				.param("eventType", eventType)
				.param("title", trim(title, 150))
				.param("body", trim(body, 1000))
				.param("checkId", checkId)
				.param("createdAt", Timestamp.valueOf(LocalDateTime.now(clock)))
				.update();
	}

	private static String elder(Names names) {
		return names.elderName() == null ? "The elder" : names.elderName();
	}

	private static String trim(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}
}
