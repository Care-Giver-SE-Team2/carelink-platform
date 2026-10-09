package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;
import sg.nus.carelink.incident.domain.repository.IncidentAcknowledgementRepository;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentAcknowledgementJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class IncidentAcknowledgementRepositoryAdapter implements IncidentAcknowledgementRepository {

	private final IncidentAcknowledgementJpaRepository jpa;

	IncidentAcknowledgementRepositoryAdapter(IncidentAcknowledgementJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<IncidentAcknowledgement> findById(Long id) {
		return jpa.findById(id).map(IncidentAcknowledgementMapper::toDomain);
	}

	@Override
	public Optional<IncidentAcknowledgement> findByIncidentIdAndFamilyMemberId(Long incidentId, Long familyMemberId) {
		return jpa.findByIncidentIdAndFamilyMemberId(incidentId, familyMemberId)
				.map(IncidentAcknowledgementMapper::toDomain);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public IncidentAcknowledgement findOrCreateForUpdate(Long incidentId, Long familyMemberId) {
		jpa.ensurePair(incidentId, familyMemberId);
		return IncidentAcknowledgementMapper.toDomain(jpa.lockPair(incidentId, familyMemberId));
	}

	@Override
	public IncidentAcknowledgement save(IncidentAcknowledgement incidentAcknowledgement) {
		return IncidentAcknowledgementMapper.toDomain(jpa.save(IncidentAcknowledgementMapper.toEntity(incidentAcknowledgement)));
	}
}
