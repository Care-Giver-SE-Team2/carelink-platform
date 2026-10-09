package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReportSeries;

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
 * unreadable - and fields a section gained later (its key, figures and series) read back from
 * an older report as absent: the key made from the title, no figures, no series.
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
					content.sections().stream().map(StoredSection::of).toList(),
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
	record StoredSection(String title, String body, String key, List<StoredFigure> figures, List<StoredSeries> series) {

		static StoredSection of(ReportSection section) {
			return new StoredSection(
					section.title(),
					section.body(),
					section.key(),
					section.figures().stream().map(StoredFigure::of).toList(),
					section.series().stream().map(StoredSeries::of).toList());
		}

		/**
		 * A section stored without a body reads back as an empty one, as the builder would have
		 * made it; one stored without a key is keyed by its title, as the builder keys them.
		 */
		ReportSection toDomain() {
			return new ReportSection(
					key == null || key.isBlank() ? ReportSection.keyOf(title) : key,
					title,
					body == null ? "" : body,
					figures == null ? List.of() : figures.stream().map(StoredFigure::toDomain).toList(),
					series == null ? List.of() : series.stream().map(StoredSeries::toDomain).toList());
		}
	}

	/** The stored shape of one figure. */
	record StoredFigure(String key, String label, BigDecimal value, BigDecimal outOf, String unit) {

		static StoredFigure of(ReportFigure figure) {
			return new StoredFigure(figure.key(), figure.label(), figure.value(), figure.outOf(), figure.unit());
		}

		ReportFigure toDomain() {
			return new ReportFigure(key, label, value, outOf, unit);
		}
	}

	/** The stored shape of one series. */
	record StoredSeries(String key, String label, String unit, List<StoredPoint> points) {

		static StoredSeries of(ReportSeries series) {
			return new StoredSeries(series.key(), series.label(), series.unit(),
					series.points().stream().map(StoredPoint::of).toList());
		}

		ReportSeries toDomain() {
			return new ReportSeries(key, label, unit,
					points == null ? List.of() : points.stream().map(StoredPoint::toDomain).toList());
		}
	}

	/** The stored shape of one point. */
	record StoredPoint(String at, BigDecimal low, BigDecimal high, boolean flagged) {

		static StoredPoint of(ReportSeries.Point point) {
			return new StoredPoint(point.at(), point.low(), point.high(), point.flagged());
		}

		ReportSeries.Point toDomain() {
			return new ReportSeries.Point(at, low, high, flagged);
		}
	}
}
