package sg.nus.carelink.report.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.report.application.ReportService;

class ReportRequestedHandlerTest {
    @Test
    void generatesPreviousWeekFromScheduledTime() {
        ReportService reports = mock(ReportService.class);
        var handler = new ReportRequestedHandler().reportRequestedEventHandler(reports);
        // 2026-10-19 02:00 Singapore time; worker may process it days later.
        var metadata = new EventMetadata("scheduled-2026-10-19", "ReportRequested", "eventbridge",
                Instant.parse("2026-10-18T18:00:00Z"), null);
        handler.handle(Map.of(), metadata);
        verify(reports).generate(null, LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18"), null);
    }
}
