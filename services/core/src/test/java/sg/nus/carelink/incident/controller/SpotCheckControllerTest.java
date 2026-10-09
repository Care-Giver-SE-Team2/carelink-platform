package sg.nus.carelink.incident.controller;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.SpotCheckService;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.security.Role;
import sg.nus.carelink.shared.web.GlobalExceptionHandlerTestSupport;

/** The HTTP surface of UC-MG08: who sees what, what each step takes, and the status codes. */
class SpotCheckControllerTest {

	private static final AppUser MANAGER = new AppUser(11L, "alice", "Alice Tan", Set.of(Role.MANAGER), true);
	private static final LocalDateTime FRIDAY = LocalDateTime.of(2026, 10, 9, 10, 0);

	private final SpotCheckService service = mock(SpotCheckService.class);
	private final IdentityService identity = mock(IdentityService.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new SpotCheckController(service, identity))
				.setControllerAdvice(GlobalExceptionHandlerTestSupport.instance())
				.build();
		when(identity.require(anyString())).thenReturn(MANAGER);
		when(service.view(100L)).thenReturn(view());
		SpotCheck saved = SpotCheck.requested(7L, 30L, 5L, FRIDAY, "Routine", 11L, FRIDAY.minusDays(2));
		SpotCheck withId = new SpotCheck(100L, saved.elderId(), saved.caregiverId(), saved.visitId(), saved.raisedByUserId(),
				null, saved.proposedTime(), saved.reason(), saved.approvalStatus(), null, null, null, null, null, null, null,
				null, null);
		when(service.request(30L, "Routine", 11L)).thenReturn(withId);
		when(service.decide(eq(100L), eq("alex"), eq(true), any())).thenReturn(withId);
		when(service.conclude(eq(100L), eq(SpotCheck.Result.MEETS_STANDARD), any())).thenReturn(withId);
		when(service.reportNoShow(eq(100L), isNull(), anyString())).thenReturn(withId);
		when(service.moveTo(100L, 31L)).thenReturn(withId);
		when(service.withdraw(100L, "Changed elders")).thenReturn(withId);
		when(service.respond(100L, "aisha", "Thanks")).thenReturn(withId);
	}

	@Test
	void eachRoleSeesItsOwnList() throws Exception {
		when(service.list(SpotCheck.Stage.SCHEDULED, null, 7L)).thenReturn(List.of(view()));
		when(service.forFamily("alex")).thenReturn(List.of(view(), view()));
		when(service.forCaregiver("aisha")).thenReturn(List.of());

		mvc.perform(get("/api/spot-checks").param("stage", "SCHEDULED").param("elderId", "7").with(as("alice", "MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].elderName").value("Mdm Tan"));
		mvc.perform(get("/api/spot-checks").with(as("alex", "FAMILY")))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/spot-checks").with(as("aisha", "CAREGIVER")))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aManagerAsksForACheckAndRecordsWhatHappened() throws Exception {
		mvc.perform(post("/api/spot-checks").with(as("alice", "MANAGER")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"visitId\":30,\"purpose\":\"Routine\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(100));
		mvc.perform(post("/api/spot-checks/100/conclusion").with(as("alice", "MANAGER"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"MEETS_STANDARD\"}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/spot-checks/100/no-show").with(as("alice", "MANAGER")))
				.andExpect(status().isOk());
		verify(service).reportNoShow(100L, null, "Alice Tan (alice)");
		mvc.perform(post("/api/spot-checks/100/move").with(as("alice", "MANAGER"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"visitId\":31}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/spot-checks/100/withdrawal").with(as("alice", "MANAGER"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed elders\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void malformedBodiesAreBadRequests() throws Exception {
		mvc.perform(post("/api/spot-checks").with(as("alice", "MANAGER")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"visitId\":30}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/spot-checks/100/conclusion").with(as("alice", "MANAGER"))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/spot-checks/100/withdrawal").with(as("alice", "MANAGER"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theFamilyAnswersAndTheCaregiverResponds() throws Exception {
		when(service.decide(eq(101L), eq("alex"), eq(false), any()))
				.thenThrow(new BusinessRuleViolation("SPOT_CHECK_REASON_REQUIRED", "Say why"));
		when(service.decide(eq(102L), eq("alex"), eq(true), any())).thenThrow(new AccessDeniedException("not bound"));

		mvc.perform(post("/api/spot-checks/100/decision").with(as("alex", "FAMILY"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/spot-checks/101/decision").with(as("alex", "FAMILY"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false}"))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/spot-checks/102/decision").with(as("alex", "FAMILY"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/spot-checks/100/response").with(as("aisha", "CAREGIVER"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"Thanks\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void visitsToChooseFromAndACaregiversConclusions() throws Exception {
		when(service.visitsToCheck(7L)).thenReturn(List.of(new SpotCheckService.VisitChoice(30L, FRIDAY, "Personal care", 5L, "Aisha")));
		when(service.conclusionsFor(5L)).thenReturn(List.of(view()));

		mvc.perform(get("/api/spot-checks/visits").param("elderId", "7").with(as("alice", "MANAGER")))
				.andExpect(jsonPath("$[0].caregiverName").value("Aisha"));
		mvc.perform(get("/api/caregivers/5/spot-checks").with(as("alice", "MANAGER")))
				.andExpect(jsonPath("$[0].id").value(100));
	}

	private static RequestPostProcessor as(String username, String role) {
		return request -> {
			request.setUserPrincipal(new UsernamePasswordAuthenticationToken(username, null,
					List.of(new SimpleGrantedAuthority("ROLE_" + role))));
			return request;
		};
	}

	private static SpotCheckService.SpotCheckView view() {
		return new SpotCheckService.SpotCheckView(100L, 7L, "Mdm Tan", 5L, "Aisha", 30L, FRIDAY, "Routine",
				SpotCheck.Stage.SCHEDULED, null, null, null, null, null, null, null);
	}
}
