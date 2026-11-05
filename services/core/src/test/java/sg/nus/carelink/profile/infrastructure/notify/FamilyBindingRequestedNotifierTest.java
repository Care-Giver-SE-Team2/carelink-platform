package sg.nus.carelink.profile.infrastructure.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.profile.application.FamilyBindingRequested;

class FamilyBindingRequestedNotifierTest {

    private JdbcClient.StatementSpec statement;
    private JdbcClient.MappedQuerySpec<Long> users;
    private Events events;
    private FamilyBindingRequestedNotifier notifier;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        JdbcClient jdbc = mock(JdbcClient.class);
        statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<String> names = mock(JdbcClient.MappedQuerySpec.class);
        users = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(names);
        when(statement.query(Long.class)).thenReturn(users);
        when(names.optional()).thenReturn(Optional.of("Tan Ah Kow"));
        when(users.optional()).thenReturn(Optional.of(20L));
        events = mock(Events.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneId.of("Asia/Singapore"));
        notifier = new FamilyBindingRequestedNotifier(jdbc, events, mock(PlatformTransactionManager.class), clock);
    }

    @Test
    void theFamilyMemberAskedIsToldAndTheMessageLinksToTheBinding() {
        notifier.onFamilyBindingRequested(new FamilyBindingRequested(10L, 1L, 3L));

        verify(events).publish(NotificationRequested.TYPE, NotificationRequested.inApp(20L, "FAMILY_BINDING_REQUESTED",
                "Tan Ah Kow wants to link with you",
                "Confirm or reject the request in Family bindings to start seeing their care.", "FAMILY_BINDING", 10L,
                1L, LocalDateTime.of(2026, 10, 15, 10, 0)));
    }

    @Test
    void aFamilyMemberWithNoAccountIsNotWrittenTo() {
        when(users.optional()).thenReturn(Optional.empty());

        notifier.onFamilyBindingRequested(new FamilyBindingRequested(10L, 1L, 3L));

        verifyNoInteractions(events);
    }

    @Test
    void aFailedPublishDoesNotEscapeTheListener() {
        doThrow(new IllegalStateException("outbox down")).when(events).publish(anyString(), any());

        notifier.onFamilyBindingRequested(new FamilyBindingRequested(10L, 1L, 3L));
    }
}
