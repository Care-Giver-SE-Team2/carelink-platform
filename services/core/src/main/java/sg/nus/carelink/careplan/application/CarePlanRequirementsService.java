package sg.nus.carelink.careplan.application;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.domain.model.CarePlanRequiredCredential;
import sg.nus.carelink.careplan.domain.repository.CarePlanRequiredCredentialRepository;

@Service
@Transactional(readOnly = true)
class CarePlanRequirementsService implements CarePlanRequirements {

	private final CarePlanRequiredCredentialRepository required;

	CarePlanRequirementsService(CarePlanRequiredCredentialRepository required) {
		this.required = required;
	}

	@Override
	public Map<Long, Set<Long>> requiredCredentialTypes(Collection<Long> carePlanIds) {
		return required.findByCarePlanIds(carePlanIds).stream()
				.map(CarePlanRequiredCredential::id)
				.collect(Collectors.groupingBy(CarePlanRequiredCredential.Id::carePlanId,
						Collectors.mapping(CarePlanRequiredCredential.Id::credentialTypeId, Collectors.toSet())));
	}
}
