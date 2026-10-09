package sg.nus.carelink.rostering.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.rostering.application.AbsenceQueryService;
import sg.nus.carelink.rostering.application.AbsenceReRosteringService;
import sg.nus.carelink.rostering.application.AbsenceService;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;
import sg.nus.carelink.shared.web.GlobalExceptionHandlerTestSupport;

/** The HTTP surface of UC-MG04 for the manager and of UC-CG02 for the caregiver: paths, bodies, status codes. */
class AbsenceControllerTest {

	private static final AppUser MANAGER = new AppUser(11L, "alice", "Alice Tan", Set.of(Role.MANAGER), true);
	private static final LocalDate DAY = LocalDate.of(2026, 10, 8);

	private final AbsenceService absences = mock(AbsenceService.class);
	private final AbsenceReRosteringService reRostering = mock(AbsenceReRosteringService.class);
	private final AbsenceQueryService queries = mock(AbsenceQueryService.class);
	private final IdentityService identity = mock(IdentityService.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new AbsenceController(absences, reRostering, queries, identity))
				.setControllerAdvice(GlobalExceptionHandlerTestSupport.instance())
				.build();
		when(identity.require(anyString())).thenReturn(MANAGER);
		when(queries.caseOf(1L)).thenReturn(new AbsenceQueryService.AbsenceCase(summary(), List.of(), List.of()));
	}

	@Test
	void listsAbsencesWithAnOptionalStatus() throws Exception {
		when(queries.list(AbsenceReport.Status.APPROVED)).thenReturn(List.of(summary()));

		mvc.perform(get("/api/absences").param("status", "APPROVED").with(as("alice")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].caregiverName").value("Aisha"))
				.andExpect(jsonPath("$[0].notYetRerostered").value(3));
	}

	@Test
	void recordingAnAbsenceReturns201() throws Exception {
		when(absences.recordForCaregiver(eq(5L), eq(AbsenceReport.Type.SICK), eq(DAY), eq(DAY.plusDays(1)), eq("flu"),
				eq(11L))).thenReturn(absence());

		mvc.perform(post("/api/absences").with(as("alice")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"caregiverId\":5,\"type\":\"SICK\",\"startDate\":\"2026-10-08\","
								+ "\"endDate\":\"2026-10-09\",\"reason\":\"flu\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1));
	}

	@Test
	void anAbsenceWithoutDatesIsABadRequest() throws Exception {
		mvc.perform(post("/api/absences").with(as("alice")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"caregiverId\":5}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void showsOneAbsenceAnd404sAMissingOne() throws Exception {
		when(queries.caseOf(404L)).thenThrow(new ResourceNotFound("Absence", 404L));

		mvc.perform(get("/api/absences/1").with(as("alice"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.absence.status").value("APPROVED"));
		mvc.perform(get("/api/absences/404").with(as("alice"))).andExpect(status().isNotFound());
	}

	@Test
	void approvingAndRejectingReturnTheAbsence() throws Exception {
		when(absences.reject(1L, 11L)).thenThrow(new BusinessRuleViolation("ABSENCE_ALREADY_REVIEWED", "reviewed"));

		mvc.perform(post("/api/absences/1/approve").with(as("alice"))).andExpect(status().isOk());
		verify(absences).approve(1L, 11L);
		mvc.perform(post("/api/absences/1/reject").with(as("alice"))).andExpect(status().isConflict());
	}

	@Test
	void reRosteringReturnsWhatItDidAndTheAbsence() throws Exception {
		when(reRostering.reroster(1L, RosteringRun.Objective.EVEN_WORKLOAD, 11L))
				.thenReturn(new AbsenceReRosteringService.ReRosterOutcome(1L, 7L, 3, 2, 1, 0));
		when(reRostering.reroster(eq(1L), isNull(), eq(11L)))
				.thenReturn(new AbsenceReRosteringService.ReRosterOutcome(1L, null, 0, 0, 0, 0));

		mvc.perform(post("/api/absences/1/rerostering-runs").with(as("alice")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"objective\":\"EVEN_WORKLOAD\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.outcome.offered").value(2))
				.andExpect(jsonPath("$.absence.absence.id").value(1));
		mvc.perform(post("/api/absences/1/rerostering-runs").with(as("alice")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.outcome.searched").value(0));
	}

	@Test
	void confirmingCoverageWhileAFamilyDecidesIsAConflict() throws Exception {
		when(reRostering.confirmCoverage(2L, 11L))
				.thenThrow(new BusinessRuleViolation("FAMILY_STILL_DECIDING", "1 visit is still waiting"));

		mvc.perform(post("/api/absences/1/coverage-confirmation").with(as("alice"))).andExpect(status().isOk());
		mvc.perform(post("/api/absences/2/coverage-confirmation").with(as("alice"))).andExpect(status().isConflict());
	}

	@Test
	void aManagersHandPickReturnsTheAbsenceOrSaysWhyNot() throws Exception {
		when(reRostering.assignByManager(1L, 100L, 10L, 11L)).thenReturn(null);
		when(reRostering.assignByManager(1L, 100L, 5L, 11L))
				.thenThrow(new BusinessRuleViolation("CAREGIVER_CANNOT_TAKE_VISIT", "Aisha cannot take this visit: on leave"));

		mvc.perform(post("/api/absences/1/changes/100/assignment").with(as("alice"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"caregiverId\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.absence.id").value(1));
		verify(reRostering).assignByManager(1L, 100L, 10L, 11L);
		mvc.perform(post("/api/absences/1/changes/100/assignment").with(as("alice"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"caregiverId\":5}"))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/absences/1/changes/100/assignment").with(as("alice"))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aCaregiverAsksForLeaveAndSeesTheirOwn() throws Exception {
		AbsenceReport asked = new AbsenceReport(3L, 5L, null, AbsenceReport.Type.ANNUAL, DAY, DAY, "trip",
				AbsenceReport.Status.PENDING, null, null, null, null);
		when(absences.requestForSelf(eq("aisha"), eq(AbsenceReport.Type.ANNUAL), eq(DAY), eq(DAY), any()))
				.thenReturn(asked);
		when(absences.listForSelf("aisha")).thenReturn(List.of(asked));

		mvc.perform(post("/api/caregivers/me/absences").with(as("aisha")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"ANNUAL\",\"startDate\":\"2026-10-08\",\"endDate\":\"2026-10-08\",\"reason\":\"trip\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));
		mvc.perform(get("/api/caregivers/me/absences").with(as("aisha")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(3));
	}

	private static RequestPostProcessor as(String username) {
		return request -> {
			request.setUserPrincipal(() -> username);
			return request;
		};
	}

	private static AbsenceReport absence() {
		return new AbsenceReport(1L, 5L, 11L, AbsenceReport.Type.SICK, DAY, DAY.plusDays(1), "flu",
				AbsenceReport.Status.APPROVED, null, null, null, null);
	}

	private static AbsenceQueryService.AbsenceSummary summary() {
		return new AbsenceQueryService.AbsenceSummary(1L, 5L, "Aisha", AbsenceReport.Type.SICK, DAY, DAY.plusDays(1),
				"flu", AbsenceReport.Status.APPROVED, 11L, null, 3, 0, 0, 0);
	}
}
