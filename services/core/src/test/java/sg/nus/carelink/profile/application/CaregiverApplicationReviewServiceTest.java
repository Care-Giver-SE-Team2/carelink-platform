package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.CaregiverApplication;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.Details;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.Status;

class CaregiverApplicationReviewServiceTest {

	private static final String MANAGER = "tan.meiling";
	private static final LocalDateTime SUBMITTED = LocalDateTime.of(2026, 10, 12, 9, 0);

	private final CaregiverApplicationReader reader = mock(CaregiverApplicationReader.class);
	private final CaregiverAdmissionCommands commands = mock(CaregiverAdmissionCommands.class);
	private final CredentialReviewService register = mock(CredentialReviewService.class);
	private final CaregiverCredentialService credentials = mock(CaregiverCredentialService.class);
	private final CaregiverApplicationReviewService service =
			new CaregiverApplicationReviewService(reader, commands, register, credentials);

	@Test
	void listsEachPendingApplicationWithItsLoginVersionAndCertificates() {
		var application = application(7L, 301L, Status.PENDING);
		when(reader.pendingApplications(MANAGER, 0, 100))
				.thenReturn(new CaregiverApplicationReader.Page(List.of(application), 0, 100, 1));
		when(reader.applicationForManager(MANAGER, 7L)).thenReturn(view(application, 3));
		when(register.register()).thenReturn(List.of(
				row(41L, 301L, "First aid", "SUBMITTED"),
				row(42L, 301L, "Dementia care", "REJECTED"),
				row(43L, 999L, "Manual handling", "PUBLISHED")));
		when(credentials.documents(MANAGER, 41L)).thenReturn(List.of(new CaregiverCredentialService.DocumentView(
				5L, 41L, "first-aid.pdf", "application/pdf", 1024, SUBMITTED, true,
				"/api/credentials/41/documents/5/content")));

		var page = service.pending(MANAGER, 0, 100);

		assertThat(page.total()).isEqualTo(1);
		var item = page.items().getFirst();
		assertThat(item.id()).isEqualTo(7L);
		assertThat(item.caregiverId()).isEqualTo(301L);
		assertThat(item.username()).isEqualTo("lim.huimin");
		assertThat(item.fullName()).isEqualTo("Lim Hui Min");
		assertThat(item.requestedSector()).isEqualTo("AMK");
		assertThat(item.version()).isEqualTo(3);
		// Only this applicant's certificates, without the rejected one.
		assertThat(item.certificates()).singleElement().satisfies(cert -> {
			assertThat(cert.credentialTypeName()).isEqualTo("First aid");
			assertThat(cert.state()).isEqualTo("SUBMITTED");
			assertThat(cert.fileName()).isEqualTo("first-aid.pdf");
			assertThat(cert.scanUrl()).isEqualTo("/api/credentials/41/documents/5/content");
			assertThat(cert.scanContentType()).isEqualTo("application/pdf");
		});
	}

	@Test
	void aScanWhoseFileIsMissingHasNoUrl() {
		var application = application(7L, 301L, Status.PENDING);
		when(reader.applicationForManager(MANAGER, 7L)).thenReturn(view(application, 0));
		when(register.register()).thenReturn(List.of(row(41L, 301L, "First aid", "SUBMITTED")));
		when(credentials.documents(MANAGER, 41L)).thenReturn(List.of(new CaregiverCredentialService.DocumentView(
				5L, 41L, "first-aid.pdf", "application/pdf", 1024, SUBMITTED, false,
				"/api/credentials/41/documents/5/content")));

		var cert = service.one(MANAGER, 7L).certificates().getFirst();

		assertThat(cert.fileName()).isEqualTo("first-aid.pdf");
		assertThat(cert.scanUrl()).isNull();
		assertThat(cert.scanContentType()).isNull();
	}

	@Test
	void approvesThroughTheAdmissionCommandOnly() {
		when(commands.decide(MANAGER, 7L, true, "Welcome", 3, "key"))
				.thenReturn(view(application(7L, 301L, Status.APPROVED), 4));

		var decision = service.approve(MANAGER, 7L, "Welcome", 3, "key");

		assertThat(decision).isEqualTo(new CaregiverApplicationReviewService.Decision(7L, 301L, "APPROVED"));
	}

	@Test
	void rejectsWithTheReason() {
		when(commands.decide(MANAGER, 7L, false, "Unreadable scan", 3, "key"))
				.thenReturn(view(application(7L, 301L, Status.REJECTED), 4));

		var decision = service.reject(MANAGER, 7L, "Unreadable scan", 3, "key");

		verify(commands).decide(MANAGER, 7L, false, "Unreadable scan", 3, "key");
		assertThat(decision.status()).isEqualTo("REJECTED");
	}

	private static CaregiverApplication application(Long id, Long caregiverId, Status status) {
		return new CaregiverApplication(id, caregiverId, status, 1,
				new Details("Lim Hui Min", "91234567", "Hokkien,Mandarin", "AMK"), SUBMITTED, null, null, null);
	}

	private static CaregiverApplication.View view(CaregiverApplication application, long version) {
		return new CaregiverApplication.View(application.caregiverId(), 501L, "lim.huimin", "ONBOARDING", "91234567",
				version, application, List.of(), false, true, false, null);
	}

	private static CredentialRegisterRow row(Long id, Long caregiverId, String type, String state) {
		return new CredentialRegisterRow(id, caregiverId, "Lim Hui Min", 11L, type, false, state, null, null, false,
				"SRC-FA-73310", "Singapore Red Cross", null, LocalDate.of(2028, 6, 30), SUBMITTED, null, null, null,
				null);
	}
}
