package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitJpaRepository;

/**
 * Applies authorized elder scope and schedule filters in the database.
 *
 * @author Wang Zhili
 */
@Repository
class VisitScheduleQueryAdapter implements VisitScheduleQuery {

	private final VisitJpaRepository jpa;

	VisitScheduleQueryAdapter(VisitJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public VisitPage findForElders(Set<Long> elderIds, VisitScheduleFilter filter, VisitScheduleFilter.DateRange dates) {
		if (elderIds.isEmpty()) {
			return new VisitPage(List.of(), filter.page(), filter.size(), 0);
		}
		var specification = specification(elderIds, filter, dates);
		long total = jpa.count(specification);
		var pageable = PageRequest.of(filter.page(), filter.size(), Sort.by("scheduledStart", "id"));
		if (pageable.getOffset() >= total) {
			return new VisitPage(List.of(), filter.page(), filter.size(), total);
		}
		var items = jpa.findAll(specification, pageable).stream().map(VisitMapper::toDomain).toList();
		return new VisitPage(items, filter.page(), filter.size(), total);
	}

	@Override
	public boolean hasAssignedVisit(Set<Long> elderIds, Long caregiverId) {
		return !elderIds.isEmpty() && jpa.existsByElderIdInAndCaregiverId(elderIds, caregiverId);
	}

	private static Specification<VisitJpaEntity> specification(Set<Long> elderIds,
			VisitScheduleFilter filter, VisitScheduleFilter.DateRange dates) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(root.get("elderId").in(elderIds));
			predicates.add(builder.greaterThanOrEqualTo(root.get("scheduledStart"), dates.fromInclusive()));
			predicates.add(builder.lessThan(root.get("scheduledStart"), dates.toExclusive()));
			if (filter.caregiverId() != null) {
				predicates.add(builder.equal(root.get("caregiverId"), filter.caregiverId()));
			}
			if (filter.status() != null) {
				predicates.add(builder.equal(root.get("status"), VisitJpaEntity.Status.valueOf(filter.status().name())));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
