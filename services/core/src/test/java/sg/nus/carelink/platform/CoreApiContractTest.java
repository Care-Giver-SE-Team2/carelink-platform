package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.client.MockMvcClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import sg.nus.carelink.careplan.application.VisitPlanReader;
import sg.nus.carelink.careplan.controller.InternalCarePlanController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreApiClients;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;
import sg.nus.carelink.incident.application.CaregiverIncidentGateway;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.application.MissedCheckInIncidentGateway;
import sg.nus.carelink.incident.controller.InternalIncidentController;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.profile.application.CaregiverPublicCredential;
import sg.nus.carelink.profile.application.CaregiverPublicProfile;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyAlertRecipients;
import sg.nus.carelink.profile.application.PrimaryCaregiverLookup;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.controller.InternalProfileController;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.rostering.application.VisitCover;
import sg.nus.carelink.rostering.controller.InternalVisitCoverController;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Both sides of core's internal API in one test. The client every other service uses (core-api's
 * {@link CoreApi}) calls core's internal controllers through MockMvc, so a path, a parameter or a
 * field that differs between the two fails here instead of in the cluster. The application services
 * behind the controllers are mocks: each test checks that a call reaches them with the caller's
 * values, and that their answer, or their error, comes back the way the caller expects.
 */
@WebMvcTest(controllers = {InternalProfileController.class, InternalCarePlanController.class,
		InternalIncidentController.class, InternalVisitCoverController.class})
