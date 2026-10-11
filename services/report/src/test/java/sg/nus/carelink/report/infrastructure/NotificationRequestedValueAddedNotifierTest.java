package sg.nus.carelink.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.report.application.ValueAddedNotifier.Why;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

class NotificationRequestedValueAddedNotifierTest {

    private static final LocalDateTime SCHEDULE = LocalDateTime.of(2026, 10, 10, 10, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneId.of("Asia/Singapore"));

    private JdbcClient.StatementSpec reminderNote;
    private CoreApi core;
    private Events events;
    private NotificationRequestedValueAddedNotifier notifier;

    @BeforeEach
    void setUp() {
        JdbcClient jdbc = mock(JdbcClient.class);
        reminderNote = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(jdbc.sql(anyString())).thenReturn(reminderNote);
        core = mock(CoreApi.class);
        when(core.elderReportProfile(10L)).thenReturn(NotificationRequestedValueAddedManagerAlertTest.elder("Tan Ah Kow"));
        when(core.findCaregiverPublicProfile(42L))
                .thenReturn(Optional.of(new CoreApi.CaregiverPublicProfile(42L, "Siti Rahman", List.of())));
        when(core.familyMemberIds(10L)).thenReturn(List.of(20L, 21L));
        when(core.alertRecipient(10L, 20L)).thenReturn(new CoreApi.AlertRecipient(20L, 3L, null));
        when(core.alertRecipient(10L, 21L)).thenReturn(new CoreApi.AlertRecipient(21L, 4L, "BINDING_EXPIRED"));
        events = mock(Events.class);
        notifier = new NotificationRequestedValueAddedNotifier(jdbc, core, new CoreNames(core), events, CLOCK);
    }

    @Test
    void aNewRequestGoesToEachFamilyMemberStillBoundAndLinksToTheRequest() {
        notifier.requested(request(), "Hospital escort");

        assertThat(sent()).singleElement().satisfies(message -> {
            assertThat(message.recipientUserId()).isEqualTo(3L);
            assertThat(message.kind()).isEqualTo("VALUE_ADDED_REQUESTED");
            assertThat(message.resourceType()).isEqualTo("VALUE_ADDED_REQUEST");
            assertThat(message.resourceId()).isEqualTo(5L);
            assertThat(message.elderId()).isEqualTo(10L);
            assertThat(message.title()).isEqualTo("Tan Ah Kow asked for Hospital escort");
            assertThat(message.body()).isEqualTo("Hospital escort on Sat 10 Oct 10:00. Approve or decline it in Extra services.");
        });
    }

    @Test
    void anAssignedCaregiverIsToldThroughTheVisit() {
        when(core.findCaregiverUserId(42L)).thenReturn(Optional.of(90L));

        notifier.caregiverAssigned(request(), "Hospital escort", 42L);

        assertThat(sent()).hasSize(2).last().satisfies(message -> {
            assertThat(message.recipientUserId()).isEqualTo(90L);
            assertThat(message.resourceType()).isEqualTo("VISIT");
            assertThat(message.resourceId()).isEqualTo(77L);
        });
        assertThat(sent().getFirst().body()).isEqualTo("Siti Rahman will come on Sat 10 Oct 10:00.");
    }

    @Test
    void aVisitThatFailedIsNotProvidedAndTheCaregiverIsNotToldItWasCancelled() {
        notifier.cancelled(request(), "Hospital escort", 42L, Why.NOT_PROVIDED);

        assertThat(sent()).singleElement().extracting(NotificationRequested::title)
                .isEqualTo("Hospital escort for Tan Ah Kow could not be provided");
    }

    @Test
    void aReminderGoesOutWhenTheRequestNotesItsFirst() {
        when(reminderNote.update()).thenReturn(1);

        notifier.reminder(request(), "Hospital escort");

        assertThat(sent()).singleElement().extracting(NotificationRequested::kind).isEqualTo("VALUE_ADDED_REMINDER");
        verify(reminderNote).param(eq("id"), eq(5L));
        verify(reminderNote).param(eq("now"), any());
    }

    @Test
    void aReminderIsSentOnlyOnce() {
        when(reminderNote.update()).thenReturn(0);

        notifier.reminder(request(), "Hospital escort");

        assertThat(sent()).isEmpty();
    }

    private List<NotificationRequested> sent() {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeast(0)).publish(eq(NotificationRequested.TYPE), payloads.capture());
        return payloads.getAllValues().stream().map(NotificationRequested.class::cast).toList();
    }

    private static ValueAddedServiceRequest request() {
        return new ValueAddedServiceRequest(5L, 10L, 2L, null, 20L, 77L, SCHEDULE, null,
                ValueAddedServiceRequest.Status.DISPATCHED, null, null, null);
    }
}
