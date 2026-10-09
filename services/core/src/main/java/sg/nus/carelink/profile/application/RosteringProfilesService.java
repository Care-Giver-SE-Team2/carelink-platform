package sg.nus.carelink.profile.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;

/** Reads profile for UC-MG04's replacement search; changes nothing. */
@Service
@Transactional(readOnly = true)
class RosteringProfilesService implements RosteringProfiles {

	private final CaregiverRepository caregivers;
	private final CredentialTypeRepository credentialTypes;
	private final ElderRepository elders;

	RosteringProfilesService(CaregiverRepository caregivers, CredentialTypeRepository credentialTypes,
			ElderRepository elders) {
		this.caregivers = caregivers;
		this.credentialTypes = credentialTypes;
		this.elders = elders;
	}

	@Override
	public List<Candidate> candidates() {
		return caregivers.findAll().stream()
				.filter(caregiver -> caregiver.status() != Caregiver.Status.INACTIVE)
				.map(caregiver -> new Candidate(caregiver.id(), caregiver.userId(), caregiver.fullName(),
						caregiver.sector(), caregiver.dialects(), caregiver.status() == Caregiver.Status.ONBOARDING))
				.toList();
	}

	@Override
	public Map<Long, String> credentialTypeNames(Collection<Long> credentialTypeIds) {
		if (credentialTypeIds.isEmpty()) {
			return Map.of();
		}
		return credentialTypes.findByIds(new HashSet<>(credentialTypeIds)).stream()
				.collect(Collectors.toMap(CredentialType::id, CredentialType::name, (a, b) -> a));
	}

	@Override
	public Optional<ElderFacts> elder(Long elderId) {
		return elders.findById(elderId)
				.map(elder -> new ElderFacts(elder.id(), elder.userId(), elder.fullName(), elder.sector(),
						elder.preferredDialects()));
	}
}