@Import(InternalApiSecurity.class)
class CoreApiContractTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private FamilyAccessQuery familyAccess;

	@MockitoBean
	private CaregiverDirectory caregivers;

	@MockitoBean
	private CaregiverWorkDirectory caregiverWork;

	@MockitoBean
	private ProfileService profiles;

	@MockitoBean
	private FamilyMemberRepository familyMembers;

	@MockitoBean
	private FamilyAlertRecipients alertRecipients;

	@MockitoBean
	private PrimaryCaregiverLookup primaryCaregivers;

	@MockitoBean
	private VisitPlanReader plans;

	@MockitoBean
	private CaregiverIncidentGateway caregiverIncidents;

	@MockitoBean
	private MissedCheckInIncidentGateway missedCheckIns;

	@MockitoBean
	private IncidentService incidents;

	@MockitoBean
	private VisitCover visitCover;

	private CoreApi core;

	@BeforeEach
	void client() {
		core = CoreApiClients.create(RestClient.builder()
				.requestFactory(new MockMvcClientHttpRequestFactory(mvc))
				.baseUrl("http://core"));
	}

	@Test
	void aFamilysEldersAndItsAccessToOne() {
		when(familyAccess.readableElderIds("tan.family")).thenReturn(Set.of(3L, 5L));

		assertThat(core.readableElders("tan.family").elderIds()).containsExactlyInAnyOrder(3L, 5L);

		core.checkElderAccess("tan.family", 3L, CoreApi.Access.READ);
		verify(familyAccess).requireReadableElder("tan.family", 3L);
		core.checkElderAccess("tan.family", 3L, CoreApi.Access.WRITE);
		verify(familyAccess).requireWritableElder("tan.family", 3L);
	}

	@Test
	void aFamilyCoreRefusesIsRefusedByTheCallerToo() {
		doThrow(new AccessDeniedException("A writable elder binding is required"))
				.when(familyAccess).requireWritableElder("tan.family", 5L);

		assertThatThrownBy(() -> core.checkElderAccess("tan.family", 5L, CoreApi.Access.WRITE))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void caregiversAsTheOtherServicesSeeThem() {
		when(caregivers.findPublicProfile(7L))
				.thenReturn(Optional.of(new CaregiverPublicProfile(7L, "Siti Rahman", List.of("Malay", "Hokkien"))));
		when(caregivers.findPublicProfile(8L)).thenReturn(Optional.empty());
		when(caregivers.listPublicCredentials(7L)).thenReturn(List.of(new CaregiverPublicCredential(31L, 7L, 2L,
				"First Aid", "Singapore Red Cross", LocalDate.of(2025, 1, 6), LocalDate.of(2027, 1, 5), "VERIFIED")));
		when(caregiverWork.require("siti")).thenReturn(new CaregiverWorkDirectory.Profile(7L, 70L, "Siti Rahman",
				"91234567", "North", "Malay, Hokkien", "ACTIVE"));

		assertThat(core.findCaregiverPublicProfile(7L))
				.contains(new CoreApi.CaregiverPublicProfile(7L, "Siti Rahman", List.of("Malay", "Hokkien")));
		assertThat(core.findCaregiverPublicProfile(8L)).isEmpty();
		assertThat(core.caregiverPublicCredentials(7L)).containsExactly(new CoreApi.CaregiverPublicCredential(31L, 7L,
				2L, "First Aid", "Singapore Red Cross", LocalDate.of(2025, 1, 6), LocalDate.of(2027, 1, 5), "VERIFIED"));
		assertThat(core.caregiverByUsername("siti")).isEqualTo(new CoreApi.CaregiverProfile(7L, 70L, "Siti Rahman",
				"91234567", "North", "Malay, Hokkien", "ACTIVE"));
	}

	@Test
	void aCaregiversCredentialAlertsKeepTheirDates() {
		LocalDate today = LocalDate.of(2026, 10, 10);
		when(caregiverWork.alerts(7L, today)).thenReturn(new CaregiverWorkDirectory.CredentialAlerts(
				List.of(new CaregiverWorkDirectory.CredentialAlert(31L, "First Aid", "FA-2291", LocalDate.of(2026, 11, 1),
						"VERIFIED", "EXPIRING_SOON", 22, "SUBMITTED", LocalDate.of(2026, 11, 2))),
				new CaregiverWorkDirectory.CredentialAlertContext(today, 30, true)));

		CoreApi.CredentialAlerts alerts = core.credentialAlerts(7L, today);

		assertThat(alerts.items()).containsExactly(new CoreApi.CredentialAlert(31L, "First Aid", "FA-2291",
				LocalDate.of(2026, 11, 1), "VERIFIED", "EXPIRING_SOON", 22, "SUBMITTED", LocalDate.of(2026, 11, 2)));
		assertThat(alerts.context()).isEqualTo(new CoreApi.CredentialAlertContext(today, 30, true));
	}

	@Test
	void anEldersDetailsForTheCaregiverOnTheVisit() {
		when(caregiverWork.elder(3L)).thenReturn(new CaregiverWorkDirectory.ElderView(3L, "Mdm Tan",
				"Blk 123 Ang Mo Kio Ave 3", "56", List.of("Hokkien"), "Gate code 4521", "Daughter 98765432"));

		assertThat(core.elderCaregiverView(3L)).isEqualTo(new CoreApi.ElderView(3L, "Mdm Tan",
				"Blk 123 Ang Mo Kio Ave 3", "56", List.of("Hokkien"), "Gate code 4521", "Daughter 98765432"));
	}

	@Test
	void anAccountsElderOrFamilyProfile() {
		Elder elder = mock(Elder.class);
		when(elder.id()).thenReturn(3L);
		when(profiles.requireElderByUserId(30L)).thenReturn(elder);
		when(profiles.requireElderByUserId(31L)).thenThrow(new ResourceNotFound("Elder for user", 31L));
		FamilyMember member = mock(FamilyMember.class);
		when(member.id()).thenReturn(12L);
		when(familyMembers.findByUserId(40L)).thenReturn(Optional.of(member));
		when(familyMembers.findByUserId(41L)).thenReturn(Optional.empty());

		assertThat(core.elderByUser(30L)).isEqualTo(new CoreApi.ElderRef(3L));
		assertThatThrownBy(() -> core.elderByUser(31L))
				.isInstanceOf(CoreNotFound.class)
				.hasMessage("Elder for user [31] does not exist");
		assertThat(core.findFamilyMemberIdByUser(40L)).contains(12L);
		assertThat(core.findFamilyMemberIdByUser(41L)).isEmpty();
	}

	@Test
	void anEldersFamilyAndWhoOfThemMayBeAlerted() {
		when(alertRecipients.familyMemberIds(3L)).thenReturn(List.of(12L, 14L));
		when(alertRecipients.resolve(3L, 12L)).thenReturn(new FamilyAlertRecipients.Candidate(12L, 40L, null));
		when(alertRecipients.resolve(3L, 14L))
				.thenReturn(new FamilyAlertRecipients.Candidate(14L, 42L, "BINDING_EXPIRED"));

		assertThat(core.familyMemberIds(3L)).containsExactly(12L, 14L);
		assertThat(core.alertRecipient(3L, 12L)).isEqualTo(new CoreApi.AlertRecipient(12L, 40L, null));
		assertThat(core.alertRecipient(3L, 12L).eligible()).isTrue();
		assertThat(core.alertRecipient(3L, 14L).eligible()).isFalse();
	}

	@Test
	void anEldersPrimaryCaregiverOrNone() {
		when(primaryCaregivers.findRosterableCaregiverId(3L)).thenReturn(Optional.of(7L));
		when(primaryCaregivers.findRosterableCaregiverId(5L)).thenReturn(Optional.empty());

		assertThat(core.findPrimaryCaregiverId(3L)).contains(7L);
		assertThat(core.findPrimaryCaregiverId(5L)).isEmpty();
	}

	@Test
	void theCarePlanVersionAVisitFollows() {
		when(plans.read(20L, 3L)).thenReturn(new VisitPlanReader.Snapshot(20L, 2,
				List.of(new VisitPlanReader.Task(201L, "Assist with bathing", "CHECKLIST"))));
		when(plans.read(21L, 3L))
				.thenThrow(new BusinessRuleViolation("VISIT_PLAN_UNAVAILABLE", "Care plan 21 cannot be used for a visit"));

		assertThat(core.carePlanSnapshot(20L, 3L)).isEqualTo(new CoreApi.CarePlanSnapshot(20L, 2,
				List.of(new CoreApi.CarePlanTask(201L, "Assist with bathing", "CHECKLIST"))));
		assertThatThrownBy(() -> core.carePlanSnapshot(21L, 3L))
				.isInstanceOfSatisfying(CoreRuleViolation.class, violation -> {
					assertThat(violation.code()).isEqualTo("VISIT_PLAN_UNAVAILABLE");
					assertThat(violation).hasMessage("Care plan 21 cannot be used for a visit");
				});
	}

	@Test
	void aCaregiverReportsAnIncidentAndReadsItBack() {
		LocalDateTime reportedAt = LocalDateTime.of(2026, 10, 10, 9, 15);
		CaregiverIncidentGateway.Report report = new CaregiverIncidentGateway.Report(55L, 100L, "FALL", "HIGH",
				"Slipped in the bathroom", "OPEN", reportedAt, reportedAt.plusMinutes(30), null);
		when(caregiverIncidents.report(3L, 100L, 70L, "FALL", "HIGH", "Slipped in the bathroom")).thenReturn(report);
		when(caregiverIncidents.own(70L, 55L)).thenReturn(report);
		when(caregiverIncidents.list(70L, 100L, 0, 20))
				.thenReturn(new CaregiverIncidentGateway.Reports(List.of(report), 0, 20, 1));

		CoreApi.IncidentReport expected = new CoreApi.IncidentReport(55L, 100L, "FALL", "HIGH",
				"Slipped in the bathroom", "OPEN", reportedAt, reportedAt.plusMinutes(30), null);
		assertThat(core.reportIncident(new CoreApi.CaregiverIncidentRequest(3L, 100L, 70L, "FALL", "HIGH",
				"Slipped in the bathroom"))).isEqualTo(expected);
		assertThat(core.incident(55L, 70L)).isEqualTo(expected);
		assertThat(core.incidents(70L, 100L, 0, 20))
				.isEqualTo(new CoreApi.IncidentReports(List.of(expected), 0, 20, 1));
	}

	@Test
	void aMissedCheckInAndADisputeEachOpenAnIncident() {
		LocalDateTime dueAt = LocalDateTime.of(2026, 10, 10, 9, 0);
		when(missedCheckIns.raise(3L, 100L, dueAt, dueAt.plusMinutes(20))).thenReturn(56L);
		Incident dispute = mock(Incident.class);
		when(dispute.id()).thenReturn(57L);
		when(incidents.createElderServiceDispute(3L, 100L, 30L, "The caregiver left early")).thenReturn(dispute);

		assertThat(core.raiseMissedCheckIn(new CoreApi.MissedCheckInRequest(3L, 100L, dueAt, dueAt.plusMinutes(20))))
				.isEqualTo(new CoreApi.IncidentRef(56L));
		assertThat(core.raiseServiceDispute(new CoreApi.ServiceDisputeRequest(3L, 100L, 30L,
				"The caregiver left early"))).isEqualTo(new CoreApi.IncidentRef(57L));
	}

	@Test
	void whoCanCoverAVisitAndPuttingThemOnIt() {
		when(visitCover.options(100L)).thenReturn(List.of(
				new VisitCover.Option(7L, "Siti Rahman", 1, "Speaks Hokkien, nearest"),
				new VisitCover.Option(9L, "Ahmad Ismail", null, "Already has a visit at that time")));

		assertThat(core.coverOptions(100L))
				.extracting(CoreApi.CoverOption::caregiverId, CoreApi.CoverOption::rank, CoreApi.CoverOption::eligible)
				.containsExactly(tuple(7L, 1, true), tuple(9L, null, false));

		core.cover(100L, new CoreApi.CoverRequest(7L, 1L));
		verify(visitCover).cover(100L, 7L, 1L);
	}

	@Test
	void aCaregiverWhoCannotCoverKeepsCoresReason() {
		doThrow(new BusinessRuleViolation("CAREGIVER_CANNOT_COVER",
				"This caregiver cannot take the visit: Already has a visit at that time"))
				.when(visitCover).cover(100L, 9L, 1L);

		assertThatThrownBy(() -> core.cover(100L, new CoreApi.CoverRequest(9L, 1L)))
				.isInstanceOfSatisfying(CoreRuleViolation.class,
						violation -> assertThat(violation.code()).isEqualTo("CAREGIVER_CANNOT_COVER"));
	}

	@Test
	void aCallCarriesNoCsrfTokenAndLeavesNoSession() throws Exception {
		MvcResult result = mvc.perform(post("/internal/v1/visits/100/cover")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"caregiverId\":7,\"byUserId\":1}"))
				.andExpect(status().isNoContent())
				.andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getCookies()).isEmpty();
	}

}
