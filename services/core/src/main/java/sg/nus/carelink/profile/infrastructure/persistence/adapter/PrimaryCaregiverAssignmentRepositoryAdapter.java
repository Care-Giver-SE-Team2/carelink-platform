package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.domain.repository.PrimaryCaregiverAssignmentRepository;
import sg.nus.carelink.profile.infrastructure.persistence.repository.PrimaryCaregiverAssignmentJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class PrimaryCaregiverAssignmentRepositoryAdapter implements PrimaryCaregiverAssignmentRepository {

	private final PrimaryCaregiverAssignmentJpaRepository jpa;

	PrimaryCaregiverAssignmentRepositoryAdapter(PrimaryCaregiverAssignmentJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<PrimaryCaregiverAssignment> findByElderId(Long elderId) {
		return jpa.findById(elderId).map(PrimaryCaregiverAssignmentMapper::toDomain);
	}

	@Override
	public List<PrimaryCaregiverAssignment> findByElderIds(Set<Long> elderIds) {
		return jpa.findByElderIdIn(elderIds).stream().map(PrimaryCaregiverAssignmentMapper::toDomain).toList();
	}

	@Override
	public PrimaryCaregiverAssignment save(PrimaryCaregiverAssignment assignment) {
		return PrimaryCaregiverAssignmentMapper.toDomain(
				jpa.save(PrimaryCaregiverAssignmentMapper.toEntity(assignment)));
	}

	@Override
	public void deleteByElderId(Long elderId) {
		jpa.deleteById(elderId);
	}
}
