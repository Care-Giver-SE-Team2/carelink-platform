package sg.nus.carelink.profile.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import sg.nus.carelink.profile.application.IntakeApproval;
import sg.nus.carelink.profile.application.IntakeReviewRow;
import sg.nus.carelink.profile.application.IntakeReviewService;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.service.IntakeScreening.Check;
import sg.nus.carelink.profile.domain.service.IntakeScreening.CheckKey;
import sg.nus.carelink.shared.security.Role;

/** HTTP surface only: paths, status codes and body shape. Security is tested at the filter-chain level. */
class IntakeReviewControllerTest {

	private final IntakeReviewService service = mock(IntakeReviewService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new IntakeReviewController(service, users)).build();
	private final TestingAuthenticationToken manager = new TestingAuthenticationToken("tan.meiling", null);

	@Test
	void listsPendingApplicationsWithApplicantSectorAndLowerCaseCheckKeys() throws Exception {
		when(service.pending()).thenReturn(List.of(new IntakeReviewRow(application(IntakeApplication.Status.SUBMITTED, null),
				new IntakeReviewRow.Applicant("Kevin Goh", "kevin.goh", null), "S31",
				List.of(new Check(CheckKey.SECTOR, true, 4), new Check(CheckKey.DIALECT, false, 0)))));

		mvc.perform(get("/api/intake-reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(40))
				.andExpect(jsonPath("$[0].applicant.fullName").value("Kevin Goh"))
				.andExpect(jsonPath("$[0].applicant.phone").doesNotExist())
				.andExpect(jsonPath("$[0].targetElderName").value("Goh Bee Lian"))
				.andExpect(jsonPath("$[0].sector").value("S31"))
				.andExpect(jsonPath("$[0].createdAt").value("2026-10-04T05:13:00Z"))
				.andExpect(jsonPath("$[0].checks[0].key").value("sector"))
				.andExpect(jsonPath("$[0].checks[0].count").value(4))
				.andExpect(jsonPath("$[0].checks[1].key").value("dialect"))
				.andExpect(jsonPath("$[0].checks[1].pass").value(false));
	}

	@Test
	void approvesWithOrWithoutAMessageAndReturnsTheElderLoginUncached() throws Exception {
		signedIn();
		IntakeApproval approval = new IntakeApproval(application(IntakeApplication.Status.APPROVED, 900L),
				"goh.bee.lian", "Kq7mT4xPa2");
		when(service.approve(40L, 7L, "Welcome")).thenReturn(approval);
		when(service.approve(40L, 7L, null)).thenReturn(approval);

		mvc.perform(post("/api/intake-reviews/40/approve").principal(manager)
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Welcome\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.elderId").value(900))
				.andExpect(jsonPath("$.elderLogin.username").value("goh.bee.lian"))
				.andExpect(jsonPath("$.elderLogin.temporaryPassword").value("Kq7mT4xPa2"));
		mvc.perform(post("/api/intake-reviews/40/approve").principal(manager)).andExpect(status().isOk());

		verify(service).approve(40L, 7L, "Welcome");
		verify(service).approve(40L, 7L, null);
	}

	@Test
	void declinesWithTheMessage() throws Exception {
		signedIn();
		when(service.decline(40L, 7L, "Outside our area")).thenReturn(application(IntakeApplication.Status.REJECTED, null));

		mvc.perform(post("/api/intake-reviews/40/decline").principal(manager)
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Outside our area\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.elderLogin").doesNotExist());
	}

	@Test
	void aMessageLongerThanTheColumnIsABadRequest() throws Exception {
		mvc.perform(post("/api/intake-reviews/40/decline").principal(manager)
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + "x".repeat(256) + "\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	private void signedIn() {
		when(users.findByUsername("tan.meiling")).thenReturn(Optional.of(
				new AppUser(7L, "tan.meiling", "Tan Mei Ling", Set.of(Role.MANAGER), true)));
	}

	private static IntakeApplication application(IntakeApplication.Status status, Long elderId) {
		return new IntakeApplication(40L, 15L, "Goh Bee Lian", null, "Blk 154 Bishan St 11", "570154",
				IntakeApplication.MobilityLevel.WHEELCHAIR_BEDBOUND, null, List.of("VITALS"), null, status, null, null,
				LocalDateTime.of(2026, 10, 4, 5, 13), null, elderId);
	}
}
