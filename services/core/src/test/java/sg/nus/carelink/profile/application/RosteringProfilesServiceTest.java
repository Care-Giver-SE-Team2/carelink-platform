package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;

/** What UC-MG04 reads from profile: who may replace whom, what the certificates are called, who the elder is. */
class RosteringProfilesServiceTest {

	private final CaregiverRepository caregivers = mock(CaregiverRepository.class);
	private final CredentialTypeRepository types = mock(CredentialTypeRepository.class);
	private final ElderRepository elders = mock(ElderRepository.class);
	private final RosteringProfilesService service = new RosteringProfilesService(caregivers, types, elders);

	@Test
	void caregiversWhoLeftAreNotCandidatesAndOnboardingOnesAreMarked() {
		when(caregivers.findAll()).thenReturn(List.of(
				caregiver(5L, Caregiver.Status.AVAILABLE),
				caregiver(6L, Caregiver.Status.ONBOARDING),
				caregiver(7L, Caregiver.Status.INACTIVE),
				caregiver(8L, Caregiver.Status.BUSY)));

		assertThat(service.candidates()).extracting(RosteringProfiles.Candidate::caregiverId).containsExactly(5L, 6L, 8L);
		assertThat(service.candidates()).filteredOn(RosteringProfiles.Candidate::onboarding)
				.extracting(RosteringProfiles.Candidate::caregiverId).containsExactly(6L);
		assertThat(service.candidates().get(0).dialects()).isEqualTo("Hokkien");
	}

	@Test
	void namesCertificatesAndElders() {
		when(types.findByIds(Set.of(2L))).thenReturn(List.of(new CredentialType(2L, "Nursing", null)));
		when(elders.findById(7L)).thenReturn(Optional.of(new Elder(7L, 1007L, "Tan Bee Lian", null, null, null, null,
				null, "Toa Payoh", "Hokkien", null, null, Elder.ContinuityPreference.PREFERRED, null, null, null)));
		when(elders.findById(404L)).thenReturn(Optional.empty());

		assertThat(service.credentialTypeNames(List.of(2L))).containsEntry(2L, "Nursing");
		assertThat(service.elder(7L)).get().extracting(RosteringProfiles.ElderFacts::sector).isEqualTo("Toa Payoh");
		assertThat(service.elder(404L)).isEmpty();
	}

	@Test
	void noTypesAskedForMeansNoQuery() {
		assertThat(service.credentialTypeNames(List.of())).isEmpty();
		verifyNoInteractions(types);
	}

	private static Caregiver caregiver(Long id, Caregiver.Status status) {
		return new Caregiver(id, 1000 + id, "Caregiver " + id, null, "Toa Payoh", "Hokkien", status, null, null);
	}
}
