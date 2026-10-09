package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** UC-MG06 orchestration: names resolved for the register, reviews saved with who and when. */
class CredentialReviewServiceTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	/** 2026-10-02 09:15 in Singapore. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T01:15:00Z"), ZoneId.of("UTC"));
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

	private final CredentialRepository credentials = mock(CredentialRepository.class);
	private final CredentialTypeRepository types = mock(CredentialTypeRepository.class);
	private final InMemoryCaregiverRepository caregivers = new InMemoryCaregiverRepository();
	private final CredentialReviewService service =
			new CredentialReviewService(credentials, types, caregivers, CLOCK, 30);

	@Test
	void theRegisterResolvesCaregiverAndTypeNamesAndTheRenewalItReplaces() {
		Caregiver devi = caregivers.save("Devi Raman", Caregiver.Status.AVAILABLE);
		Credential old = credential(1L, devi.id(), Credential.Status.PUBLISHED, TODAY.plusDays(12), null);
		Credential renewal = credential(2L, devi.id(), Credential.Status.SUBMITTED, LocalDate.of(2028, 8, 27), 1L);
		when(credentials.findAll()).thenReturn(List.of(old, renewal));
		when(types.findByIds(Set.of(11L))).thenReturn(List.of(new CredentialType(11L, "First aid", null)));

		List<CredentialRegisterRow> rows = service.register();

		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.id()).isEqualTo(2L);
			assertThat(row.caregiverName()).isEqualTo("Devi Raman");
			assertThat(row.credentialTypeName()).isEqualTo("First aid");
			assertThat(row.renewal()).isTrue();
			assertThat(row.state()).isEqualTo("SUBMITTED");
			assertThat(row.daysUntilExpiry()).isEqualTo(12L);
			assertThat(row.expiring()).isTrue();
			assertThat(row.replacesId()).isEqualTo(1L);
			assertThat(row.replacesExpiryDate()).isEqualTo(TODAY.plusDays(12));
			assertThat(row.expiryDate()).isEqualTo(LocalDate.of(2028, 8, 27));
		});
	}

	@Test
	void coversLeaveOutRejectedRows() {
		when(credentials.findAll()).thenReturn(List.of(
				credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(40), null),
				credential(2L, 202L, Credential.Status.REJECTED, TODAY.plusYears(1), null)));

		assertThat(service.covers()).containsExactly(
				new CredentialRegister.Cover(1L, 201L, 11L, TODAY.plusDays(40)));
	}

	@Test
	void publishingSavesTheReviewerAndTheTimeInSingapore() {
		Credential old = credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(12), null);
		when(credentials.findById(2L)).thenReturn(Optional.of(
				credential(2L, 201L, Credential.Status.SUBMITTED, LocalDate.of(2028, 8, 27), 1L)));
		when(credentials.findById(1L)).thenReturn(Optional.of(old));

		service.publish(2L, 7L);

		Credential saved = saved();
		assertThat(saved.status()).isEqualTo(Credential.Status.PUBLISHED);
		assertThat(saved.reviewedByUserId()).isEqualTo(7L);
		assertThat(saved.reviewedAt()).isEqualTo(LocalDateTime.now(CLOCK.withZone(SINGAPORE)));
	}

	@Test
	void aRejectionKeepsTheManagersNote() {
		when(credentials.findById(3L)).thenReturn(Optional.of(
				credential(3L, 201L, Credential.Status.SUBMITTED, TODAY.plusYears(2), null)));

		service.reject(3L, 7L, "Glare over the expiry date - please upload it again");

		assertThat(saved().status()).isEqualTo(Credential.Status.REJECTED);
		assertThat(saved().reviewNote()).isEqualTo("Glare over the expiry date - please upload it again");
	}

	@Test
	void reviewingSomethingAlreadyReviewedSavesNothing() {
		when(credentials.findById(4L)).thenReturn(Optional.of(
				credential(4L, 201L, Credential.Status.PUBLISHED, TODAY.plusYears(2), null)));

		assertThatThrownBy(() -> service.reject(4L, 7L, "too late")).isInstanceOf(BusinessRuleViolation.class);
		verify(credentials, never()).save(any());
	}

	@Test
	void anUnknownCredentialIsNotFound() {
		when(credentials.findById(5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.publish(5L, 7L)).isInstanceOf(ResourceNotFound.class);
	}

	private Credential saved() {
		ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
		verify(credentials).save(captor.capture());
		return captor.getValue();
	}

	private static Credential credential(Long id, Long caregiverId, Credential.Status status, LocalDate expiry,
			Long renews) {
		return new Credential(id, caregiverId, 11L, null, "CERT-" + id, "Singapore Red Cross", null, expiry, status,
				LocalDateTime.of(2026, 8, 27, 21, 4), null, renews);
	}
}
