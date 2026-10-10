package sg.nus.carelink.profile.infrastructure.notify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import sg.nus.carelink.careplan.application.CarePlanPublished;
import sg.nus.carelink.profile.application.FamilyAlertRecipients;

/**
 * Tells the elder's family when the manager publishes a care plan version, so they know what was
 * planned and can read it on their Care plan page. The message links to the plan (CARE_PLAN),
 * which the inbox resolves to its elder for the family access rule; recipients are the family
 * members whose binding to the elder is active right now.
 *
 * <p>Runs after the publish commits and writes on its own, so a failed notification never undoes
 * the manager's publish; it is logged instead.
 */
@Component
class CarePlanPublishedFamilyNotifier {

	static final String EVENT = "CARE_PLAN_PUBLISHED";
	static final String RESOURCE = "CARE_PLAN";

	private static final Logger log = LoggerFactory.getLogger(CarePlanPublishedFamilyNotifier.class);

	/** English names whatever the server's locale: "Mon 3 Nov 2026". */
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

	private final JdbcClient jdbc;
	private final FamilyAlertRecipients families;
	private final Clock clock;

	CarePlanPublishedFamilyNotifier(JdbcClient jdbc, FamilyAlertRecipients families, Clock clock) {
		this.jdbc = jdbc;
		this.families = families;
		this.clock = clock;
	}

	@TransactionalEventListener
	void onCarePlanPublished(CarePlanPublished event) {
		try {
			notifyFamily(event);
		} catch (RuntimeException failure) {
			log.error("Could not tell the family of elder {} about care plan v{}", event.elderId(), event.version(), failure);
		}
	}

	private void notifyFamily(CarePlanPublished event) {
		String elder = jdbc.sql("select full_name from elder where id = :id").param("id", event.elderId())
				.query(String.class).optional().orElse("Elder #" + event.elderId());
		String title = "Care plan v" + event.version() + " for " + elder + " is ready";
		String body = (event.version() == 1 ? "Care starts on " : "It replaces the current plan from ")
				+ day(event.startDate()) + ". See what is planned on the Care plan page.";
		List<Long> users = families.familyMemberIds(event.elderId()).stream()
				.map(id -> families.resolve(event.elderId(), id))
				.filter(FamilyAlertRecipients.Candidate::eligible)
				.map(FamilyAlertRecipients.Candidate::userId)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		users.forEach(user -> jdbc.sql("""
				insert into notification
				(recipient_user_id,event_type,channel,title,body,resource_type,resource_id,status,created_at)
				values (:recipient,:event,'IN_APP',:title,:body,:type,:resource,'PENDING',:createdAt)
				""").param("recipient", user).param("event", EVENT)
				// Stamped from the application clock, as the other notifiers do, so the inbox orders it with them.
				.param("createdAt", Timestamp.valueOf(LocalDateTime.now(clock)))
				.param("title", clip(title, 150)).param("body", clip(body, 1000))
				.param("type", RESOURCE).param("resource", event.carePlanId()).update());
	}

	private static String day(LocalDate date) {
		return date == null ? "a date to be confirmed" : date.format(DAY);
	}

	private static String clip(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
	}
}
