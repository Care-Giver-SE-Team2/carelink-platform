package sg.nus.carelink.report.infrastructure;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.ValueAddedNotifier.Why;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

class NotificationTableValueAddedNotifierTest {

    private static final LocalDateTime SCHEDULE = LocalDateTime.of(2026, 10, 10, 10, 0);

    private JdbcClient jdbc;
    private JdbcClient.StatementSpec statement;
    private JdbcClient.MappedQuerySpec<String> names;
    private JdbcClient.MappedQuerySpec<Long> ids;
    private CoreApi core;
    private NotificationTableValueAddedNotifier notifier;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        names = mock(JdbcClient.MappedQuerySpec.class);
        ids = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.query(String.class)).thenReturn(names);
        when(statement.query(Long.class)).thenReturn(ids);
        when(names.optional()).thenReturn(Optional.of("Tan Ah Kow"));
        core = mock(CoreApi.class);
        when(core.familyMemberIds(10L)).thenReturn(List.of(20L, 21L));
        when(core.alertRecipient(10L, 20L)).thenReturn(new CoreApi.AlertRecipient(20L, 3L, null));
        when(core.alertRecipient(10L, 21L)).thenReturn(new CoreApi.AlertRecipient(21L, 4L, "BINDING_EXPIRED"));
        notifier = new NotificationTableValueAddedNotifier(jdbc, core);
    }

    @Test
    void aNewRequestGoesToEachFamilyMemberStillBoundAndLinksToTheRequest() {
        notifier.requested(request(), "Hospital escort");

        verify(statement).param("recipient", 3L);
        verify(statement, never()).param("recipient", 4L);
        verify(statement).param("type", "VALUE_ADDED_REQUEST");
        verify(statement).param("resource", 5L);
        verify(statement).param("title", "Tan Ah Kow asked for Hospital escort");
        verify(statement).param("body", "Hospital escort on Sat 10 Oct 10:00. Approve or decline it in Extra services.");
        verify(statement).update();
    }

    @Test
    void anAssignedCaregiverIsToldThroughTheVisit() {
        when(ids.optional()).thenReturn(Optional.of(90L));

        notifier.caregiverAssigned(request(), "Hospital escort", 42L);

        verify(statement).param("recipient", 90L);
        verify(statement).param("type", "VISIT");
        verify(statement).param("resource", 77L);
        verify(statement, times(2)).update();
    }

    @Test
    void aVisitThatFailedIsNotProvidedAndTheCaregiverIsNotToldItWasCancelled() {
        notifier.cancelled(request(), "Hospital escort", 42L, Why.NOT_PROVIDED);

        verify(statement).param("title", "Hospital escort for Tan Ah Kow could not be provided");
        verify(statement, times(1)).update();
    }

    @Test
    void aReminderIsSentOnlyOnce() {
        when(ids.single()).thenReturn(1L);

        notifier.reminder(request(), "Hospital escort");

        verify(jdbc).sql(contains("VALUE_ADDED_REMINDER"));
        verify(statement, never()).update();
    }

    private static ValueAddedServiceRequest request() {
        return new ValueAddedServiceRequest(5L, 10L, 2L, null, 20L, 77L, SCHEDULE, null,
                ValueAddedServiceRequest.Status.DISPATCHED, null, null, null);
    }
}
