package sg.nus.carelink.profile.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.CredentialRegisterRow;
import sg.nus.carelink.profile.application.CredentialReviewService;
import sg.nus.carelink.shared.security.Role;

/** HTTP surface only: paths, status codes and body shape. Security is tested at the filter-chain level. */
class CredentialReviewControllerTest {

	private final CredentialReviewService service = mock(CredentialReviewService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CredentialReviewController(service, users)).build();
	private final TestingAuthenticationToken manager = new TestingAuthenticationToken("tan.meiling", null);

	@Test
	void listsTheRegister() throws Exception {
		when(service.register()).thenReturn(List.of(new CredentialRegisterRow(2L, 201L, "Devi Raman", 11L, "First aid",
				true, "SUBMITTED", LocalDate.of(2026, 10, 14), 12L, true, "SRC-FA-88412", "Singapore Red Cross",
				null, LocalDate.of(2028, 8, 27), LocalDateTime.of(2026, 8, 27, 21, 4), null, null, 1L,
				LocalDate.of(2026, 10, 14))));

		mvc.perform(get("/api/credentials"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].caregiverName").value("Devi Raman"))
				.andExpect(jsonPath("$[0].state").value("SUBMITTED"))
				.andExpect(jsonPath("$[0].renewal").value(true))
				.andExpect(jsonPath("$[0].daysUntilExpiry").value(12));
	}

	@Test
	void publishesAsTheSignedInManager() throws Exception {
		signedIn();
		mvc.perform(post("/api/credentials/2/publish").principal(manager)).andExpect(status().isNoContent());
		verify(service).publish(2L, 7L);
	}

	@Test
	void rejectsWithAReason() throws Exception {
		signedIn();
		mvc.perform(post("/api/credentials/2/reject").principal(manager)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Issuer is not accredited\"}"))
				.andExpect(status().isNoContent());
		verify(service).reject(2L, 7L, "Issuer is not accredited");
	}

	@Test
	void aRejectionWithoutAReasonIsABadRequest() throws Exception {
		mvc.perform(post("/api/credentials/2/reject").principal(manager)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	private void signedIn() {
		when(users.findByUsername("tan.meiling")).thenReturn(Optional.of(
				new AppUser(7L, "tan.meiling", "Tan Mei Ling", Set.of(Role.MANAGER), true)));
	}
}
