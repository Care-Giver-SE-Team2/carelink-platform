package sg.nus.carelink.report.infrastructure;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class NotificationTableValueAddedManagerAlertTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 10, 0);
    private JdbcClient jdbc;
    private JdbcClient.StatementSpec statement;
    private JdbcClient.MappedQuerySpec<Long> managersQuery;
    private JdbcClient.MappedQuerySpec<String> nameQuery;
    private NotificationTableValueAddedManagerAlert alert;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        managersQuery = mock(JdbcClient.MappedQuerySpec.class);
        nameQuery = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.query(Long.class)).thenReturn(managersQuery);
        when(statement.query(String.class)).thenReturn(nameQuery);
        when(nameQuery.optional()).thenReturn(Optional.of("Tan Ah Kow"), Optional.of("Siti Rahman"));
        alert = new NotificationTableValueAddedManagerAlert(jdbc);
    }

    @Test
    void assignedCaregiverSendsSuccessNotificationToManager() {
        when(managersQuery.list()).thenReturn(List.of(4L));
        alert.approved(14L, 1L, "Hospital escort", START, 7L);
        verify(statement).param("recipient", 4L);
        verify(statement).param("title", "Extra service approved and assigned");
        verify(statement).param("body", "Tan Ah Kow: Hospital escort on 24 Oct 10:00. Siti Rahman (primary caregiver) is on it. Review in Extra services.");
        verify(statement).param("visit", 14L);
        verify(statement).update();
    }

    @Test
    void unassignedVisitSendsActionRequiredNotification() {
        when(managersQuery.list()).thenReturn(List.of(4L));
        alert.approved(14L, 1L, "Grocery assistance", START, null);
        verify(statement).param("title", "Extra service needs caregiver assignment");
        verify(statement).param("body", "Tan Ah Kow: Grocery assistance on 24 Oct 10:00. The primary caregiver is not free; assign a caregiver in Extra services.");
        verify(statement).param("visit", 14L);
        verify(statement).update();
    }

    @Test
    void notifiesEveryEnabledManagerReturnedByQuery() {
        when(managersQuery.list()).thenReturn(List.of(4L, 8L, 12L));
        alert.approved(14L, 1L, "Hospital escort", START, 7L);
        verify(statement).param("recipient", 4L);
        verify(statement).param("recipient", 8L);
        verify(statement).param("recipient", 12L);
        verify(statement, times(3)).update();
    }

    @Test
    void noManagersDoesNotInsertNotifications() {
        when(managersQuery.list()).thenReturn(List.of());
        alert.approved(14L, 1L, "Hospital escort", START, null);
        verify(statement, never()).update();
    }

    @Test
    void namesFallBackToIdsWhenTheRowsAreGone() {
        when(nameQuery.optional()).thenReturn(Optional.empty());
        when(managersQuery.list()).thenReturn(List.of(4L));
        alert.approved(14L, 1L, "Companionship", START, 7L);
        verify(statement).param("body", "Elder #1: Companionship on 24 Oct 10:00. Caregiver #7 (primary caregiver) is on it. Review in Extra services.");
    }
}
