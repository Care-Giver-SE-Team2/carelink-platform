package sg.nus.carelink.profile.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.profile.application.PrimaryCaregiverService;
import sg.nus.carelink.profile.application.PrimaryCaregiverSummary;
import sg.nus.carelink.profile.domain.model.Caregiver;

/** HTTP surface only: paths, status codes and body shape. Security is tested at the filter-chain level. */
class PrimaryCaregiverControllerTest {

	private final PrimaryCaregiverService service = mock(PrimaryCaregiverService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PrimaryCaregiverController(service)).build();

	@Test
	void listsCaregiversWithTheirAssignability() throws Exception {
		when(service.listCaregivers()).thenReturn(List.of(
				new Caregiver(3L, 30L, "Aisyah N.", null, "S31", null, Caregiver.Status.AVAILABLE, null, null),
				new Caregiver(4L, 40L, "New Hire", null, "S28", null, Caregiver.Status.ONBOARDING, null, null)));

		mvc.perform(get("/api/caregivers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(3))
				.andExpect(jsonPath("$[0].fullName").value("Aisyah N."))
				.andExpect(jsonPath("$[0].assignable").value(true))
				.andExpect(jsonPath("$[1].status").value("ONBOARDING"))
				.andExpect(jsonPath("$[1].assignable").value(false));
	}

	@Test
	void putAssignsAndReturnsTheCaregiver() throws Exception {
		when(service.assign(1L, 3L))
				.thenReturn(new PrimaryCaregiverSummary(3L, "Aisyah N.", LocalDateTime.of(2026, 9, 26, 10, 30)));

		mvc.perform(put("/api/elders/1/primary-caregiver")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"caregiverId\":3}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caregiverId").value(3))
				.andExpect(jsonPath("$.fullName").value("Aisyah N."));
	}

	@Test
	void putWithoutACaregiverIdIsABadRequest() throws Exception {
		mvc.perform(put("/api/elders/1/primary-caregiver")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void deleteUnassignsWithNoContent() throws Exception {
		mvc.perform(delete("/api/elders/1/primary-caregiver")).andExpect(status().isNoContent());
		verify(service).unassign(1L);
	}
}
