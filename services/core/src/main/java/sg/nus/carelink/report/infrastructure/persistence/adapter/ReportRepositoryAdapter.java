package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportRepository;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportAmendmentJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ReportAmendmentJpaRepository;
import sg.nus.carelink.report.infrastructure.persistence.repository.ReportJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 *
 * <p>A report comes back with its corrections: they are a separate table but not a separate
 * idea, and a report read without them would show a reader text that has since been
 * corrected. For a page of the list they are read in one query for the whole page.
 */
@Repository
class ReportRepositoryAdapter implements ReportRepository {

	/**
	 * Most recent period first; within a period the three readers' versions together, in the
	 * order the audience column declares them - FAMILY, REGULATOR, INTERNAL - then elder and id
	 * so that a page boundary never falls differently between two reads.
	 */
	private static final Sort LIST_ORDER = Sort.by(
			Sort.Order.desc("periodEnd"),
			Sort.Order.asc("audience"),
			Sort.Order.asc("elderId"),
			Sort.Order.desc("id"));

	private static final Sort FAMILY_LIST_ORDER = Sort.by(
			Sort.Order.desc("periodStart"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final ReportJpaRepository reports;
	private final ReportAmendmentJpaRepository amendments;

	ReportRepositoryAdapter(ReportJpaRepository reports, ReportAmendmentJpaRepository amendments) {
		this.reports = reports;
		this.amendments = amendments;
	}

	@Override
	public Optional<Report> findById(Long id) {
		return reports.findById(id).map(this::withAmendments);
	}

	@Override
	public boolean existsById(Long id) {
		return reports.existsById(id);
	}

	@Override
	public Optional<Report> findFamilyDetail(Long id, Set<Long> readableElderIds) {
		return reports.findByIdAndElderIdInAndAudienceAndStatusIn(id, readableElderIds,
				ReportJpaEntity.Audience.FAMILY,
				EnumSet.of(ReportJpaEntity.Status.PUBLISHED, ReportJpaEntity.Status.ARCHIVED))
				.map(this::withAmendments);
	}

	@Override
	public Optional<Report> findLatestFamilyFor(Long elderId, ReportPeriod period) {
		return reports.findFirstByElderIdAndAudienceAndStatusInAndPeriodStartAndPeriodEndOrderByCreatedAtDescIdDesc(
				elderId, ReportJpaEntity.Audience.FAMILY,
				EnumSet.of(ReportJpaEntity.Status.PUBLISHED, ReportJpaEntity.Status.ARCHIVED), period.start(), period.end())
				.map(row -> ReportMapper.toDomain(row, List.of()));
	}

	/**
	 * Inserts, never updates: a report that already has an id is refused rather than merged
	 * over the stored one, which is what {@code save} on an existing entity would otherwise do.
	 */
	@Override
	public Report save(Report report) {
		if (report.id() != null) {
			throw new IllegalArgumentException("Report %d is already on file; reports are stored once".formatted(report.id()));
		}
		return ReportMapper.toDomain(reports.save(ReportMapper.toEntity(report)), List.of());
	}

	@Override
	public ReportAmendment saveAmendment(ReportAmendment amendment) {
		if (amendment.id() != null) {
			throw new IllegalArgumentException("Correction %d is already on file".formatted(amendment.id()));
		}
		return ReportAmendmentMapper.toDomain(amendments.save(ReportAmendmentMapper.toEntity(amendment)));
	}

	@Override
	public Optional<Report> findFor(Long elderId, Report.Audience audience, ReportPeriod period) {
		return reports
				.findFirstByElderIdAndAudienceAndPeriodStartAndPeriodEndOrderByIdAsc(
						elderId, ReportJpaEntity.Audience.valueOf(audience.name()), period.start(), period.end())
				.map(this::withAmendments);
	}

	@Override
	public ReportPage findPage(Long elderId, Report.Audience audience, int page, int size) {
		// No reader chosen means all three, spelled out: see the note on the query.
		Set<ReportJpaEntity.Audience> audiences = audience == null
				? EnumSet.allOf(ReportJpaEntity.Audience.class)
				: EnumSet.of(ReportJpaEntity.Audience.valueOf(audience.name()));

		PageRequest request = PageRequest.of(page, size, LIST_ORDER);
		Page<ReportJpaEntity> rows = elderId == null
				? reports.findByAudienceIn(audiences, request)
				: reports.findByElderIdAndAudienceIn(elderId, audiences, request);

		Map<Long, List<ReportAmendmentJpaEntity>> corrections = rows.isEmpty()
				? Map.of()
				: amendments.findByReportIdInOrderByCreatedAtAscIdAsc(
								rows.getContent().stream().map(ReportJpaEntity::getId).toList())
						.stream()
						.collect(Collectors.groupingBy(ReportAmendmentJpaEntity::getReportId));

		return new ReportPage(
				rows.getContent().stream()
						.map(row -> ReportMapper.toDomain(row, corrections.getOrDefault(row.getId(), List.of())))
						.toList(),
				rows.getNumber(),
				rows.getSize(),
				rows.getTotalElements());
	}

	@Override
	public ReportPage findFamilyPage(Set<Long> elderIds, int page, int size) {
		var states = EnumSet.of(ReportJpaEntity.Status.PUBLISHED, ReportJpaEntity.Status.ARCHIVED);
		PageRequest request = PageRequest.of(page, size, FAMILY_LIST_ORDER);
		long total = reports.countByElderIdInAndAudienceAndStatusIn(elderIds, ReportJpaEntity.Audience.FAMILY, states);
		// Count first, so even a large valid page beyond the result avoids JPA's int offset limit.
		if (request.getOffset() >= total) {
			return new ReportPage(List.of(), page, size, total);
		}
		var rows = reports.findByElderIdInAndAudienceAndStatusIn(elderIds, ReportJpaEntity.Audience.FAMILY, states, request);
		return new ReportPage(rows.stream().map(row -> ReportMapper.toDomain(row, List.of())).toList(), page, size, total);
	}

	private Report withAmendments(ReportJpaEntity row) {
		return ReportMapper.toDomain(row, amendments.findByReportIdOrderByCreatedAtAscIdAsc(row.getId()));
	}
}
