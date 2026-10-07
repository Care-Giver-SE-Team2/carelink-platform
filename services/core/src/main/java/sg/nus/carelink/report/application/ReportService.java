package sg.nus.carelink.report.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;
import sg.nus.carelink.report.domain.repository.ReportRepository;
import sg.nus.carelink.report.domain.service.ReportAssembler;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Application layer of the report module (periodic reports, value-added services and requests, caregiver reviews).
 *
 * <p>One public method per use case (UC-MG07, UC-FM04, UC-FM09): it loads what it needs through
 * the domain ports, calls the domain model, saves, and returns. Business rules stay in
 * domain.model. identity.application.IdentityService is the template.
 *
 * <p>For UC-MG07 that means: gather one elder's facts once, hand the same facts to the
 * assembler for each reader, file what comes back. How the three versions differ is decided
 * in {@code domain.service}; if a filtering rule appears in this file, it is in the wrong
 * place.
 */
@Service
@Transactional
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	/** The contract's upper bound for {@code size} on GET /reports. */
	private static final int MAX_PAGE_SIZE = 200;

	private final ReportRepository reports;
	private final ReportFactsSource facts;
	private final Clock clock;

	ReportService(ReportRepository reports, ReportFactsSource facts, Clock clock) {
		this.reports = reports;
		this.facts = facts;
		this.clock = clock;
	}

	// ----------------------------------------------------------------- generating ---

	/**
	 * UC-MG07 steps 1 to 4: generate and file the three readers' reports for a period.
	 *
	 * <p>Idempotent. A report already filed for the same elder, reader and period is returned
	 * as it is rather than filed again or refused: the weekly run and a manager pressing
	 * Generate for the same week are the same request, and pressing it twice is not a mistake
	 * worth a 409. Reports are never regenerated - they are archived as generated - so a
	 * period whose data was incomplete stays marked that way and is corrected by amendment.
	 *
	 * <p>One transaction for the whole run. If any elder's reports cannot be produced, none of
	 * this run's are: the use case's failure outcome is "不产生报告", and a half-filed week
	 * would be harder to explain than an empty one. Running it again is safe.
	 *
	 * @param elderId           one elder, or null for every elder with a visit in the period
	 * @param requestedByUserId who asked, or null for the scheduled run
	 * @return every elder's three reports, FAMILY, REGULATOR, INTERNAL for each
	 */
	public List<Report> generate(Long elderId, LocalDate periodStart, LocalDate periodEnd, Long requestedByUserId) {
		ReportPeriod period = new ReportPeriod(periodStart, periodEnd);
		List<Long> elders = elderId == null ? facts.eldersWithVisitsIn(period) : List.of(elderId);

		List<Report> generated = new ArrayList<>();
		for (Long elder : elders) {
			generated.addAll(generateFor(elder, period, requestedByUserId));
		}
		return generated;
	}

	/**
	 * The scheduled run: the week ending on or before today, for every elder who had a visit
	 * in it. Called by {@code ReportScheduler}; "today" is the application clock's, in the
	 * institution's zone, not the server's.
	 */
	public List<Report> generateForLastWeek() {
		ReportPeriod week = ReportPeriod.weekEndingOnOrBefore(LocalDate.now(clock));
		List<Report> filed = generate(null, week.start(), week.end(), null);
		log.info("Weekly reports for {} to {}: {} report(s) on file", week.start(), week.end(), filed.size());
		return filed;
	}

	/**
	 * One elder's three reports. The facts are read once, and only if at least one reader's
	 * report is still missing; every reader's assembler is given the same facts.
	 */
	private List<Report> generateFor(Long elderId, ReportPeriod period, Long requestedByUserId) {
		Map<Report.Audience, Report> onFile = new EnumMap<>(Report.Audience.class);
		for (Report.Audience audience : Report.Audience.values()) {
			reports.findFor(elderId, audience, period).ifPresent(report -> onFile.put(audience, report));
		}
		if (onFile.size() == Report.Audience.values().length) {
			return List.copyOf(onFile.values());
		}

		ReportFacts gathered = facts.gather(elderId, period)
				.orElseThrow(() -> new ResourceNotFound("Elder", elderId));
		LocalDateTime now = now();

		List<Report> result = new ArrayList<>();
		for (Report.Audience audience : Report.Audience.values()) {
			Report report = onFile.get(audience);
			if (report == null) {
				report = reports.save(Report.generate(
						elderId,
						audience,
						period,
						ReportAssembler.forAudience(audience).assemble(gathered),
						requestedByUserId,
						now));
			}
			result.add(report);
		}
		return result;
	}

	// ----------------------------------------------------------------- correcting ---

	/**
	 * Appends a correction to a filed report. The report's own row is not written: the
	 * correction is stored beside it, dated and signed.
	 *
	 * @return the stored correction
	 */
	public ReportAmendment amend(Long reportId, String note, Long authorUserId) {
		Report amended = require(reportId).amend(note, authorUserId, now());
		return reports.saveAmendment(amended.amendments().getLast());
	}

	// -------------------------------------------------------------------- reading ---

	/** One report with every correction appended to it. */
	@Transactional(readOnly = true)
	public Report findDetail(Long id) {
		return require(id);
	}

	/**
	 * The manager's list of filed reports.
	 *
	 * <p>Page and size are clamped rather than rejected, as on the incident queue: they come
	 * from a URL, where {@code page=-1} is a slip, and an unbounded size is how one request
	 * reads the whole table.
	 */
	@Transactional(readOnly = true)
	public ReportPage page(Long elderId, Report.Audience audience, int page, int size) {
		return reports.findPage(elderId, audience, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
	}

	private Report require(Long id) {
		return reports.findById(id).orElseThrow(() -> new ResourceNotFound("Report", id));
	}

	/**
	 * The application clock, to the second. The columns a report and a correction are dated
	 * in keep no fractions, and MySQL rounds a fraction rather than dropping it: a correction
	 * answered with 11:40:42.97 would read back as 11:40:43, and at 11:40:59.6 the minute the
	 * screen shows would change between the answer and every read after it. Truncating here
	 * makes what the request answers with the same as what is on file.
	 */
	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
	}
}
