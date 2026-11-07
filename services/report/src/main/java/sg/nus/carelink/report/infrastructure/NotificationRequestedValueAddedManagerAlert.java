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
    public void approved(Long visitId, Long elderId, String serviceName, LocalDateTime start) {
        String body = names.elder(elderId) + ": " + serviceName + " on " + start.format(WHEN)
                + ". Assign a caregiver or offer it to a provider in Extra services.";
        tellManagers("VALUE_ADDED_APPROVED", "Extra service needs assignment", body, visitId, elderId);
    }

    @Override
    public void providerRemoved(Long visitId, Long elderId, String serviceName, LocalDateTime start,
            String providerName) {
        String body = names.elder(elderId) + ": " + serviceName + " on " + start.format(WHEN) + ". "
                + (providerName == null ? "The provider" : providerName)
                + " had taken it on and was removed; assign a caregiver in Extra services.";
        tellManagers("VALUE_ADDED_PROVIDER_REMOVED", "Extra service needs caregiver assignment", body, visitId,
                elderId);
    }

    private void tellManagers(String kind, String title, String body, Long visitId, Long elderId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long managerId : core.enabledAccountsWithRole("MANAGER").userIds()) {
            events.publish(NotificationRequested.TYPE, NotificationRequested.inApp(managerId, kind,
                    title, body, "VISIT", visitId, elderId, now));
        }
    }
}
