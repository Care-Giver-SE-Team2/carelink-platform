package sg.nus.carelink.report.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportSection;

/**
 * Puts a report's content together one part at a time, then hands back an immutable
 * {@link ReportContent}: the Builder half of DP5.
 *
 * <p>The content has five parts that arrive at different moments of {@link ReportAssembler}'s
 * skeleton - the completeness verdict first, then four sections in turn, then the disclaimer
 * and how the text was produced. A constructor taking all five would force the skeleton to
 * hold every intermediate result in locals until the end; a mutable {@code ReportContent}
 * would let an archived report be changed after the fact. The builder is the mutable stage
 * that exists only while the report is being assembled.
 *
 * <p>{@link #build()} refuses content that is not finished. A report that never stated
 * whether its data was complete would default to looking complete, which is the one outcome
 * the use case rules out ("不产生内容残缺却标记为完成的报告"); so the verdict has to be given,
 * not assumed.
 */
public final class ReportContentBuilder {

	private final List<ReportSection> sections = new ArrayList<>();
	private Boolean dataComplete;
	private List<String> missingItems = List.of();
	private String disclaimer;
	private ReportContent.GeneratedBy generatedBy;

	private ReportContentBuilder() {
	}

	public static ReportContentBuilder create() {
		return new ReportContentBuilder();
	}

	/**
	 * States whether the facts were complete, and names each gap.
	 *
	 * @param gaps     what is missing; empty when nothing is
	 * @param describe how one gap is written down for the reader
	 * @param <T>      whatever a gap is; the builder only needs its description
	 */
	public <T> ReportContentBuilder completeness(List<T> gaps, Function<? super T, String> describe) {
		Objects.requireNonNull(describe, "describe");
		this.missingItems = gaps.stream().<String>map(describe).toList();
		this.dataComplete = gaps.isEmpty();
		return this;
	}

	/** Adds the next section. Titles are unique within one report. */
	public ReportContentBuilder section(String title, String body) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("A report section needs a title");
		}
		if (sections.stream().anyMatch(section -> section.title().equals(title))) {
			throw new IllegalArgumentException("A report already has a section called " + title);
		}
		sections.add(new ReportSection(title, body == null ? "" : body));
		return this;
	}

	/** The fixed text some readers must see under the report, or null for none. */
	public ReportContentBuilder disclaimer(String text) {
		this.disclaimer = text;
		return this;
	}

	public ReportContentBuilder generatedBy(ReportContent.GeneratedBy by) {
		this.generatedBy = Objects.requireNonNull(by, "generatedBy");
		return this;
	}

	/**
	 * @return the finished content, which nothing can change
	 * @throws IllegalStateException when completeness was never stated, there are no sections,
	 *                               or it was never said how the text was produced
	 */
	public ReportContent build() {
		if (dataComplete == null) {
			throw new IllegalStateException("A report has to state whether its data was complete");
		}
		if (sections.isEmpty()) {
			throw new IllegalStateException("A report needs at least one section");
		}
		if (generatedBy == null) {
			throw new IllegalStateException("A report has to state how its text was produced");
		}
		return new ReportContent(sections, dataComplete, missingItems, disclaimer, generatedBy);
	}
}
