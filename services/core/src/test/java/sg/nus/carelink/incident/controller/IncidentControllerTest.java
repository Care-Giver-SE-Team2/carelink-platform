package sg.nus.carelink.incident.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.domain.model.ContactAttempt;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.domain.model.Playbook;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;
import sg.nus.carelink.shared.web.GlobalExceptionHandlerTestSupport;

/**
 * The HTTP surface of UC-MG05: paths, bodies and status codes.
 *
 * <p>Standalone MockMvc, so this is about the controller and nothing else. The rules are
 * tested in {@code IncidentServiceTest}; what matters here is that a broken rule leaves as
 * 409, a forbidden close as 403, a missing incident as 404 and a malformed body as 400.
 */
class IncidentControllerTest {

	private static final AppUser MANAGER =
			new AppUser(11L, "alice", "Alice Tan", Set.of(Role.MANAGER), true);

	private final IncidentService service = mock(IncidentService.class);
	private final IdentityService identity = mock(IdentityService.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders
				.standaloneSetup(new IncidentController(service, identity))
				.setControllerAdvice(GlobalExceptionHandlerTestSupport.instance())
				.build();
		when(identity.require(anyString())).thenReturn(MANAGER);
	}

	private static RequestPostProcessor asManager() {
		return request -> {
			request.setUserPrincipal(() -> "alice");
			return request;
		};
	}

	@Test
	void returnsTheIncidentWithItsTimeline() throws Exception {
		Incident incident = IncidentFixtures.savedSos(1L);
		when(service.findIncident(1L)).thenReturn(Optional.of(incident));
		when(service.timelineOf(1L)).thenReturn(List.of());

		mvc.perform(get("/api/incidents/1").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.incident.id").value(1))
				.andExpect(jsonPath("$.suggestedPlaybookCode").value("PB-SOS"));
	}

	@Test
	void returns404WhenTheIncidentDoesNotExist() throws Exception {
		when(service.findIncident(404L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/incidents/404").with(asManager()))
				.andExpect(status().isNotFound());
	}

	@Test
	void takingOverAnIncidentSomebodyElseHasIsAConflict() throws Exception {
		when(service.claim(eq(1L), anyLong(), anyString()))
				.thenThrow(new BusinessRuleViolation("INCIDENT_ALREADY_CLAIMED", "already taken over"));

		mvc.perform(post("/api/incidents/1/claim").with(asManager()))
				.andExpect(status().isConflict());
	}

	@Test
	void takingOverSucceedsWith200() throws Exception {
		when(service.claim(eq(1L), anyLong(), anyString())).thenReturn(IncidentFixtures.savedSos(1L));

		mvc.perform(post("/api/incidents/1/claim").with(asManager()))
				.andExpect(status().isOk());
	}

	@Test
	void escalatingWithoutABodyIsAllowed() throws Exception {
		when(service.escalate(eq(1L), any(), anyString())).thenReturn(IncidentFixtures.savedSos(1L));

		mvc.perform(post("/api/incidents/1/escalate").with(asManager()))
				.andExpect(status().isOk());

		verify(service).escalate(eq(1L), any(), anyString());
	}

	@Test
	void recordingAContactAttemptReturns201AndTheFallbackPlaybook() throws Exception {
		when(service.recordContactAttempt(eq(1L), any(), anyString())).thenReturn(
				new IncidentService.ContactOutcome(
						IncidentFixtures.savedSos(1L),
						new ContactAttempt(ContactAttempt.Channel.PHONE, ContactAttempt.Outcome.NOT_REACHED, "no answer"),
						Playbook.SOS_IMMEDIATE));

		mvc.perform(post("/api/incidents/1/contact-attempts")
						.with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"channel\":\"PHONE\",\"outcome\":\"NOT_REACHED\",\"note\":\"no answer\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reachedTheFamily").value(false))
				.andExpect(jsonPath("$.fallbackPlaybook.code").value("PB-SOS"));
	}

	@Test
	void aContactAttemptWithoutAChannelIsABadRequest() throws Exception {
		mvc.perform(post("/api/incidents/1/contact-attempts")
						.with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"outcome\":\"REACHED\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void resolvingWithoutANoteIsABadRequest() throws Exception {
		mvc.perform(post("/api/incidents/1/resolve")
						.with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"outcome\":\"FALSE_ALARM\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void resolvingAnIncidentSomebodyElseIsHandlingIsForbidden() throws Exception {
		when(service.resolve(eq(1L), anyLong(), anyString(), any(), anyString()))
				.thenThrow(new AccessDeniedException("not yours"));

		mvc.perform(post("/api/incidents/1/resolve")
						.with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"resolutionNote\":\"all fine\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void applyingAnUnknownPlaybookIs404() throws Exception {
		when(service.applyPlaybook(eq(1L), anyString(), anyString()))
				.thenThrow(new ResourceNotFound("Playbook", "PB-NOPE"));

		mvc.perform(post("/api/incidents/1/playbook")
						.with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playbookCode\":\"PB-NOPE\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void changingSeverityWithoutOneIsABadRequest() throws Exception {
		mvc.perform(post("/api/incidents/1/severity")
						.with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"worse\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listsThePlaybooks() throws Exception {
		when(service.playbooks()).thenReturn(List.of(Playbook.values()));

		mvc.perform(get("/api/incident-playbooks").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("PB-SOS"));
	}

	/**
	 * The screen this endpoint exists for is opened by a manager who has not chosen
	 * anything yet. It used to answer that request with 400 because elderId was required,
	 * which left the queue impossible to build from the published contract.
	 */
	@Test
	void theQueueAnswersARequestWithNoParametersAtAll() throws Exception {
		when(service.queue(null, null, null, 0, 20))
				.thenReturn(new PageSlice<>(List.of(IncidentFixtures.savedSos(1L)), 0, 20, 1));

		mvc.perform(get("/api/incidents").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void theQueuePassesEveryFilterItWasGivenThrough() throws Exception {
		when(service.queue(Incident.Status.OPEN, Incident.Severity.HIGH, 7L, 2, 5))
				.thenReturn(PageSlice.empty(2, 5));

		mvc.perform(get("/api/incidents")
						.param("page", "2")
						.param("size", "5")
						.param("status", "OPEN")
						.param("severity", "HIGH")
						.param("elderId", "7")
						.with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(service).queue(Incident.Status.OPEN, Incident.Severity.HIGH, 7L, 2, 5);
	}

	/**
	 * A status that is not one of the five is the caller's mistake, not the server's:
	 * naming the parameter is what tells whoever is debugging the front end where to look.
	 */
	@Test
	void namesTheQueryParameterTheCallerSpeltWrongInsteadOfFailing() throws Exception {
		mvc.perform(get("/api/incidents").param("status", "NOT_A_STATUS").with(asManager()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.fields.status").value("Invalid value"));
	}
}
