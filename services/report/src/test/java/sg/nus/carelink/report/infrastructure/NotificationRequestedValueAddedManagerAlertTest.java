package sg.nus.carelink.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;

class NotificationRequestedValueAddedManagerAlertTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 10, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Singapore"));

    private final CoreApi core = mock(CoreApi.class);
    private final Events events = mock(Events.class);
    private final NotificationRequestedValueAddedManagerAlert alert =
            new NotificationRequestedValueAddedManagerAlert(core, new CoreNames(core), events, CLOCK);

    @BeforeEach
    void setUp() {
        when(core.elderReportProfile(1L)).thenReturn(elder("Tan Ah Kow"));
        when(core.findCaregiverPublicProfile(7L))
                .thenReturn(Optional.of(new CoreApi.CaregiverPublicProfile(7L, "Siti Rahman", List.of())));
        when(core.enabledAccountsWithRole("MANAGER")).thenReturn(new CoreApi.AccountIds(List.of(4L)));
    }

    @Test
    void assignedCaregiverSendsSuccessNotificationToManager() {
        alert.approved(14L, 1L, "Hospital escort", START, 7L);

        assertThat(sent()).singleElement().satisfies(message -> {
            assertThat(message.recipientUserId()).isEqualTo(4L);
            assertThat(message.kind()).isEqualTo("VALUE_ADDED_APPROVED");
            assertThat(message.channel()).isEqualTo("IN_APP");
            assertThat(message.title()).isEqualTo("Extra service approved and assigned");
            assertThat(message.body()).isEqualTo("Tan Ah Kow: Hospital escort on 24 Oct 10:00. Siti Rahman (primary caregiver) is on it. Review in Extra services.");
            assertThat(message.resourceType()).isEqualTo("VISIT");
            assertThat(message.resourceId()).isEqualTo(14L);
            assertThat(message.elderId()).isEqualTo(1L);
        });
    }

    @Test
    void unassignedVisitSendsActionRequiredNotification() {
        alert.approved(14L, 1L, "Grocery assistance", START, null);

        assertThat(sent()).singleElement().satisfies(message -> {
            assertThat(message.title()).isEqualTo("Extra service needs caregiver assignment");
            assertThat(message.body()).isEqualTo("Tan Ah Kow: Grocery assistance on 24 Oct 10:00. The primary caregiver is not free; assign a caregiver in Extra services.");
            assertThat(message.resourceId()).isEqualTo(14L);
        });
    }

    @Test
    void notifiesEveryEnabledManagerCoreNames() {
        when(core.enabledAccountsWithRole("MANAGER")).thenReturn(new CoreApi.AccountIds(List.of(4L, 8L, 12L)));

        alert.approved(14L, 1L, "Hospital escort", START, 7L);

        assertThat(sent()).extracting(NotificationRequested::recipientUserId).containsExactly(4L, 8L, 12L);
    }

    @Test
    void noManagersSendsNothing() {
        when(core.enabledAccountsWithRole("MANAGER")).thenReturn(new CoreApi.AccountIds(List.of()));

        alert.approved(14L, 1L, "Hospital escort", START, null);

        assertThat(sent()).isEmpty();
    }

    @Test
    void namesFallBackToIdsWhenCoreHasNobody() {
        when(core.elderReportProfile(1L)).thenThrow(new CoreNotFound("Elder [1] does not exist"));
        when(core.findCaregiverPublicProfile(7L)).thenReturn(Optional.empty());

        alert.approved(14L, 1L, "Companionship", START, 7L);

        assertThat(sent()).singleElement().extracting(NotificationRequested::body)
                .isEqualTo("Elder #1: Companionship on 24 Oct 10:00. Caregiver #7 (primary caregiver) is on it. Review in Extra services.");
    }

    private List<NotificationRequested> sent() {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeast(0)).publish(eq(NotificationRequested.TYPE), payloads.capture());
        return payloads.getAllValues().stream().map(NotificationRequested.class::cast).toList();
    }

    static CoreApi.ElderReportProfile elder(String name) {
        return new CoreApi.ElderReportProfile(1L, name, null, null, null, null, null, null, null, null, null);
    }
}
