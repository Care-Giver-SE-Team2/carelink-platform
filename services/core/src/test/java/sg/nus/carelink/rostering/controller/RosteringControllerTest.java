package sg.nus.carelink.rostering.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.rostering.application.RosteringService;
import sg.nus.carelink.rostering.domain.model.RosteringRun;

/** HTTP surface only: status codes for found and not found. Security is tested at the filter-chain level. */
class RosteringControllerTest {

	private final RosteringService service = mock(RosteringService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RosteringController(service)).build();

	@Test
	void returns200WithTheRecord() throws Exception {
		when(service.findRosteringRun(1L)).thenReturn(Optional.of(new RosteringRun(
				1L,
				RosteringRun.TriggerType.NEW_VISIT,
				3L,
				RosteringRun.Objective.CONTINUITY,
				5L,
				RosteringRun.Status.PROPOSED,
				7,
				8,
				9,
				new BigDecimal("10.5"),
				LocalDateTime.of(2026, 9, 6, 10, 11),
				LocalDateTime.of(2026, 9, 6, 10, 12))));

		mvc.perform(get("/api/rostering-runs/1")).andExpect(status().isOk());
	}

	@Test
	void returns404WhenMissing() throws Exception {
		when(service.findRosteringRun(2L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/rostering-runs/2")).andExpect(status().isNotFound());
	}
}
