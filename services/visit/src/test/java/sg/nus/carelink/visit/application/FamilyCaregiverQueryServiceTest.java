package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.profile.application.CaregiverPublicProfile;
import sg.nus.carelink.profile.application.CaregiverPublicCredential;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;

/**
 * Verifies access checks precede caregiver data lookup and failures remain distinct.
 *
 * @author Wang Zhili
 */
class FamilyCaregiverQueryServiceTest {

	private final FamilyReadAudit audit = mock(FamilyReadAudit.class);
	private final FamilyAccessQuery access = mock(FamilyAccessQuery.class);
	private final VisitScheduleQuery visits = mock(VisitScheduleQuery.class);
	private final CaregiverDirectory caregivers = mock(CaregiverDirectory.class);
	private final FamilyCaregiverQueryService service = new FamilyCaregiverQueryService(access, visits, caregivers, audit);

	@BeforeEach
	void executeAuditedQueries() {
		when(audit.read(anyString(), any(), nullable(Long.class), anyString(), any()))
				.thenAnswer(call -> call.<Supplier<?>>getArgument(4).get());
	}

	@Test
	void readsPublicDetailsOnlyAfterCheckingAllCurrentlyReadableElders() {
		var elderIds = Set.of(101L, 102L);
		var profile = new CaregiverPublicProfile(201L, "Lim Jia Hui", List.of("Mandarin"));
		when(access.readableElderIds("family-a")).thenReturn(elderIds);
		when(visits.hasAssignedVisit(elderIds, 201L)).thenReturn(true);
		when(caregivers.findPublicProfile(201L)).thenReturn(Optional.of(profile));

		assertThat(service.getProfile("family-a", 201L)).isEqualTo(profile);
		var ordered = inOrder(access, visits, caregivers);
		ordered.verify(access).readableElderIds("family-a");
		ordered.verify(visits).hasAssignedVisit(elderIds, 201L);
		ordered.verify(caregivers).findPublicProfile(201L);
	}

	@Test
	void unavailableFamilyStopsBeforeVisitOrProfileLookup() {
		when(access.readableElderIds("family-a")).thenThrow(new AccessDeniedException("Unavailable family"));

		assertThatThrownBy(() -> service.getProfile("family-a", 201L)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(visits, caregivers);
	}

	@Test
	void noReadableEldersCannotAccessAnyCaregiver() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of());

		assertThatThrownBy(() -> service.getProfile("family-a", 201L)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(visits, caregivers);
	}

	@Test
	void unrelatedCaregiverIsRejectedWithoutLookingUpWhetherTheirProfileExists() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L));
		when(visits.hasAssignedVisit(Set.of(101L), 999L)).thenReturn(false);

		assertThatThrownBy(() -> service.getProfile("family-a", 999L)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(caregivers);
	}

	@Test
	void missingProfileAfterAuthorizationIsReportedAsNotFound() {
		allowCaregiver();
		when(caregivers.findPublicProfile(201L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getProfile("family-a", 201L))
				.isInstanceOf(ResourceNotFound.class).hasMessage("Caregiver [201] does not exist");
	}

	@Test
	void failedRelationshipLookupDoesNotExposeProfileData() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L));
		when(visits.hasAssignedVisit(Set.of(101L), 201L))
				.thenThrow(new DataAccessResourceFailureException("Unavailable"));

		assertThatThrownBy(() -> service.getProfile("family-a", 201L))
				.isInstanceOf(DataAccessResourceFailureException.class);
		verifyNoInteractions(caregivers);
	}

	@Test
	void failedProfileLookupIsNotReportedAsMissingOrForbidden() {
		allowCaregiver();
		when(caregivers.findPublicProfile(201L)).thenThrow(new DataAccessResourceFailureException("Unavailable"));

		assertThatThrownBy(() -> service.getProfile("family-a", 201L))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	private void allowCaregiver() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L));
		when(visits.hasAssignedVisit(Set.of(101L), 201L)).thenReturn(true);
	}

	@Test
	void credentialListChecksRelationshipBeforeCallingTheDirectory() {
		allowCaregiver();
		var result = List.of(new CaregiverPublicCredential(401L, 201L, 11L, "First Aid", null,
				null, LocalDate.of(2027, 1, 1), "PUBLISHED"));
		when(caregivers.listPublicCredentials(201L)).thenReturn(result);

		assertThat(service.listCredentials("family-a", 201L)).isEqualTo(result);
		var ordered = inOrder(access, visits, caregivers);
		ordered.verify(access).readableElderIds("family-a");
		ordered.verify(visits).hasAssignedVisit(Set.of(101L), 201L);
		ordered.verify(caregivers).listPublicCredentials(201L);
		verify(caregivers, never()).findPublicProfile(201L);
	}

	@Test
	void caregiverWithoutPublicCredentialsReturnsAnEmptyListAfterAuthorization() {
		allowCaregiver();
		when(caregivers.listPublicCredentials(201L)).thenReturn(List.of());

		assertThat(service.listCredentials("family-a", 201L)).isEmpty();
	}

	@Test
	void credentialListRequiresCurrentFamilyIdentityBeforeAnyLookup() {
		when(access.readableElderIds("family-a")).thenThrow(new AccessDeniedException("Unavailable family"));

		assertThatThrownBy(() -> service.listCredentials("family-a", 201L)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(visits, caregivers);
	}

	@Test
	void unrelatedCaregiverCannotQueryEvenAnEmptyCredentialList() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L));
		when(visits.hasAssignedVisit(Set.of(101L), 999L)).thenReturn(false);

		assertThatThrownBy(() -> service.listCredentials("family-a", 999L)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(caregivers);
	}

	@Test
	void readingAProfileDoesNotAuthorizeLaterCredentialRequestsAfterRevocation() {
		when(access.readableElderIds("family-a")).thenReturn(Set.of(101L)).thenReturn(Set.of());
		when(visits.hasAssignedVisit(Set.of(101L), 201L)).thenReturn(true);
		when(caregivers.findPublicProfile(201L))
				.thenReturn(Optional.of(new CaregiverPublicProfile(201L, "Lim Jia Hui", List.of())));
		assertThat(service.getProfile("family-a", 201L).id()).isEqualTo(201L);

		assertThatThrownBy(() -> service.listCredentials("family-a", 201L)).isInstanceOf(AccessDeniedException.class);
		verify(caregivers, never()).listPublicCredentials(201L);
	}

	@Test
	void credentialLookupFailuresAreNotConvertedToEmptyLists() {
		allowCaregiver();
		when(caregivers.listPublicCredentials(201L)).thenThrow(new DataAccessResourceFailureException("Unavailable"));

		assertThatThrownBy(() -> service.listCredentials("family-a", 201L))
				.isInstanceOf(DataAccessResourceFailureException.class);
	}

	@Test
	void missingAuthorizedCaregiverIsNotConvertedToAnEmptyCredentialList() {
		allowCaregiver();
		when(caregivers.listPublicCredentials(201L)).thenThrow(new ResourceNotFound("Caregiver", 201L));

		assertThatThrownBy(() -> service.listCredentials("family-a", 201L)).isInstanceOf(ResourceNotFound.class);
	}
}
