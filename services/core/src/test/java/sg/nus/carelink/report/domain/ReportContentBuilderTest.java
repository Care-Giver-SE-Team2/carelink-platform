package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.service.ReportContentBuilder;

/**
 * The Builder half of DP5: content is assembled a part at a time and comes out immutable,
 * and it cannot come out unfinished.
 */
class ReportContentBuilderTest {

	@Test
	void buildsTheContentInTheOrderItWasGiven() {
		ReportContent content = ReportContentBuilder.create()
				.completeness(List.of(13L), id -> "Visit " + id + " on 2026-09-18 not closed")
				.section("Service completion", "3 visits")
				.section("Vital signs", "Pulse 72 bpm")
				.disclaimer("fixed text")
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE)
				.build();

		assertThat(content.sections()).containsExactly(
				new ReportSection("Service completion", "3 visits"),
				new ReportSection("Vital signs", "Pulse 72 bpm"));
		assertThat(content.dataComplete()).isFalse();
		assertThat(content.missingItems()).containsExactly("Visit 13 on 2026-09-18 not closed");
		assertThat(content.disclaimer()).isEqualTo("fixed text");
		assertThat(content.generatedBy()).isEqualTo(ReportContent.GeneratedBy.TEMPLATE);
	}

	@Test
	void noGapsMeansComplete() {
		ReportContent content = finishedBuilder().build();

		assertThat(content.dataComplete()).isTrue();
		assertThat(content.missingItems()).isEmpty();
		assertThat(content.disclaimer()).isNull();
	}

	/** A report is archived as built; nothing that held on to a list may change it afterwards. */
	@Test
	void whatComesOutCannotBeChanged() {
		List<Long> gaps = new ArrayList<>(List.of(13L));
		ReportContentBuilder builder = ReportContentBuilder.create()
				.completeness(gaps, id -> "Visit " + id)
				.section("Service completion", "3 visits")
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE);
		ReportContent content = builder.build();

		gaps.add(14L);
		builder.section("Vital signs", "added after the report was built");

		assertThat(content.sections()).hasSize(1);
		assertThat(content.missingItems()).containsExactly("Visit 13");

		List<ReportSection> sections = content.sections();
		ReportSection extra = new ReportSection("x", "y");
		List<String> missingItems = content.missingItems();
		assertThatThrownBy(() -> sections.add(extra)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> missingItems.add("Visit 99")).isInstanceOf(UnsupportedOperationException.class);
	}

	/** The one default that must not exist: silence about completeness reading as "complete". */
	@Test
	void refusesToBuildWithoutAVerdictOnCompleteness() {
		ReportContentBuilder builder = ReportContentBuilder.create()
				.section("Service completion", "3 visits")
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE);

		assertThatThrownBy(builder::build)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("complete");
	}

	@Test
	void refusesToBuildWithoutASection() {
		ReportContentBuilder builder = ReportContentBuilder.create()
				.completeness(List.of(), String::valueOf)
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE);

		assertThatThrownBy(builder::build)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("section");
	}

	@Test
	void refusesToBuildWithoutSayingHowTheTextWasProduced() {
		ReportContentBuilder builder = ReportContentBuilder.create()
				.completeness(List.of(), String::valueOf)
				.section("Service completion", "3 visits");

		assertThatThrownBy(builder::build)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("produced");
	}

	@Test
	void aSectionNeedsATitleAndOnlyOneSectionMayHaveIt() {
		ReportContentBuilder builder = finishedBuilder();

		assertThatThrownBy(() -> builder.section(" ", "body")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.section(null, "body")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.section("Service completion", "again"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Service completion");
	}

	@Test
	void aSectionWithNoBodyIsEmptyRatherThanNull() {
		ReportContent content = finishedBuilder().section("Vital signs", null).build();

		assertThat(content.sections().get(1).body()).isEmpty();
	}

	private static ReportContentBuilder finishedBuilder() {
		return ReportContentBuilder.create()
				.completeness(List.of(), String::valueOf)
				.section("Service completion", "3 visits")
				.generatedBy(ReportContent.GeneratedBy.TEMPLATE);
	}
}
