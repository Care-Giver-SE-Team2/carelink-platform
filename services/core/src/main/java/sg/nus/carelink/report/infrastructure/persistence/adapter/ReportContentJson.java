package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportSection;

/**
 * ReportContent to and from the JSON in report.content.
 *
 * <p>The JSON is a stored document, not a message: a report is archived once and read back
 * for as long as it is kept. So its shape is written down here, in two records of its own,
 * rather than being whatever Jackson makes of the domain types. Renaming a field of
 * {@link ReportContent} then changes nothing that is already on disk; the mapping in
 * {@link Stored} is where that change would have to be faced.
 *
 * <p>Its own mapper, not the web layer's. How the API renders JSON may be tuned for the
 * browser at any time; the archive's format must not move with it. Unknown fields are
 * ignored on the way back in, so a later version can add one without making older reports
 * unreadable.
 */
final class ReportContentJson {

	private static final JsonMapper JSON = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private ReportContentJson() {
	}

	static String write(ReportContent content) {
		return JSON.writeValueAsString(Stored.of(content));
	}

	/**
	 * @throws IllegalStateException when the column is empty: every report this module files
	 *                               has content, so an empty one is damage, not a state
	 */
	static ReportContent read(String json) {
		if (json == null || json.isBlank()) {
			throw new IllegalStateException("A report was stored without its content");
		}
		return JSON.readValue(json, Stored.class).toDomain();
	}

	/** The stored shape of a report's content. */
	record Stored(
			List<StoredSection> sections,
			boolean dataComplete,
			List<String> missingItems,
			String disclaimer,
			String generatedBy) {

		static Stored of(ReportContent content) {
			return new Stored(
					content.sections().stream().map(section -> new StoredSection(section.title(), section.body())).toList(),
					content.dataComplete(),
					content.missingItems(),
					content.disclaimer(),
					content.generatedBy().name());
		}

		ReportContent toDomain() {
			return new ReportContent(
					sections == null ? List.of() : sections.stream().map(StoredSection::toDomain).toList(),
					dataComplete,
					missingItems == null ? List.of() : missingItems,
					disclaimer,
					ReportContent.GeneratedBy.valueOf(generatedBy));
		}
	}

	/** The stored shape of one section. */
	record StoredSection(String title, String body) {

		/** A section stored without a body reads back as an empty one, as the builder would have made it. */
		ReportSection toDomain() {
			return new ReportSection(title, body == null ? "" : body);
		}
	}
}
