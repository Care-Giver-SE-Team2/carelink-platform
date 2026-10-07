package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.support.ReportFixtures;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The report aggregate: filed once, then only ever added to.
 *
 * <p>UC-MG07 5a and its business rule - no deleting, no editing in place, corrections
 * appended - are properties of this type's shape as much as of its methods, so the shape is
 * tested too.
 */
class ReportTest {

	private static final LocalDateTime LATER = LocalDateTime.of(2026, 9, 22, 8, 30);

	@Test
	void aGeneratedReportIsPublishedStraightAway() {
		Report report = Report.generate(
				ReportFixtures.ELDER, Report.Audience.FAMILY, ReportFixtures.WEEK, ReportFixtures.content(),
				7L, ReportFixtures.GENERATED_AT);

		assertThat(report.id()).isNull();
		assertThat(report.status()).isEqualTo(Report.Status.PUBLISHED);
		assertThat(report.amendments()).isEmpty();
		assertThat(report.createdAt()).isEqualTo(ReportFixtures.GENERATED_AT);
		assertThat(report.generatedByUserId()).isEqualTo(7L);
		assertThat(report.period()).isEqualTo(ReportFixtures.WEEK);
		assertThat(report.content()).isEqualTo(ReportFixtures.content());
	}

	@Test
	void theWeeklyRunGeneratesWithNobodyNamed() {
		Report report = Report.generate(
				ReportFixtures.ELDER, Report.Audience.INTERNAL, ReportFixtures.WEEK, ReportFixtures.content(),
				null, ReportFixtures.GENERATED_AT);

		assertThat(report.generatedByUserId()).isNull();
	}

	@Test
	void anAmendmentIsAppendedAndNothingElseChanges() {
		Report original = ReportFixtures.stored(40L, Report.Audience.FAMILY);

		Report amended = original.amend("  Visit 13 was cancelled by the family on 17 Sep.  ", 9L, LATER);

		assertThat(amended.amendments()).containsExactly(
				new ReportAmendment(null, 40L, "Visit 13 was cancelled by the family on 17 Sep.", 9L, LATER));
		assertThat(amended)
				.usingRecursiveComparison()
				.ignoringFields("amendments")
				.isEqualTo(original);
		assertThat(original.amendments()).as("the report amended is left as it was").isEmpty();
	}

	@Test
	void correctionsAccumulateInTheOrderTheyWereMade() {
		Report report = ReportFixtures.stored(40L, Report.Audience.INTERNAL)
				.amend("first", 9L, LATER)
				.amend("second", 10L, LATER.plusHours(1));

		assertThat(report.amendments()).extracting(ReportAmendment::note).containsExactly("first", "second");
		assertThat(report.amendments()).extracting(ReportAmendment::authorUserId).containsExactly(9L, 10L);
	}

	@Test
	void aCorrectionHasToSaySomething() {
		Report report = ReportFixtures.stored(40L, Report.Audience.FAMILY);

		assertThatThrownBy(() -> report.amend("   ", 9L, LATER))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(error -> ((BusinessRuleViolation) error).code())
				.isEqualTo("REPORT_AMENDMENT_NOTE_REQUIRED");
		assertThatThrownBy(() -> report.amend(null, 9L, LATER)).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aCorrectionFitsTheColumnItIsStoredIn() {
		Report report = ReportFixtures.stored(40L, Report.Audience.FAMILY);

		assertThat(report.amend("x".repeat(1000), 9L, LATER).amendments()).hasSize(1);

		String tooLong = "x".repeat(1001);
		assertThatThrownBy(() -> report.amend(tooLong, 9L, LATER))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(error -> ((BusinessRuleViolation) error).code())
				.isEqualTo("REPORT_AMENDMENT_TOO_LONG");
	}

	@Test
	void aCorrectionIsSigned() {
		Report report = ReportFixtures.stored(40L, Report.Audience.FAMILY);

		assertThatThrownBy(() -> report.amend("unsigned", null, LATER)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void aReportThatWasNeverStoredCannotBeAmended() {
		Report unsaved = Report.generate(
				ReportFixtures.ELDER, Report.Audience.FAMILY, ReportFixtures.WEEK, ReportFixtures.content(),
				7L, ReportFixtures.GENERATED_AT);

		assertThatThrownBy(() -> unsaved.amend("too early", 9L, LATER)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void theCorrectionsCannotBeEditedThroughTheList() {
		List<ReportAmendment> amendments =
				ReportFixtures.stored(40L, Report.Audience.FAMILY).amend("note", 9L, LATER).amendments();

		assertThatThrownBy(amendments::clear).isInstanceOf(UnsupportedOperationException.class);
	}

	/**
	 * A7: there is no way to edit or delete a report, only to append to one. As a record it has
	 * no setters; this checks that no method was added since that hands back a changed report
	 * other than {@code amend} - a {@code withContent}, an {@code archive}, a {@code redact}.
	 */
	@Test
	void amendIsTheOnlyWayAReportChanges() {
		assertThat(Arrays.stream(Report.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.filter(method -> !Modifier.isStatic(method.getModifiers()))
				.filter(method -> method.getReturnType().equals(Report.class))
				.map(Method::getName))
				.containsExactly("amend");
		assertThat(Arrays.stream(Report.class.getDeclaredMethods()).map(Method::getName))
				.noneMatch(name -> name.startsWith("set") || name.startsWith("delete") || name.startsWith("remove"));
	}

	@Test
	void aReportNeedsItsElderReaderPeriodAndContent() {
		ReportContent content = ReportFixtures.content();

		assertThatThrownBy(() -> new Report(1L, null, 7L, Report.Audience.FAMILY, ReportFixtures.WEEK,
				Report.Status.PUBLISHED, content, null, LATER))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Report(1L, 1L, 7L, Report.Audience.FAMILY, ReportFixtures.WEEK,
				Report.Status.PUBLISHED, null, null, LATER))
				.isInstanceOf(NullPointerException.class);
		assertThat(new Report(1L, 1L, 7L, Report.Audience.FAMILY, ReportFixtures.WEEK,
				Report.Status.PUBLISHED, content, null, LATER).amendments()).isEmpty();
	}
}
