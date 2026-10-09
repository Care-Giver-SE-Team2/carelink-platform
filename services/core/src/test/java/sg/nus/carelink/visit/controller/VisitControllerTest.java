package sg.nus.carelink.visit.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.visit.application.VisitService;
import sg.nus.carelink.visit.application.FamilyVisitDetailService;
import sg.nus.carelink.visit.application.FamilyVisitTimelineService;
import sg.nus.carelink.visit.application.FamilyVisitTaskService;
import sg.nus.carelink.visit.domain.model.Visit;

/** HTTP surface only: status codes for found and not found. Security is tested at the filter-chain level. */
class VisitControllerTest {

	private final VisitService service = mock(VisitService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
			new VisitController(service, mock(FamilyVisitDetailService.class), mock(FamilyVisitTimelineService.class),
					mock(FamilyVisitTaskService.class))).build();
	private final UsernamePasswordAuthenticationToken manager = new UsernamePasswordAuthenticationToken(
			"manager", null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));

	@Test
	void returns200WithTheRecord() throws Exception {
		when(service.findVisit(1L)).thenReturn(Optional.of(new Visit(
				1L,
				2L,
				3L,
				4L,
				5L,
				"v6",
				LocalDateTime.of(2026, 9, 6, 10, 7),
				LocalDateTime.of(2026, 9, 6, 10, 8),
				LocalDateTime.of(2026, 9, 6, 10, 9),
				LocalDateTime.of(2026, 9, 6, 10, 10),
				Visit.Status.SCHEDULED,
				LocalDateTime.of(2026, 9, 6, 10, 12),
				13L,
				14,
				LocalDateTime.of(2026, 9, 6, 10, 15),
				LocalDateTime.of(2026, 9, 6, 10, 16))));

		mvc.perform(get("/api/visits/1").principal(manager)).andExpect(status().isOk());
	}

	@Test
	void returns404WhenMissing() throws Exception {
		when(service.findVisit(2L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/visits/2").principal(manager)).andExpect(status().isNotFound());
	}

	@Test
	void listsTheDayRosterForTheRequestedDate() throws Exception {
		when(service.findDayRoster(LocalDate.of(2026, 9, 27))).thenReturn(List.of(new Visit(
				7L, 2L, null, null, null, "Companionship",
				LocalDateTime.of(2026, 9, 27, 11, 0), LocalDateTime.of(2026, 9, 27, 12, 0),
				null, null, Visit.Status.SCHEDULED, null, null, 0, null, null)));

		mvc.perform(get("/api/visits/roster").param("date", "2026-09-27"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(7))
				.andExpect(jsonPath("$[0].caregiverId").doesNotExist());
	}

	@Test
	void theDayRosterDateIsOptional() throws Exception {
		when(service.findDayRoster(null)).thenReturn(List.of());

		mvc.perform(get("/api/visits/roster")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}
}
