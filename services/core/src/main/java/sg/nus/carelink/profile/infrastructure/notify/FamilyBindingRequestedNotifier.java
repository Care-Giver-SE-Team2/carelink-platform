package sg.nus.carelink.profile.infrastructure.notify;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.profile.application.FamilyBindingRequested;

/**
 * Tells a family member that an elder has asked to link with them, so the request does not wait
 * unseen until they happen to open Family bindings. The message links to the binding
 * (FAMILY_BINDING), which the inbox shows to the family member it was sent to whatever the
 * binding's status.
 *
 * <p>Runs after the request commits and sends the message as a {@code NotificationRequested} event
 * in a transaction of its own, so a failed notification never undoes the elder's request; it is
 * logged instead.
 */
@Component
class FamilyBindingRequestedNotifier {

	static final String EVENT = "FAMILY_BINDING_REQUESTED";
	static final String RESOURCE = "FAMILY_BINDING";

	private static final Logger log = LoggerFactory.getLogger(FamilyBindingRequestedNotifier.class);

	private final JdbcClient jdbc;
	private final Events events;
	private final TransactionTemplate ownTransaction;
	private final Clock clock;

	FamilyBindingRequestedNotifier(JdbcClient jdbc, Events events, PlatformTransactionManager transactions,
			Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.ownTransaction = new TransactionTemplate(transactions);
		this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@TransactionalEventListener
	void onFamilyBindingRequested(FamilyBindingRequested event) {
		try {
			notifyFamilyMember(event);
		} catch (RuntimeException failure) {
			log.error("Could not tell family member {} about binding request {}", event.familyMemberId(),
					event.bindingId(), failure);
		}
	}

	private void notifyFamilyMember(FamilyBindingRequested event) {
		Long user = jdbc.sql("select user_id from family_member where id = :id").param("id", event.familyMemberId())
				.query(Long.class).optional().orElse(null);
		if (user == null) {
			return;
		}
		String elder = jdbc.sql("select full_name from elder where id = :id").param("id", event.elderId())
				.query(String.class).optional().orElse("An elder");
		String title = elder + " wants to link with you";
		String body = "Confirm or reject the request in Family bindings to start seeing their care.";
		LocalDateTime now = LocalDateTime.now(clock);
		ownTransaction.executeWithoutResult(status -> events.publish(NotificationRequested.TYPE,
				NotificationRequested.inApp(user, EVENT, title, body, RESOURCE, event.bindingId(), event.elderId(), now)));
	}
}
