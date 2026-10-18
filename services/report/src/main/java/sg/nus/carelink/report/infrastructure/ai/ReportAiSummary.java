package sg.nus.carelink.report.infrastructure.ai;

import java.util.Optional;

/** Receives only an allowlisted, aggregate-only string. */
@FunctionalInterface
public interface ReportAiSummary {
    Optional<String> summarize(String aggregateMetrics);
}
