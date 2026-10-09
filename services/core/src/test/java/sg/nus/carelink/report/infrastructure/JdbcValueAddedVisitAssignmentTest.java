package sg.nus.carelink.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import sg.nus.carelink.profile.application.PrimaryCaregiverLookup;

class JdbcValueAddedVisitAssignmentTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 10, 0);
    private PrimaryCaregiverLookup primary;
    private JdbcClient jdbc;
    private JdbcClient.StatementSpec statement;
    private JdbcClient.MappedQuerySpec<Long> countQuery;
    private JdbcValueAddedVisitAssignment assignment;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        primary = mock(PrimaryCaregiverLookup.class);
        jdbc = mock(JdbcClient.class);
        statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        countQuery = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.query(Long.class)).thenReturn(countQuery);
        assignment = new JdbcValueAddedVisitAssignment(primary, jdbc);
    }

    @Test
    void noPrimaryCaregiverReturnsEmptyWithoutQueryingDatabase() {
        when(primary.findRosterableCaregiverId(1L)).thenReturn(Optional.empty());
        assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void approvedLeaveExcludesPrimaryCaregiverAndSkipsOverlapQuery() {
        when(primary.findRosterableCaregiverId(1L)).thenReturn(Optional.of(7L));
        when(countQuery.single()).thenReturn(1L);
        assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).isEmpty();
        verify(jdbc, times(1)).sql(anyString());
        verify(statement).param("day", START.toLocalDate());
    }

    @Test
    void overlappingVisitAnywhereInTheServicesLengthExcludesPrimaryCaregiver() {
        when(primary.findRosterableCaregiverId(1L)).thenReturn(Optional.of(7L));
        when(countQuery.single()).thenReturn(0L, 1L);
        assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).isEmpty();
        verify(jdbc, times(2)).sql(anyString());
        verify(statement).param("start", START);
        verify(statement).param("end", START.plusHours(3));
    }

    @Test
    void availablePrimaryCaregiverIsChosen() {
        when(primary.findRosterableCaregiverId(1L)).thenReturn(Optional.of(7L));
        when(countQuery.single()).thenReturn(0L, 0L);
        assertThat(assignment.chooseCaregiver(1L, START, START.plusHours(3))).contains(7L);
        verify(jdbc, times(2)).sql(anyString());
        verify(statement, times(2)).param("caregiverId", 7L);
    }
}
