package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Projects stored caregiver profiles and credentials into public fields.
 *
 * @author Wang Zhili
 */
@Service
@Transactional(readOnly = true)
public class CaregiverDirectoryService implements CaregiverDirectory {

	private final CaregiverRepository caregivers;
	private final CredentialRepository credentials;
	private final CredentialTypeRepository credentialTypes;
	private final Clock clock;

	public CaregiverDirectoryService(CaregiverRepository caregivers, CredentialRepository credentials,
			CredentialTypeRepository credentialTypes, Clock clock) {
		this.caregivers = caregivers;
		this.credentials = credentials;
		this.credentialTypes = credentialTypes;
		this.clock = clock;
	}

	@Override
	public List<CaregiverPublicCredential> listPublicCredentials(Long caregiverId) {
		caregivers.findById(caregiverId).orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));
		LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));
		var publicCredentials = credentials.findByCaregiverId(caregiverId).stream()
				.filter(credential -> credential.publicStatusOn(today).isPresent()).toList();
		if (publicCredentials.isEmpty()) {
			return List.of();
		}
		var typeIds = publicCredentials.stream().map(Credential::credentialTypeId).collect(Collectors.toSet());
		var typeNames = credentialTypes.findByIds(typeIds).stream()
				.collect(Collectors.toMap(CredentialType::id, CredentialType::name));
		return publicCredentials.stream().map(credential -> publicCredential(credential, typeNames, today)).toList();
	}

	private static CaregiverPublicCredential publicCredential(Credential credential,
			Map<Long, String> typeNames, LocalDate today) {
		String typeName = typeNames.get(credential.credentialTypeId());
		if (typeName == null) {
			throw new IllegalStateException("Public credential type is unavailable");
		}
		return new CaregiverPublicCredential(credential.id(), credential.caregiverId(), credential.credentialTypeId(),
				typeName, credential.issuingBody(), credential.validFrom(), credential.expiryDate(),
				credential.publicStatusOn(today).orElseThrow().name());
	}

	@Override
	public Optional<CaregiverPublicProfile> findPublicProfile(Long caregiverId) {
		return caregivers.findById(caregiverId)
				.map(caregiver -> new CaregiverPublicProfile(caregiver.id(), caregiver.fullName(),
						dialectNames(caregiver.dialects())));
	}

	private static List<String> dialectNames(String storedDialects) {
		if (storedDialects == null) {
			return List.of();
		}
		return Arrays.stream(storedDialects.split(","))
				.map(String::strip).filter(dialect -> !dialect.isEmpty()).toList();
	}
}
