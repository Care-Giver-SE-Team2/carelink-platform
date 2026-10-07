package sg.nus.carelink.report.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What one reader's report says, already filtered for that reader.
 *
 * <p>Built step by step by {@code ReportContentBuilder} and immutable once built: the lists
 * are copied on the way in, so nothing that held a reference while the content was being
 * assembled can change an archived report afterwards.
 *
 * <p>This is what the report table's {@code content} column holds. Whether the data was
 * complete, what was missing and how the text was produced travel inside it rather than in
 * columns of their own, because the table was fixed in V2 and a report is stored once and
 * never rewritten. Turning it into JSON is the persistence adapter's business; nothing here
 * knows a serialisation format exists.
 *
 * @param sections     in the order the reader sees them; every audience gets the same titles
 * @param dataComplete false when the period still held visits that were not closed (2a)
 * @param missingItems one line per such visit, so the gap is named rather than implied
 * @param disclaimer   the family's fixed medical disclaimer; null for the other readers
 */
public record ReportContent(
		List<ReportSection> sections,
		boolean dataComplete,
		List<String> missingItems,
		String disclaimer,
		ReportContent.GeneratedBy generatedBy) {

	private static final Set<String> FAMILY_SECTION_TITLES = Set.of(
			"Service completion", "Vital signs", "Observations", "Incidents");

	public ReportContent {
		sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
		missingItems = List.copyOf(Objects.requireNonNull(missingItems, "missingItems"));
		Objects.requireNonNull(generatedBy, "generatedBy");
	}

	/**
	 * The published family chapter types, retaining stored order and text. Called only after
	 * report authorization; MG07 owns the content it publishes for the FAMILY audience.
	 *
	 * @author Wang Zhili
	 */
	public List<ReportSection> familySections() {
		return sections.stream().filter(section -> FAMILY_SECTION_TITLES.contains(section.title())).toList();
	}

	/**
	 * How the text was produced. MODEL is the language-model summary of UC-MG07 alternative
	 * 3a's happy path; TEMPLATE is the structured text assembled from the facts. Nothing
	 * generates MODEL yet, so every report today says TEMPLATE, and says so honestly.
	 */
	public enum GeneratedBy {
		MODEL, TEMPLATE
	}
}
