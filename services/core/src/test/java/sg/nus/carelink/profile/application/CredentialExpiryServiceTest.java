package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CredentialExpiryAlert;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Lapse;

/** SYS01 orchestration: today in Singapore, new statuses saved, each one handed to the alert with names. */
class CredentialExpiryServiceTest {

	/** 2026-10-04 01:15 in Singapore, still 2026-10-03 in UTC. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T17:15:00Z"), ZoneId.of("UTC"));
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

	private final CredentialRepository credentials = mock(CredentialRepository.class);
	private final CredentialTypeRepository types = mock(CredentialTypeRepository.class);
	private final InMemoryCaregiverRepository caregivers = new InMemoryCaregiverRepository();
	private final CredentialExpiryAlert alert = mock(CredentialExpiryAlert.class);
	private final CredentialExpiryService service =
			new CredentialExpiryService(credentials, types, caregivers, alert, CLOCK, 30);

	@Test
	void savesEachMoveAndHandsItToTheAlertWithTheCaregiverAndTypeName() {
		Caregiver devi = caregivers.save("Devi Raman", Caregiver.Status.AVAILABLE);
		when(credentials.findAll()).thenReturn(List.of(
				credential(1L, devi.id(), Credential.Status.PUBLISHED, TODAY.plusDays(30)),
				credential(2L, devi.id(), Credential.Status.EXPIRING, TODAY.minusDays(1)),
				credential(3L, devi.id(), Credential.Status.PUBLISHED, TODAY.plusDays(31))));
		when(types.findByIds(Set.of(11L))).thenReturn(List.of(new CredentialType(11L, "First aid", null)));
		when(alert.lapsed(any(), any(), anyString())).thenReturn(2);

		var result = service.scanToday();

		ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
		verify(credentials, times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(Credential::id, Credential::status).containsExactly(
				tuple(1L, Credential.Status.EXPIRING),
				tuple(2L, Credential.Status.EXPIRED));

		verify(alert, times(2)).lapsed(any(Lapse.class), eq(devi), eq("First aid"));
		assertThat(result).isEqualTo(new CredentialExpiryService.Result(1, 1, 4));
	}

	@Test
	void aQuietDayChangesNothing() {
		when(credentials.findAll()).thenReturn(List.of(credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(90))));

		assertThat(service.scanToday()).isEqualTo(new CredentialExpiryService.Result(0, 0, 0));
		verify(credentials, never()).save(any());
		verify(alert, never()).lapsed(any(), any(), anyString());
	}

	private static Credential credential(Long id, Long caregiverId, Credential.Status status, LocalDate expiry) {
		return new Credential(id, caregiverId, 11L, 7L, "CERT-" + id, null, null, expiry, status, null, null, null);
	}
}
