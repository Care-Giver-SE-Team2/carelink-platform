package sg.nus.carelink.report.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportRepository;

/**
 * Test double for the port: the service is exercised without Spring or a database (as in identity).
 *
 * <p>Append-only like the adapter: storing a report that already has an id is refused, and a
 * correction is kept beside its report rather than written into it.
 */
class InMemoryReportRepository implements ReportRepository {

	private final Map<Long, Report> rows = new LinkedHashMap<>();
	private final List<ReportAmendment> amendments = new ArrayList<>();
	private long nextId = 1;
	private long nextAmendmentId = 1;

	@Override
	public Optional<Report> findById(Long id) {
		return Optional.ofNullable(rows.get(id)).map(this::withAmendments);
	}

	@Override
	public boolean existsById(Long id) {
		return rows.containsKey(id);
	}

	@Override
	public Optional<Report> findFamilyDetail(Long id, Set<Long> readableElderIds) {
		return findById(id).filter(report -> readableElderIds.contains(report.elderId()))
				.filter(report -> report.audience() == Report.Audience.FAMILY)
				.filter(report -> report.status() == Report.Status.PUBLISHED || report.status() == Report.Status.ARCHIVED);
	}

	@Override
	public Report save(Report report) {
		if (report.id() != null) {
			throw new IllegalArgumentException("reports are stored once");
		}
		Report stored = new Report(nextId++, report.elderId(), report.basisId(), report.generatedByUserId(),
				report.audience(), report.period(), report.status(), report.content(), List.of(), report.createdAt());
		rows.put(stored.id(), stored);
		return stored;
	}

	@Override
	public Optional<Report> findLatestFamilyFor(Long elderId, ReportPeriod period) {
		return rows.values().stream()
				.filter(report -> report.elderId().equals(elderId) && report.audience() == Report.Audience.FAMILY)
				.filter(report -> report.status() == Report.Status.PUBLISHED || report.status() == Report.Status.ARCHIVED)
				.filter(report -> report.period().equals(period))
				.max(Comparator.comparing(Report::createdAt).thenComparing(Report::id));
	}

	@Override
	public ReportAmendment saveAmendment(ReportAmendment amendment) {
		ReportAmendment stored = new ReportAmendment(nextAmendmentId++, amendment.reportId(), amendment.kind(),
				amendment.note(), amendment.authorUserId(), amendment.createdAt());
		amendments.add(stored);
		return stored;
	}

	@Override
	public Optional<Report> findFor(Long elderId, Report.Audience audience, ReportPeriod period) {
		return rows.values().stream()
				.filter(report -> report.elderId().equals(elderId))
				.filter(report -> report.audience() == audience)
				.filter(report -> report.period().equals(period))
				.findFirst()
				.map(this::withAmendments);
	}

	/** The adapter's order: latest period first, then reader, elder, newest id. */
	@Override
	public ReportPage findPage(Long elderId, Report.Audience audience, int page, int size) {
		List<Report> matching = rows.values().stream()
				.filter(report -> elderId == null || report.elderId().equals(elderId))
				.filter(report -> audience == null || report.audience() == audience)
				.sorted(Comparator.comparing((Report report) -> report.period().end()).reversed()
						.thenComparing(Report::audience)
						.thenComparing(Report::elderId)
						.thenComparing(Report::id, Comparator.reverseOrder()))
				.map(this::withAmendments)
				.toList();
		int from = Math.min(page * size, matching.size());
		int to = Math.min(from + size, matching.size());
		return new ReportPage(matching.subList(from, to), page, size, matching.size());
	}

	@Override
	public ReportPage findFamilyPage(Set<Long> elderIds, int page, int size) {
		List<Report> matching = rows.values().stream()
				.filter(report -> elderIds.contains(report.elderId()) && report.audience() == Report.Audience.FAMILY)
				.filter(report -> report.status() == Report.Status.PUBLISHED || report.status() == Report.Status.ARCHIVED)
				.sorted(Comparator.comparing((Report report) -> report.period().start()).reversed()
						.thenComparing(Report::createdAt, Comparator.reverseOrder())
						.thenComparing(Report::id, Comparator.reverseOrder()))
				.toList();
		return new ReportPage(matching.stream().skip((long) page * size).limit(size).toList(), page, size, matching.size());
	}

	int reportCount() {
		return rows.size();
	}

	private Report withAmendments(Report report) {
		List<ReportAmendment> own = amendments.stream()
				.filter(amendment -> Objects.equals(amendment.reportId(), report.id()))
				.toList();
		return new Report(report.id(), report.elderId(), report.basisId(), report.generatedByUserId(),
				report.audience(), report.period(), report.status(), report.content(), own, report.createdAt());
	}
}
