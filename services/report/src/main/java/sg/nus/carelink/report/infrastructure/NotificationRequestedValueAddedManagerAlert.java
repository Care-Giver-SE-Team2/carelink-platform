package sg.nus.carelink.report.infrastructure;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.report.application.ValueAddedManagerAlert;

/**
 * Tells every manager about an approval as {@code NotificationRequested} events, in the same
 * transaction as the approval; the notification service keeps them for the managers' in-app bells.
 * The bell links a VISIT message to the manager's Extra services screen, where a visit nobody holds
 * is staffed. The managers and the names come from core.
 */
@Component
public class NotificationRequestedValueAddedManagerAlert implements ValueAddedManagerAlert {
    /** English month names whatever the server's locale. */
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private final CoreApi core;
    private final CoreNames names;
    private final Events events;
    private final Clock clock;

    NotificationRequestedValueAddedManagerAlert(CoreApi core, CoreNames names, Events events, Clock clock) {
        this.core = core;
        this.names = names;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void approved(Long visitId, Long elderId, String serviceName, LocalDateTime start, Long caregiverId) {
        String title = caregiverId == null ? "Extra service needs caregiver assignment"
                : "Extra service approved and assigned";
        String body = names.elder(elderId) + ": " + serviceName + " on " + start.format(WHEN)
                + (caregiverId == null
                        ? ". The primary caregiver is not free; assign a caregiver in Extra services."
                        : ". " + names.caregiver(caregiverId) + " (primary caregiver) is on it. Review in Extra services.");
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long managerId : core.enabledAccountsWithRole("MANAGER").userIds()) {
            events.publish(NotificationRequested.TYPE, NotificationRequested.inApp(managerId, "VALUE_ADDED_APPROVED",
                    title, body, "VISIT", visitId, elderId, now));
        }
    }
}
