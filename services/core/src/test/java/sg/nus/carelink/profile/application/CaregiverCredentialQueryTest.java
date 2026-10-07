package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Checks public credential projection, catalogue lookups and Singapore date handling.
 *
 * @author Wang Zhili
 */
class CaregiverCredentialQueryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);
	private final CaregiverRepository caregivers = mock(CaregiverRepository.class);
	private final CredentialRepository credentials = mock(CredentialRepository.class);
	private final CredentialTypeRepository types = mock(CredentialTypeRepository.class);
	private final CaregiverDirectory service = new CaregiverDirectoryService(caregivers, credentials, types,
			Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC));

	@Test
	void filtersPrivateCredentialsAndProjectsOrderedResultsUsingSingaporeDateAndPublicTypeNames() {
		caregiverExists();
		var expired = credential(501L, 11L, Credential.Status.PUBLISHED, TODAY.minusDays(1));
		var dueToday = credential(502L, 11L, Credential.Status.EXPIRING, TODAY);
		var revoked = credential(503L, 12L, Credential.Status.REVOKED, TODAY.plusYears(1));
		when(credentials.findByCaregiverId(201L)).thenReturn(List.of(expired, dueToday, revoked,
				credential(504L, 98L, Credential.Status.SUBMITTED, TODAY.plusYears(1)),
				credential(505L, 99L, Credential.Status.REJECTED, TODAY.plusYears(1))));
		when(types.findByIds(Set.of(11L, 12L))).thenReturn(List.of(
				new CredentialType(12L, "Medication Assistance", null),
				new CredentialType(11L, "First Aid", null)));

		assertThat(service.listPublicCredentials(201L)).containsExactly(
				new CaregiverPublicCredential(501L, 201L, 11L, "First Aid", "Training Centre",
						TODAY.minusYears(1), TODAY.minusDays(1), "EXPIRED"),
				new CaregiverPublicCredential(502L, 201L, 11L, "First Aid", "Training Centre",
						TODAY.minusYears(1), TODAY, "EXPIRING"),
				new CaregiverPublicCredential(503L, 201L, 12L, "Medication Assistance", "Training Centre",
						TODAY.minusYears(1), TODAY.plusYears(1), "REVOKED"));
		assertThat(expired.status()).isEqualTo(Credential.Status.PUBLISHED);
		verifyReadOnlyLookups(Set.of(11L, 12L));
	}

	@Test
	void preservesFutureStartAndPermanentExpiryWithNullablePublicFields() {
		caregiverExists();
		var permanent = LocalDate.of(9999, 12, 31);
		when(credentials.findByCaregiverId(201L)).thenReturn(List.of(
				new Credential(501L, 201L, 11L, 91L, "private-certificate", null, TODAY.plusDays(3),
						permanent, Credential.Status.PUBLISHED, null, null, 500L),
				new Credential(502L, 201L, 11L, 91L, "private-certificate", null, null,
						permanent, Credential.Status.PUBLISHED, null, null, null)));
		when(types.findByIds(Set.of(11L))).thenReturn(List.of(new CredentialType(11L, "Care Orientation", null)));

		assertThat(service.listPublicCredentials(201L)).containsExactly(
				new CaregiverPublicCredential(501L, 201L, 11L, "Care Orientation", null,
						TODAY.plusDays(3), permanent, "PUBLISHED"),
				new CaregiverPublicCredential(502L, 201L, 11L, "Care Orientation", null,
						null, permanent, "PUBLISHED"));
		verifyReadOnlyLookups(Set.of(11L));
	}

	@Test
	void missingCaregiverFailsBeforeCredentialOrTypeQueries() {
		when(caregivers.findById(201L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.listPublicCredentials(201L)).isInstanceOf(ResourceNotFound.class);
		verifyNoInteractions(credentials, types);
	}

	@Test
	void caregiverWithoutCredentialsReturnsAnEmptyListWithoutReadingTheCatalogue() {
		caregiverExists();
		when(credentials.findByCaregiverId(201L)).thenReturn(List.of());

		assertThat(service.listPublicCredentials(201L)).isEmpty();
		verifyNoInteractions(types);
		verify(caregivers).findById(201L);
		verify(credentials).findByCaregiverId(201L);
		verifyNoMoreInteractions(caregivers, credentials);
	}

	@Test
	void privateCredentialsOnlyReturnAnEmptyListWithoutReadingTheirTypes() {
		caregiverExists();
		when(credentials.findByCaregiverId(201L)).thenReturn(List.of(
				credential(501L, 11L, Credential.Status.SUBMITTED, TODAY.minusDays(1)),
				credential(502L, 12L, Credential.Status.REJECTED, TODAY.plusYears(1))));

		assertThat(service.listPublicCredentials(201L)).isEmpty();
		verifyNoInteractions(types);
	}

	@Test
	void missingPublicTypeFailsInsteadOfReturningAnIncompleteCredential() {
		caregiverExists();
		when(credentials.findByCaregiverId(201L)).thenReturn(List.of(
				credential(501L, 11L, Credential.Status.PUBLISHED, TODAY.plusYears(1))));
		when(types.findByIds(Set.of(11L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.listPublicCredentials(201L))
				.isInstanceOf(IllegalStateException.class).hasMessage("Public credential type is unavailable");
		verifyReadOnlyLookups(Set.of(11L));
	}

	@Test
	void caregiverStorageFailureIsNotReportedAsMissingOrAnEmptyCredentialList() {
		var failure = new DataAccessResourceFailureException("Caregiver storage unavailable");
		when(caregivers.findById(201L)).thenThrow(failure);

		assertThatThrownBy(() -> service.listPublicCredentials(201L)).isSameAs(failure);
		verifyNoInteractions(credentials, types);
	}

	@Test
	void credentialStorageFailureIsNotReportedAsAnEmptyList() {
		caregiverExists();
		var failure = new DataAccessResourceFailureException("Credential storage unavailable");
		when(credentials.findByCaregiverId(201L)).thenThrow(failure);

		assertThatThrownBy(() -> service.listPublicCredentials(201L)).isSameAs(failure);
		verifyNoInteractions(types);
	}

	@Test
	void catalogueStorageFailureIsNotReportedAsAnEmptyList() {
		caregiverExists();
		when(credentials.findByCaregiverId(201L)).thenReturn(List.of(
				credential(501L, 11L, Credential.Status.PUBLISHED, TODAY.plusYears(1))));
		var failure = new DataAccessResourceFailureException("Catalogue unavailable");
		when(types.findByIds(Set.of(11L))).thenThrow(failure);

		assertThatThrownBy(() -> service.listPublicCredentials(201L)).isSameAs(failure);
	}

	private void caregiverExists() {
		when(caregivers.findById(201L)).thenReturn(Optional.of(new Caregiver(201L, 1201L,
				"Lim Jia Hui", "private-phone", "private-sector", "Mandarin", Caregiver.Status.INACTIVE, null, null)));
	}

	private static Credential credential(Long id, Long typeId, Credential.Status status, LocalDate expiry) {
		return new Credential(id, 201L, typeId, 91L, "private-certificate", "Training Centre",
				TODAY.minusYears(1), expiry, status, TODAY.atStartOfDay(), TODAY.atStartOfDay(), 400L);
	}

	private void verifyReadOnlyLookups(Set<Long> typeIds) {
		verify(caregivers).findById(201L);
		verify(credentials).findByCaregiverId(201L);
		verify(types).findByIds(typeIds);
		verifyNoMoreInteractions(caregivers, credentials, types);
	}
}
