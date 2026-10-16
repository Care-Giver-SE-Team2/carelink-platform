package sg.nus.carelink.report.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.infrastructure.ai.ReportAiSummary;

/** Optional narrative enrichment. Structured figures, series and source sections remain unchanged. */
public final class ReportAiEnricher {
    private final ReportAiSummary model;

    public ReportAiEnricher(ReportAiSummary model) {
        this.model = model;
    }

    public ReportContent enrich(ReportContent original) {
        if (original.sections().isEmpty()) return original;
        String metrics = safeMetrics(original);
        if (metrics.isBlank()) return original;
        try {
            Optional<String> result = model.summarize(metrics);
            if (result.isEmpty() || result.get().isBlank() || result.get().length() > 1200) return original;
            // Keep the source overview verbatim; the generated text is a clearly labelled addendum.
            List<ReportSection> sections = new ArrayList<>(original.sections());
            ReportSection first = sections.getFirst();
            sections.set(0, new ReportSection(first.key(), first.title(),
                    first.body() + "\nAI-generated service summary (verify against figures): " + result.get().strip(),
                    first.figures(), first.series()));
            return new ReportContent(sections, original.dataComplete(), original.missingItems(),
                    original.disclaimer(), ReportContent.GeneratedBy.MODEL);
        } catch (RuntimeException ignored) {
            return original; // AI must never prevent filing the template report.
        }
    }

    /** Only known numeric aggregates and fixed labels; never send body, names, notes or identifiers. */
    static String safeMetrics(ReportContent content) {
        StringBuilder out = new StringBuilder("Aggregate care-service metrics. Do not infer diagnoses.\n");
        int count = 0;
        for (ReportSection section : content.sections()) {
            if (!List.of("overview", "services", "service-completion", "ratings-and-spot-checks").contains(section.key())) continue;
            for (ReportFigure figure : section.figures()) {
                if (!figure.key().matches("[a-z0-9-]{1,40}")) continue;
                if (count++ >= 16) return out.toString();
                out.append(figure.key()).append(": ").append(figure.value().toPlainString());
                if (figure.outOf() != null) out.append(" / ").append(figure.outOf().toPlainString());
                if ("%".equals(figure.unit())) out.append(" percent");
                out.append('\n');
            }
        }
        return count == 0 ? "" : out.toString();
    }
}
