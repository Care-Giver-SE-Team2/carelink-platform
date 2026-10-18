package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;

class ReportTaskProducerTest {
    private final ReportFactsSource facts = mock(ReportFactsSource.class);
    private final Events events = mock(Events.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T16:15:00Z"), ZoneOffset.UTC);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportTaskProducer producer = new ReportTaskProducer(facts, events, clock, jdbc);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void publishesOneV1TaskPerDistinctElderForLastCompletedWeek() {
        ReportPeriod period = new ReportPeriod(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18"));
        when(facts.eldersWithVisitsIn(period)).thenReturn(List.of(101L, 202L, 101L));
        when(jdbc.update(any(String.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(1);
        assertThat(producer.publishLastCompletedWeek()).isEqualTo(2);
        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(events, times(2)).publish(eq("ReportRequested"), payload.capture());
        assertThat(payload.getAllValues()).extracting(p -> p.get("elderId")).containsExactly(101L, 202L);
        for (Map p : payload.getAllValues()) {
            assertThat(p).containsEntry("schemaVersion", 1)
                .containsEntry("periodStart", "2026-10-12")
                .containsEntry("periodEnd", "2026-10-18")
                .containsEntry("runId", "weekly-20261012");
        }
    }

    @Test
    void skipsAlreadyClaimedBusinessTask() {
        ReportPeriod period = new ReportPeriod(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18"));
        when(facts.eldersWithVisitsIn(period)).thenReturn(List.of(101L));
        when(jdbc.update(any(String.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(0);
        assertThat(producer.publishPeriod(period)).isZero();
        verifyNoInteractions(events);
    }

    @Test
    void refusesUnfinishedOrNonWeeklyPeriods() {
        assertThatThrownBy(() -> producer.publishPeriod(new ReportPeriod(
            LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> producer.publishPeriod(new ReportPeriod(
            LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-18"))))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(events);
    }
}
