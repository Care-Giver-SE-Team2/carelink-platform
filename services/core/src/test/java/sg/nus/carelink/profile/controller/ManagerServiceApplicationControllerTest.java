package sg.nus.carelink.profile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.profile.application.ServiceApplicationDeclineService;

/** HTTP surface only. Security is tested at the filter-chain level. */
class ManagerServiceApplicationControllerTest {

	private final ServiceApplicationDeclineService declines = mock(ServiceApplicationDeclineService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ManagerServiceApplicationController(declines)).build();

	@Test
	void declinesWithTheManagersReason() throws Exception {
		mvc.perform(post("/api/service-applications/9/decline")
						.principal(new TestingAuthenticationToken("mei.ling", null))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"No exercise coach in your sector yet\"}"))
				.andExpect(status().isNoContent());

		verify(declines).decline(9L, "No exercise coach in your sector yet", "mei.ling");
	}

	@Test
	void aReasonIsRequired() throws Exception {
		mvc.perform(post("/api/service-applications/9/decline")
						.principal(new TestingAuthenticationToken("mei.ling", null))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\" \"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(declines);
	}
}
