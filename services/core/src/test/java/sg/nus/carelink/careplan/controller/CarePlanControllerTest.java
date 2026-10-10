package sg.nus.carelink.careplan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.careplan.application.CarePlanService;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduledVisit;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.shared.security.Role;

/** HTTP surface only: status codes for found and not found. Security is tested at the filter-chain level. */
class CarePlanControllerTest {

	private final CarePlanService service = mock(CarePlanService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CarePlanController(service, users)).build();

	@Test
	void returns200WithTheRecord() throws Exception {
		when(service.findCarePlan(1L)).thenReturn(Optional.of(new CarePlan(
				1L,
				2L,
				3L,
				4L,
				5,
				CarePlan.Status.DRAFT,
				new BigDecimal("7.5"),
				LocalDateTime.of(2026, 9, 6, 10, 8),
				LocalDateTime.of(2026, 9, 6, 10, 9),
				LocalDateTime.of(2026, 9, 6, 10, 10))));

		mvc.perform(get("/api/care-plans/1")).andExpect(status().isOk());
	}

	@Test
	void returns404WhenMissing() throws Exception {
		when(service.findCarePlan(2L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/care-plans/2")).andExpect(status().isNotFound());
	}

	@Test
	void returns201WhenDraftIsCreated() throws Exception {
		AppUser actingUser = new AppUser(7L, "mei.ling", "Tan Mei Ling", Set.of(Role.MANAGER), true);
		when(users.findByUsername("mei.ling")).thenReturn(Optional.of(actingUser));
		when(service.createDraft(42L, 7L)).thenReturn(new CarePlan(
				1L, 42L, 7L, null, 1, CarePlan.Status.DRAFT, null, null, null, null));

		mvc.perform(post("/api/care-plans")
						.principal(new UsernamePasswordAuthenticationToken("mei.ling", null))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"elderId\":42}"))
				.andExpect(status().isCreated());
	}

	@Test
	void returns200WithTheLatestPlanForAnElder() throws Exception {
		when(service.findLatestByElderId(42L)).thenReturn(Optional.of(new CarePlan(
				1L, 42L, 7L, null, 1, CarePlan.Status.DRAFT, null, null, null, null)));

		mvc.perform(get("/api/care-plans/latest").param("elderId", "42")).andExpect(status().isOk());
	}

	@Test
	void returns404WhenThereIsNoLatestPlanForAnElder() throws Exception {
		when(service.findLatestByElderId(42L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/care-plans/latest").param("elderId", "42")).andExpect(status().isNotFound());
	}

	@Test
	void returns200WithThePlanNodes() throws Exception {
		when(service.findNodes(1L)).thenReturn(List.of(new CarePlanNode(
				5L, 1L, "Personal care", "BATHING", "Bathing", "MON,WED", null, null,
				CarePlanNode.EvidenceType.CHECKLIST, 1, null, null,
				List.of(new ScheduledVisit(DayOfWeek.WEDNESDAY, LocalTime.of(16, 30), 45),
						new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30)))));

		mvc.perform(get("/api/care-plans/1/nodes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].visits[0].day").value("Mon"))
				.andExpect(jsonPath("$[0].visits[0].startTime").value("08:00:00"))
				.andExpect(jsonPath("$[0].visits[0].minutes").value(30))
				.andExpect(jsonPath("$[0].visits[1].day").value("Wed"))
				.andExpect(jsonPath("$[0].visits[1].startTime").value("16:30:00"));
	}

	@Test
	void returns200WhenThePlanIsPublished() throws Exception {
		when(service.publish(eq(1L), any(), any())).thenReturn(new CarePlan(
				1L, 42L, 7L, null, 1, CarePlan.Status.PUBLISHED, null, null, null, null));

		mvc.perform(post("/api/care-plans/1/publish")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2026-04-01","nodes":[{"groupName":"Personal care","name":"Bathing",
								"visits":[{"day":"Mon","startTime":"08:00","minutes":30}],"evidenceType":"CHECKLIST"}]}
								"""))
				.andExpect(status().isOk());
	}

	@Test
	void savesADraftWithAnUnscheduledCatalogTask() throws Exception {
		when(service.saveDraft(eq(1L), any(), any())).thenReturn(new CarePlan(
				1L, 42L, 7L, null, 1, CarePlan.Status.DRAFT, null, null, null, null));

		mvc.perform(put("/api/care-plans/1/draft")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":null,"nodes":[{"groupName":"Personal care","activityCode":"BATHING",
								"name":"Bathing assistance","visits":[],"evidenceType":"CHECKLIST"}]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"));

		verify(service).saveDraft(eq(1L), eq(null), eq(List.of(new sg.nus.carelink.careplan.application.PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance", List.of(), CarePlanNode.EvidenceType.CHECKLIST))));
	}

	@Test
	void returns204WhenADraftIsDiscarded() throws Exception {
		mvc.perform(delete("/api/care-plans/1")).andExpect(status().isNoContent());

		verify(service).discardDraft(1L);
	}

	@Test
	void returns200WhenThePlanIsStopped() throws Exception {
		AppUser actingUser = new AppUser(7L, "mei.ling", "Tan Mei Ling", Set.of(Role.MANAGER), true);
		when(users.findByUsername("mei.ling")).thenReturn(Optional.of(actingUser));
		when(service.stop(eq(1L), any(), eq("No longer needed"), eq(7L))).thenReturn(new CarePlan(
				1L, 42L, 7L, null, 1, CarePlan.Status.STOPPED, null, null, null, null));

		mvc.perform(post("/api/care-plans/1/stop")
						.principal(new UsernamePasswordAuthenticationToken("mei.ling", null))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"effectiveDate\":\"2026-09-20\",\"reason\":\"No longer needed\"}"))
				.andExpect(status().isOk());
	}
}
