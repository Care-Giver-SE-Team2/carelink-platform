package sg.nus.carelink.report.infrastructure;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.profile.application.FamilyAlertRecipients;
import sg.nus.carelink.report.application.ValueAddedNotifier;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/**
 * Writes the in-app messages into {@code notification} as PENDING, in the caller's transaction,
 * the way every other notifier does. A family message links to the request
 * (VALUE_ADDED_REQUEST), which the inbox resolves to its elder for the family access rule; a
 * caregiver message links to the visit. Family recipients are those whose binding to the elder
 * is active right now; the family member who made or answered the request is told too, since
 * a bell entry is also their record of it.
 */
@Component
public class NotificationTableValueAddedNotifier implements ValueAddedNotifier {

    static final String REQUEST = "VALUE_ADDED_REQUEST";
    static final String VISIT = "VISIT";

    /** English month names whatever the server's locale. */
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final FamilyAlertRecipients families;

    public NotificationTableValueAddedNotifier(JdbcClient jdbc, FamilyAlertRecipients families) {
        this.jdbc = jdbc;
        this.families = families;
    }

    @Override
    public void requested(ValueAddedServiceRequest request, String serviceName) {
        toFamily(request, "VALUE_ADDED_REQUESTED", elder(request) + " asked for " + serviceName,
                serviceName + " on " + when(request) + ". Approve or decline it in Extra services.");
    }

    /** Once per request: a reminder already written for it is not written again. */
    @Override
    public void reminder(ValueAddedServiceRequest request, String serviceName) {
        Long sent = jdbc.sql("""
                select count(*) from notification
                where event_type = 'VALUE_ADDED_REMINDER' and resource_type = :type and resource_id = :id
                """).param("type", REQUEST).param("id", request.id()).query(Long.class).single();
        if (sent > 0) {
            return;
        }
        toFamily(request, "VALUE_ADDED_REMINDER", "Still waiting: " + serviceName + " for " + elder(request),
                "Requested for " + when(request) + ". Approve or decline it soon, or it will be cancelled.");
    }

    @Override
    public void caregiverAssigned(ValueAddedServiceRequest request, String serviceName, Long caregiverId) {
        String caregiver = caregiver(caregiverId);
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
        List<Long> users = families.familyMemberIds(request.elderId()).stream()
                .map(id -> families.resolve(request.elderId(), id))
                .filter(FamilyAlertRecipients.Candidate::eligible)
                .map(FamilyAlertRecipients.Candidate::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        users.forEach(user -> insert(user, event, title, body, REQUEST, request.id()));
    }

    private void toCaregiver(Long caregiverId, ValueAddedServiceRequest request, String event, String title, String body) {
        jdbc.sql("select user_id from caregiver where id = :id").param("id", caregiverId).query(Long.class).optional()
                .ifPresent(user -> insert(user, event, title, body, VISIT, request.visitId()));
    }

    private void insert(Long recipient, String event, String title, String body, String resourceType, Long resourceId) {
        jdbc.sql("""
                insert into notification
                (recipient_user_id,event_type,channel,title,body,resource_type,resource_id,status,created_at)
                values (:recipient,:event,'IN_APP',:title,:body,:type,:resource,'PENDING',CURRENT_TIMESTAMP)
                """).param("recipient", recipient).param("event", event)
                .param("title", clip(title, 150)).param("body", clip(body, 1000))
                .param("type", resourceType).param("resource", resourceId).update();
    }

    private String elder(ValueAddedServiceRequest request) {
        return jdbc.sql("select full_name from elder where id = :id").param("id", request.elderId())
                .query(String.class).optional().orElse("Elder #" + request.elderId());
    }

    private String caregiver(Long caregiverId) {
        return jdbc.sql("select full_name from caregiver where id = :id").param("id", caregiverId)
                .query(String.class).optional().orElse("Caregiver #" + caregiverId);
    }

    private static String when(ValueAddedServiceRequest request) {
        LocalDateTime at = request.requestedSchedule();
        return at == null ? "an unspecified time" : at.format(WHEN);
    }

    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
