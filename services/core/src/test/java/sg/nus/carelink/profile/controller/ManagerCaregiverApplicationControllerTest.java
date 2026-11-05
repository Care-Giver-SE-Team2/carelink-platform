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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.profile.application.CaregiverApplicationReviewService;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Certificate;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Decision;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Item;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Page;

/** HTTP surface only: paths, status codes and body shape. Security is tested at the filter-chain level. */
class ManagerCaregiverApplicationControllerTest {

	private static final String REQUEST_ID = UUID.randomUUID().toString();

	private final CaregiverApplicationReviewService service = mock(CaregiverApplicationReviewService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ManagerCaregiverApplicationController(service)).build();
	private final TestingAuthenticationToken manager = new TestingAuthenticationToken("tan.meiling", null);

	@Test
	void listsPendingApplications() throws Exception {
		when(service.pending("tan.meiling", 0, 100)).thenReturn(new Page(List.of(new Item(7L, 301L, "lim.huimin",
				"Lim Hui Min", "91234567", "Hokkien,Mandarin", "AMK", LocalDateTime.of(2026, 10, 12, 9, 0), 2, 3,
				List.of(new Certificate(41L, "First aid", "SUBMITTED", "SRC-FA-73310", "Singapore Red Cross", null,
						LocalDate.of(2028, 6, 30), "first-aid.pdf", "/api/credentials/41/documents/5/content",
						"application/pdf")))), 0, 100, 1));

		mvc.perform(get("/api/caregiver-applications").principal(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].username").value("lim.huimin"))
				.andExpect(jsonPath("$.items[0].requestedSector").value("AMK"))
				.andExpect(jsonPath("$.items[0].roundNumber").value(2))
				.andExpect(jsonPath("$.items[0].version").value(3))
				.andExpect(jsonPath("$.items[0].certificates[0].state").value("SUBMITTED"))
				.andExpect(jsonPath("$.items[0].certificates[0].scanUrl").value("/api/credentials/41/documents/5/content"));
	}

	@Test
	void approvesWithAnOptionalMessage() throws Exception {
		when(service.approve("tan.meiling", 7L, null, 3, REQUEST_ID)).thenReturn(new Decision(7L, 301L, "APPROVED"));

		mvc.perform(post("/api/caregiver-applications/7/approve").principal(manager)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedVersion\":3,\"clientRequestId\":\"" + REQUEST_ID + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caregiverId").value(301))
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void rejectsWithAReason() throws Exception {
		when(service.reject("tan.meiling", 7L, "Unreadable scan", 3, REQUEST_ID)).thenReturn(new Decision(7L, 301L, "REJECTED"));

		mvc.perform(post("/api/caregiver-applications/7/reject").principal(manager)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Unreadable scan\",\"expectedVersion\":3,\"clientRequestId\":\"" + REQUEST_ID + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));
		verify(service).reject("tan.meiling", 7L, "Unreadable scan", 3, REQUEST_ID);
	}

	@Test
	void aRejectionWithoutAReasonOrADecisionWithoutAVersionIsABadRequest() throws Exception {
		mvc.perform(post("/api/caregiver-applications/7/reject").principal(manager)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\" \",\"expectedVersion\":3,\"clientRequestId\":\"" + REQUEST_ID + "\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/caregiver-applications/7/approve").principal(manager)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"clientRequestId\":\"" + REQUEST_ID + "\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}
}
