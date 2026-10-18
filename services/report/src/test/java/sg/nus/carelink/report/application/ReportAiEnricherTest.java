package sg.nus.carelink.report.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.report.domain.model.*;

class ReportAiEnricherTest {
    private static ReportContent template() {
        return new ReportContent(List.of(new ReportSection("overview", "Overview",
            "Grace Wee: confidential medical note", List.of(ReportFigure.count("visits", "Visits", 2)), List.of())),
            true, List.of(), null, ReportContent.GeneratedBy.TEMPLATE);
    }

    @Test void sendsOnlyAllowlistedMetricsAndPreservesFigures() {
        AtomicReference<String> sent = new AtomicReference<>();
        ReportContent original = template();
        ReportContent result = new ReportAiEnricher(metrics -> { sent.set(metrics); return Optional.of("Two visits recorded."); }).enrich(original);
        assertEquals(ReportContent.GeneratedBy.MODEL, result.generatedBy());
        assertTrue(result.sections().getFirst().body().contains("AI-generated service summary"));
        assertEquals(original.sections().getFirst().figures(), result.sections().getFirst().figures());
        assertFalse(sent.get().contains("Grace"));
        assertFalse(sent.get().contains("medical note"));
        assertTrue(sent.get().contains("visits: 2"));
    }

    @Test void fallsBackOnUnavailableModel() {
        ReportContent original = template();
        assertSame(original, new ReportAiEnricher(m -> Optional.empty()).enrich(original));
        assertSame(original, new ReportAiEnricher(m -> { throw new IllegalStateException(); }).enrich(original));
    }

    @Test void rejectsOversizedSummary() {
        ReportContent original = template();
        assertSame(original, new ReportAiEnricher(m -> Optional.of("x".repeat(1201))).enrich(original));
    }
}
