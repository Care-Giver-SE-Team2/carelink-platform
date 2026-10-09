package sg.nus.carelink.rostering.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.rostering.application.AbsenceQueryService;
import sg.nus.carelink.rostering.application.AbsenceReRosteringService;
import sg.nus.carelink.rostering.application.FamilyChoice;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.web.GlobalExceptionHandlerTestSupport;

/** The family's half of UC-MG04 over HTTP: their changes, and their answer. */
class RosterChangeControllerTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 8, 9, 0);

	private final AbsenceReRosteringService reRostering = mock(AbsenceReRosteringService.class);
	private final AbsenceQueryService queries = mock(AbsenceQueryService.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new RosterChangeController(reRostering, queries))
				.setControllerAdvice(GlobalExceptionHandlerTestSupport.instance())
				.build();
	}

	@Test
	void listsTheFamilysChanges() throws Exception {
		when(queries.forFamily("alex")).thenReturn(List.of(change()));

		mvc.perform(get("/api/roster-changes").with(asFamily()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].elderName").value("Mdm Tan"))
				.andExpect(jsonPath("$[0].options[0].name").value("Farah"));
	}

	@Test
	void answeringReturnsTheChangeAsItNowStands() throws Exception {
		when(queries.forFamily("alex", 100L)).thenReturn(change());

		mvc.perform(post("/api/roster-changes/100/decision").with(asFamily()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"choice\":\"RESCHEDULE\",\"newStart\":\"2026-10-10T09:00:00\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(100));
		verify(reRostering).decide(100L, "alex", FamilyChoice.moveTo(LocalDateTime.of(2026, 10, 10, 9, 0)));
	}

	@Test
	void anAnswerWithoutAChoiceIsABadRequestAndALateOneAConflict() throws Exception {
		mvc.perform(post("/api/roster-changes/100/decision").with(asFamily()).contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());

		when(reRostering.decide(eq(101L), eq("alex"), any()))
				.thenThrow(new BusinessRuleViolation("FAMILY_WINDOW_CLOSED", "too late"));
		mvc.perform(post("/api/roster-changes/101/decision").with(asFamily()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"choice\":\"SKIP\"}"))
				.andExpect(status().isConflict());

		when(reRostering.decide(eq(102L), eq("alex"), any())).thenThrow(new AccessDeniedException("not bound"));
		mvc.perform(post("/api/roster-changes/102/decision").with(asFamily()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"choice\":\"CHANGE_CAREGIVER\",\"caregiverId\":10}"))
				.andExpect(status().isForbidden());
	}

	private static RequestPostProcessor asFamily() {
		return request -> {
			request.setUserPrincipal(() -> "alex");
			return request;
		};
	}

	private static AbsenceQueryService.FamilyChange change() {
		return new AbsenceQueryService.FamilyChange(100L, 7L, "Mdm Tan", NINE, NINE.plusMinutes(45), "Aisha",
				RosterChange.Status.AWAITING_FAMILY, NINE.minusHours(22), 9L,
				List.of(new AbsenceQueryService.FamilyOption(9L, "Farah", 1, "Has visited this elder 3 times before")),
				null, null, null, null, null, null);
	}
}
