package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class IncidentRepositoryAdapter implements IncidentRepository {

	/**
	 * The states that still owe somebody a response. Mirrors
	 * {@code Incident.awaitingTakeOver()}; kept here because the Spring Data query needs the
	 * persistence enum, while the rule itself is stated once in the domain model.
	 */
	private static final Set<IncidentJpaEntity.Status> AWAITING_TAKE_OVER =
			Set.of(IncidentJpaEntity.Status.OPEN, IncidentJpaEntity.Status.ACKNOWLEDGED);

	/**
	 * The state the queue pins above everything else: the chain ran out and nobody is
	 * answerable any more (UC-MG05 3b). Handed to the query as a parameter rather than
	 * spelled as a nested enum's name inside JPQL.
	 */
	private static final IncidentJpaEntity.Status PINNED_TO_THE_TOP =
			IncidentJpaEntity.Status.UNRESOLVED_ESCALATED;

	private final IncidentJpaRepository jpa;

	IncidentRepositoryAdapter(IncidentJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<Incident> findById(Long id) {
		return jpa.findById(id).map(IncidentMapper::toDomain);
	}

	@Override
	public Incident save(Incident incident) {
		return IncidentMapper.toDomain(jpa.save(IncidentMapper.toEntity(incident)));
	}

	@Override
	public List<Incident> findAwaitingTakeOverPastDeadline(LocalDateTime deadline) {
		return jpa
				.findByStatusInAndRespondByNotNullAndRespondByLessThanEqualOrderByRespondByAsc(
						AWAITING_TAKE_OVER, deadline)
				.stream()
				.map(IncidentMapper::toDomain)
				.toList();
	}

	@Override
	public Optional<Long> lastResponderForElder(Long elderId, Long excludingIncidentId) {
		if (elderId == null) {
			return Optional.empty();
		}
		// -1 rather than null: the derived query needs a value to compare against, and no
		// row can carry that id, so an incident that has not been saved yet excludes nothing.
		Long excluded = excludingIncidentId == null ? -1L : excludingIncidentId;
		return jpa
				.findByElderIdAndIdNotAndResponderUserIdNotNullOrderByReportedAtDesc(
						elderId, excluded, PageRequest.of(0, 1))
				.stream()
				.findFirst()
				.map(IncidentJpaEntity::getResponderUserId);
	}

	@Override
	public List<Incident> findByElder(Long elderId) {
		return jpa.findByElderIdOrderByReportedAtDesc(elderId).stream()
				.map(IncidentMapper::toDomain)
				.toList();
	}

	/**
	 * The domain states which incidents it wants; the translation to the persistence enums
	 * happens here and only here. JPQL that names a domain enum does not compile against the
	 * entity's own type, so the mapping belongs at this boundary with the rest of it.
	 */
	@Override
	public PageSlice<Incident> findQueue(
			Set<Incident.Status> statuses, Incident.Severity severity, Long elderId, int page, int size) {

		Set<IncidentJpaEntity.Status> rowStatuses = EnumSet.noneOf(IncidentJpaEntity.Status.class);
		statuses.forEach(status -> rowStatuses.add(IncidentJpaEntity.Status.valueOf(status.name())));

		// No severity filter means every severity, spelled out: see the note on the query.
		Set<IncidentJpaEntity.Severity> rowSeverities = severity == null
				? EnumSet.allOf(IncidentJpaEntity.Severity.class)
				: EnumSet.of(IncidentJpaEntity.Severity.valueOf(severity.name()));

		// Unsorted on purpose: the order is the query's own, and a sort here would be
		// appended after it.
		Page<IncidentJpaEntity> rows = jpa.findQueue(
				rowStatuses, rowSeverities, elderId, PINNED_TO_THE_TOP, PageRequest.of(page, size));

		return new PageSlice<>(
				rows.getContent().stream().map(IncidentMapper::toDomain).toList(),
				rows.getNumber(),
				rows.getSize(),
				rows.getTotalElements());
	}
}
