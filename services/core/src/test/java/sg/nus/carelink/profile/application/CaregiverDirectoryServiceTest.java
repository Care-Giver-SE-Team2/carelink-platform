package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;

/**
 * Checks public profile projection and the stored comma-separated language format.
 *
 * @author Wang Zhili
 */
class CaregiverDirectoryServiceTest {

	private final CaregiverRepository caregivers = mock(CaregiverRepository.class);
	private final CaregiverDirectory service = new CaregiverDirectoryService(caregivers,
			mock(CredentialRepository.class), mock(CredentialTypeRepository.class), Clock.systemUTC());

	@Test
	void returnsPublicFieldsAndPreservesLanguageOrderSpellingAndDuplicates() {
		when(caregivers.findById(201L)).thenReturn(Optional.of(caregiver(" Mandarin, ,Hokkien, Mandarin,\tTamil ,")));

		assertThat(service.findPublicProfile(201L)).contains(new CaregiverPublicProfile(
				201L, "Lim Jia Hui", List.of("Mandarin", "Hokkien", "Mandarin", "Tamil")));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  \t ", ", ,,," })
	void missingLanguagesReturnAnEmptyArray(String dialects) {
		when(caregivers.findById(201L)).thenReturn(Optional.of(caregiver(dialects)));

		assertThat(service.findPublicProfile(201L)).contains(new CaregiverPublicProfile(
				201L, "Lim Jia Hui", List.of()));
	}

	@Test
	void doesNotGuessUndocumentedLanguageSeparators() {
		when(caregivers.findById(201L)).thenReturn(Optional.of(caregiver("Mandarin;Hokkien")));

		assertThat(service.findPublicProfile(201L)).contains(new CaregiverPublicProfile(
				201L, "Lim Jia Hui", List.of("Mandarin;Hokkien")));
	}

	@Test
	void missingCaregiverReturnsNoProfile() {
		when(caregivers.findById(999L)).thenReturn(Optional.empty());

		assertThat(service.findPublicProfile(999L)).isEmpty();
	}

	@Test
	void storageFailureDoesNotLookLikeAMissingProfile() {
		when(caregivers.findById(201L)).thenThrow(new DataAccessResourceFailureException("Unavailable"));

		assertThatThrownBy(() -> service.findPublicProfile(201L))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	@Test
	void crossModuleProfileDoesNotShareMutableLanguageLists() {
		var languages = new ArrayList<>(List.of("Mandarin"));
		var profile = new CaregiverPublicProfile(201L, "Lim Jia Hui", languages);
		languages.add("Hokkien");

		assertThat(profile.dialects()).containsExactly("Mandarin");
		var publishedLanguages = profile.dialects();
		assertThatThrownBy(() -> publishedLanguages.add("Tamil"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	private static Caregiver caregiver(String dialects) {
		return new Caregiver(201L, 1201L, "Lim Jia Hui", "private-phone", "private-sector",
				dialects, Caregiver.Status.INACTIVE, null, null);
	}
}
