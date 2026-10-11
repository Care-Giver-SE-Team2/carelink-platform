package sg.nus.carelink.report.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.report.application.ValueAddedNotifier;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/**
 * Sends the in-app messages as {@code NotificationRequested} events, one per recipient, in the
 * caller's transaction, the way every other notifier does; the notification service keeps them for
 * the recipients' inboxes. A family message links to the request
 * (VALUE_ADDED_REQUEST), which the inbox resolves to its elder for the family access rule; a
 * caregiver message links to the visit. Family recipients are those whose binding to the elder
 * is active right now; the family member who made or answered the request is told too, since
 * a bell entry is also their record of it.
 */
@Component
public class NotificationRequestedValueAddedNotifier implements ValueAddedNotifier {

    static final String REQUEST = "VALUE_ADDED_REQUEST";
    static final String VISIT = "VISIT";

    /** English month names whatever the server's locale. */
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);

    /** Notes the reminder on the request, unless one is noted already: then it has been sent. */
    private static final String REMIND_ONCE = """
            update value_added_service_request set family_reminded_at = :now
            where id = :id and family_reminded_at is null
            """;

    private final JdbcClient jdbc;
    private final CoreApi core;
    private final CoreNames names;
    private final Events events;
    private final Clock clock;

    NotificationRequestedValueAddedNotifier(JdbcClient jdbc, CoreApi core, CoreNames names, Events events, Clock clock) {
        this.jdbc = jdbc;
        this.core = core;
        this.names = names;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void requested(ValueAddedServiceRequest request, String serviceName) {
        toFamily(request, "VALUE_ADDED_REQUESTED", elder(request) + " asked for " + serviceName,
                serviceName + " on " + when(request) + ". Approve or decline it in Extra services.");
    }

    /** Once per request: the request notes its reminder, and one that has a reminder noted gets no other. */
    @Override
    public void reminder(ValueAddedServiceRequest request, String serviceName) {
        int noted = jdbc.sql(REMIND_ONCE).param("now", Timestamp.valueOf(LocalDateTime.now(clock)))
                .param("id", request.id()).update();
        if (noted == 0) {
            return;
        }
        toFamily(request, "VALUE_ADDED_REMINDER", "Still waiting: " + serviceName + " for " + elder(request),
                "Requested for " + when(request) + ". Approve or decline it soon, or it will be cancelled.");
    }

    @Override
    public void caregiverAssigned(ValueAddedServiceRequest request, String serviceName, Long caregiverId) {
        String caregiver = names.caregiver(caregiverId);
        toFamily(request, "VALUE_ADDED_ASSIGNED", serviceName + " for " + elder(request) + " is booked",
                caregiver + " will come on " + when(request) + ".");
        toCaregiver(caregiverId, request, "VALUE_ADDED_ASSIGNED", "New visit: " + serviceName,
                elder(request) + " on " + when(request) + ". Open the visit for the elder's instructions.");
    }

    @Override
    public void cancelled(ValueAddedServiceRequest request, String serviceName, Long caregiverId, Why why) {
        String title = serviceName + " for " + elder(request) + (why == Why.NOT_PROVIDED ? " could not be provided" : " was cancelled");
        String body = switch (why) {
            case BY_MANAGER -> "The care team cancelled the request for " + when(request) + ".";
            case BY_ELDER -> elder(request) + " withdrew the request for " + when(request) + ".";
            case NOT_PROVIDED -> "Nobody could carry out the visit on " + when(request)
                    + ". The care team has been alerted; ask again for another time if it is still needed.";
            case NOT_ANSWERED -> "Nobody approved the request before " + when(request) + ", so it was cancelled.";
        };
        toFamily(request, "VALUE_ADDED_CANCELLED", title, body);
        if (caregiverId != null && why != Why.NOT_PROVIDED) {
            toCaregiver(caregiverId, request, "VALUE_ADDED_CANCELLED", "Visit cancelled: " + serviceName,
                    elder(request) + " on " + when(request) + " is off your schedule.");
        }
    }

    private void toFamily(ValueAddedServiceRequest request, String event, String title, String body) {
        List<Long> users = core.familyMemberIds(request.elderId()).stream()
                .map(id -> core.alertRecipient(request.elderId(), id))
                .filter(CoreApi.AlertRecipient::eligible)
                .map(CoreApi.AlertRecipient::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        users.forEach(user -> send(user, event, title, body, REQUEST, request.id(), request));
    }

    private void toCaregiver(Long caregiverId, ValueAddedServiceRequest request, String event, String title, String body) {
        core.findCaregiverUserId(caregiverId)
                .ifPresent(user -> send(user, event, title, body, VISIT, request.visitId(), request));
    }

    private void send(Long recipient, String event, String title, String body, String resourceType, Long resourceId,
            ValueAddedServiceRequest request) {
        events.publish(NotificationRequested.TYPE, NotificationRequested.inApp(recipient, event, title, body,
                resourceType, resourceId, request.elderId(), LocalDateTime.now(clock)));
    }

    private String elder(ValueAddedServiceRequest request) {
        return names.elder(request.elderId());
    }

    private static String when(ValueAddedServiceRequest request) {
        LocalDateTime at = request.requestedSchedule();
        return at == null ? "an unspecified time" : at.format(WHEN);
    }
}
