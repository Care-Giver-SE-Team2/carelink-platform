package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.support.ReportFixtures;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * UC-MG07 through the application layer, against a repository in memory and facts written by
 * hand: no database, no Spring. What is tested here is the orchestration - one reading of the
 * facts for three readers, filing, idempotency, corrections - not what the reports say, which
 * is {@code ReportAssemblerTest}'s.
 */
class ReportServiceTest {

	/** Sunday 20 September 2026, 23:00 in Singapore: when the weekly run would fire. */
	private static final Clock SUNDAY_NIGHT =
			Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("Asia/Singapore"));
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 23, 0);

	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);
	private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 20);

	private final InMemoryReportRepository repository = new InMemoryReportRepository();
	private final FakeReportFactsSource facts = new FakeReportFactsSource().with(ReportFixtures.week());
	private final ReportService service = new ReportService(repository, facts, SUNDAY_NIGHT);

	// ----------------------------------------------------------------- generating ---

	@Test
	void filesOneReportPerReaderFromASingleReadingOfTheFacts() {
		List<Report> filed = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);

		assertThat(filed).extracting(Report::audience).containsExactly(
				Report.Audience.FAMILY, Report.Audience.REGULATOR, Report.Audience.INTERNAL);
		assertThat(filed).allSatisfy(report -> {
			assertThat(report.id()).isNotNull();
			assertThat(report.status()).isEqualTo(Report.Status.PUBLISHED);
			assertThat(report.generatedByUserId()).isEqualTo(7L);
			assertThat(report.createdAt()).isEqualTo(NOW);
			assertThat(report.period()).isEqualTo(ReportFixtures.WEEK);
			assertThat(report.content().dataComplete()).isFalse();
			assertThat(report.content().missingItems()).containsExactly("Visit 13 on 2026-09-18 not closed");
			assertThat(report.content().generatedBy()).isEqualTo(ReportContent.GeneratedBy.TEMPLATE);
		});
		assertThat(facts.gatheredFor()).as("the tables are read once for all three readers").containsExactly(1L);
	}

	@Test
	void theThreeReadersGetDifferentVersionsOfTheSameFacts() {
		List<Report> filed = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);

		assertThat(filed).extracting(Report::content).doesNotHaveDuplicates();
		assertThat(filed.get(0).content().disclaimer()).isNotBlank();
		assertThat(filed.get(1).content().disclaimer()).isNull();
		assertThat(filed.get(2).content().disclaimer()).isNull();
	}

	@Test
	void aCompleteWeekIsFiledAsComplete() {
		FakeReportFactsSource complete = new FakeReportFactsSource().with(ReportFixtures.closedWeek());
		ReportService withCompleteWeek = new ReportService(repository, complete, SUNDAY_NIGHT);

		assertThat(withCompleteWeek.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L))
				.isNotEmpty()
				.allSatisfy(report -> {
					assertThat(report.content().dataComplete()).isTrue();
					assertThat(report.content().missingItems()).isEmpty();
				});
	}

	/** The weekly run and a manager's Generate for the same week are the same request. */
	@Test
	void generatingTheSamePeriodAgainHandsBackTheReportsAlreadyFiled() {
		List<Report> first = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);
		List<Report> again = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 8L);

		assertThat(again).extracting(Report::id).containsExactlyElementsOf(first.stream().map(Report::id).toList());
		assertThat(again).extracting(Report::generatedByUserId).containsOnly(7L);
		assertThat(repository.reportCount()).isEqualTo(3);
		assertThat(facts.gatheredFor()).as("nothing was missing, so nothing was read").containsExactly(1L);
	}

	@Test
	void onlyTheMissingReaderIsFiledWhenOneVersionIsAlreadyOnFile() {
		Report familyOnFile = repository.save(Report.generate(ReportFixtures.ELDER, Report.Audience.FAMILY,
				ReportFixtures.WEEK, ReportFixtures.content(), 5L, NOW.minusDays(1)));

		List<Report> filed = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);

		assertThat(filed).hasSize(3);
		assertThat(filed.getFirst().id()).isEqualTo(familyOnFile.id());
		assertThat(filed.getFirst().generatedByUserId()).isEqualTo(5L);
		assertThat(filed.subList(1, 3)).extracting(Report::generatedByUserId).containsOnly(7L);
		assertThat(repository.reportCount()).isEqualTo(3);
	}

	@Test
	void withoutAnElderEveryElderWhoHadAVisitGetsTheirReports() {
		ReportFacts week = ReportFixtures.week();
		facts.with(new ReportFacts(2L, week.period(), week.visits(), List.of(), List.of(), List.of()));

		List<Report> filed = service.generate(null, MONDAY, SUNDAY, 7L);

		assertThat(filed).extracting(Report::elderId).containsExactly(1L, 1L, 1L, 2L, 2L, 2L);
	}

	@Test
	void aQuietWeekIsStillAReportWorthFiling() {
		FakeReportFactsSource quiet = new FakeReportFactsSource().with(ReportFixtures.quietWeek());

		List<Report> filed = new ReportService(repository, quiet, SUNDAY_NIGHT)
				.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);

		assertThat(filed).hasSize(3).allSatisfy(report -> assertThat(report.content().dataComplete()).isTrue());
	}

	@Test
	void anElderWhoDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> service.generate(99L, MONDAY, SUNDAY, 7L))
				.isInstanceOf(ResourceNotFound.class)
				.hasMessageContaining("99");
		assertThat(repository.reportCount()).isZero();
	}

	@Test
	void aPeriodThatEndsBeforeItStartsIsRefused() {
		assertThatThrownBy(() -> service.generate(ReportFixtures.ELDER, SUNDAY, MONDAY, 7L))
				.isInstanceOf(BusinessRuleViolation.class);
	}

	/** Fired late on Sunday, the run reports on the week ending that day, and nobody is named as asking. */
	@Test
	void theWeeklyRunReportsOnTheWeekEndingOnTheDayItRuns() {
		List<Report> filed = service.generateForLastWeek();

		assertThat(facts.lastPeriodAsked()).isEqualTo(new ReportPeriod(MONDAY, SUNDAY));
		assertThat(filed).hasSize(3).allSatisfy(report -> {
			assertThat(report.period()).isEqualTo(new ReportPeriod(MONDAY, SUNDAY));
			assertThat(report.generatedByUserId()).isNull();
		});
	}

	@Test
	void theWeeklyRunDoesNotFileTheSameWeekTwice() {
		service.generateForLastWeek();
		service.generateForLastWeek();

		assertThat(repository.reportCount()).isEqualTo(3);
	}

	// ----------------------------------------------------------------- correcting ---

	@Test
	void aCorrectionIsStoredBesideTheReportAndTheReportIsUntouched() {
		Report family = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L).getFirst();

		ReportAmendment stored = service.amend(family.id(), "Visit 13 was cancelled by the family.", 9L);

		assertThat(stored.id()).isNotNull();
		assertThat(stored.reportId()).isEqualTo(family.id());
		assertThat(stored.authorUserId()).isEqualTo(9L);
		assertThat(stored.createdAt()).isEqualTo(NOW);

		Report read = service.findDetail(family.id());
		assertThat(read.amendments()).containsExactly(stored);
		assertThat(read.content()).isEqualTo(family.content());
		assertThat(read.status()).isEqualTo(family.status());
	}

	/**
	 * The columns keep whole seconds and MySQL rounds a fraction, so a time with one would
	 * answer the request differently from every read after it - 11:40:42.97 comes back 11:40:43.
	 */
	@Test
	void reportsAndCorrectionsAreDatedToTheSecondTheyAreStoredAt() {
		Clock withFraction = Clock.fixed(Instant.parse("2026-09-20T15:00:42.968699100Z"), ZoneId.of("Asia/Singapore"));
		ReportService onThatClock = new ReportService(repository, facts, withFraction);

		Report family = onThatClock.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L).getFirst();
		ReportAmendment stored = onThatClock.amend(family.id(), "note", 9L);

		assertThat(family.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 23, 0, 42));
		assertThat(stored.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 23, 0, 42));
	}

	@Test
	void correctingAReportThatDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> service.amend(404L, "note", 9L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void anEmptyCorrectionIsRefusedByTheRule() {
		Long familyId = service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L).getFirst().id();

		assertThatThrownBy(() -> service.amend(familyId, " ", 9L)).isInstanceOf(BusinessRuleViolation.class);
	}

	// -------------------------------------------------------------------- reading ---

	@Test
	void readingAReportThatDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> service.findDetail(404L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void theListShowsTheLatestPeriodFirstWithItsThreeVersionsTogether() {
		service.generate(ReportFixtures.ELDER, MONDAY.minusWeeks(1), SUNDAY.minusWeeks(1), 7L);
		service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);

		ReportPage page = service.page(null, null, 0, 20);

		assertThat(page.totalElements()).isEqualTo(6);
		assertThat(page.items()).extracting(report -> report.period().end()).containsExactly(
				SUNDAY, SUNDAY, SUNDAY, SUNDAY.minusWeeks(1), SUNDAY.minusWeeks(1), SUNDAY.minusWeeks(1));
		assertThat(page.items().subList(0, 3)).extracting(Report::audience).containsExactly(
				Report.Audience.FAMILY, Report.Audience.REGULATOR, Report.Audience.INTERNAL);
	}

	@Test
	void theListCanBeNarrowedToOneElderAndOneReader() {
		service.generate(ReportFixtures.ELDER, MONDAY, SUNDAY, 7L);

		ReportPage family = service.page(ReportFixtures.ELDER, Report.Audience.FAMILY, 0, 20);

		assertThat(family.items()).extracting(Report::audience).containsExactly(Report.Audience.FAMILY);
		assertThat(service.page(2L, null, 0, 20).items()).isEmpty();
	}

	@Test
	void pageAndSizeAreClampedRatherThanRefused() {
		assertThat(service.page(null, null, -1, 0)).satisfies(page -> {
			assertThat(page.page()).isZero();
			assertThat(page.size()).isEqualTo(1);
		});
		assertThat(service.page(null, null, 0, 1000).size()).isEqualTo(200);
	}
}
