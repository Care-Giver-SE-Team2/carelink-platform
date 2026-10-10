package sg.nus.carelink.notification.messaging;

import org.springframework.stereotype.Component;
import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.notification.application.NotificationRequests;
import sg.nus.carelink.notification.domain.model.Notification;

/**
 * Takes {@code NotificationRequested} off notification's queue and keeps the message for its
 * recipient. It is the worked example of handling an event: a thin adapter, like a controller,
 * that turns the event's record into one call to the application layer.
 *
 * <p>The library runs {@link #handle} in a transaction with the row in {@code consumed_message}
 * that marks the event handled, so a second delivery of the same event changes nothing, and a
 * failure here is delivered again.
 */
@Component
class NotificationRequestedHandler implements EventHandler<NotificationRequested> {

	private final NotificationRequests requests;

	NotificationRequestedHandler(NotificationRequests requests) {
		this.requests = requests;
	}

	@Override
	public String type() {
		return NotificationRequested.TYPE;
	}

	@Override
	public Class<NotificationRequested> payloadType() {
		return NotificationRequested.class;
	}

	@Override
	public void handle(NotificationRequested event, EventMetadata metadata) {
		requests.keep(event.recipientUserId(), event.kind(), Notification.Channel.valueOf(event.channel()),
				event.title(), event.body(), event.resourceType(), event.resourceId(),
				SingaporeTime.local(event.requestedAt()));
	}

}
