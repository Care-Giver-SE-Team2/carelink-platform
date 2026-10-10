package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import sg.nus.carelink.report.application.Accounts;
import sg.nus.carelink.report.application.FamilyReportQueryService;
import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.service.ReportAssembler;
import sg.nus.carelink.report.support.ReportFixtures;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.web.GlobalExceptionHandlerTestSupport;

/**
 * The HTTP surface of UC-MG07: paths, bodies and status codes.
 *
 * <p>Standalone MockMvc with the real exception handler, so this is about the controller and
 * the contract's shapes and nothing else: 202 for a generation, 201 for a correction, 400 for
 * a malformed request, 404 for an unknown report, 409 for a broken rule. The bodies are
 * checked field by field against {@code docs/api/openapi-draft.yaml}'s Report, ReportDetail and
 * ReportAmendment; the front end's fixtures are copied from what this serialises.
 */
class ReportControllerTest {

	/** The signed-in manager's account id. */
	private static final Long MANAGER = 11L;

	/** The numbers of the week in ReportFixtures, as its basis would hold them. */
	private static final ReportMetrics METRICS = new ReportMetrics(
			3, 2, new BigDecimal("66.67"), 1, 1, new BigDecimal("3.50"), 2, false);

	private final ReportService service = mock(ReportService.class);
	private final Accounts accounts = mock(Accounts.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders
				.standaloneSetup(new ReportController(service, accounts, mock(FamilyReportQueryService.class)))
				.setControllerAdvice(GlobalExceptionHandlerTestSupport.instance())
				.build();
		when(accounts.idOf(anyString())).thenReturn(MANAGER);
	}

	private static RequestPostProcessor asManager() {
		return request -> {
			request.setUserPrincipal(new UsernamePasswordAuthenticationToken("alice", null,
					List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
			return request;
		};
	}

	// ----------------------------------------------------------------- generating ---

	@Test
	void generatingAnswers202WithOneReportPerReader() throws Exception {
		when(service.generate(1L, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 11L)).thenReturn(List.of(
				ReportFixtures.stored(40L, Report.Audience.FAMILY),
				ReportFixtures.stored(41L, Report.Audience.REGULATOR),
				ReportFixtures.stored(42L, Report.Audience.INTERNAL)));

		mvc.perform(post("/api/reports/generate").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"elderId\":1,\"periodStart\":\"2026-09-14\",\"periodEnd\":\"2026-09-20\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(40))
				.andExpect(jsonPath("$[0].elderId").value(1))
				.andExpect(jsonPath("$[0].audience").value("FAMILY"))
				.andExpect(jsonPath("$[1].audience").value("REGULATOR"))
				.andExpect(jsonPath("$[2].audience").value("INTERNAL"))
				.andExpect(jsonPath("$[0].periodStart").value("2026-09-14"))
				.andExpect(jsonPath("$[0].periodEnd").value("2026-09-20"))
				.andExpect(jsonPath("$[0].status").value("PUBLISHED"))
				.andExpect(jsonPath("$[0].dataComplete").value(false))
				.andExpect(jsonPath("$[0].missingItems[0]").value("Visit 13 on 2026-09-18 not closed"))
				.andExpect(jsonPath("$[0].generatedBy").value("TEMPLATE"))
				.andExpect(jsonPath("$[0].createdAt").value("2026-09-20T23:00:00"))
				.andExpect(jsonPath("$[0].archivedAt").isEmpty())
				.andExpect(jsonPath("$[0].sections").doesNotExist());
	}

	@Test
	void withoutAnElderTheWholeInstitutionIsAskedFor() throws Exception {
		when(service.generate(any(), any(), any(), anyLong())).thenReturn(List.of());

		mvc.perform(post("/api/reports/generate").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"periodStart\":\"2026-09-14\",\"periodEnd\":\"2026-09-20\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.length()").value(0));

		verify(service).generate(null, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20), 11L);
	}

	@Test
	void aPeriodThatEndsBeforeItStartsIs400AndNothingIsGenerated() throws Exception {
		mvc.perform(post("/api/reports/generate").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"elderId\":1,\"periodStart\":\"2026-09-20\",\"periodEnd\":\"2026-09-14\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.periodInOrder").value("periodEnd must not be before periodStart"));

		verify(service, never()).generate(any(), any(), any(), any());
	}

	@Test
	void aPeriodWithoutAStartIs400() throws Exception {
		mvc.perform(post("/api/reports/generate").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"periodEnd\":\"2026-09-20\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.periodStart").value("periodStart is required"));
	}

	@Test
	void generatingForAnElderWhoDoesNotExistIs404() throws Exception {
		when(service.generate(eq(99L), any(), any(), anyLong())).thenThrow(new ResourceNotFound("Elder", 99L));

		mvc.perform(post("/api/reports/generate").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"elderId\":99,\"periodStart\":\"2026-09-14\",\"periodEnd\":\"2026-09-20\"}"))
				.andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------------- reading ---

	@Test
	void readsOneReportWithItsSectionsDisclaimerAndCorrections() throws Exception {
		Report family = new Report(40L, 1L, 8L, 11L, Report.Audience.FAMILY, ReportFixtures.WEEK,
				Report.Status.PUBLISHED,
				ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(ReportFixtures.week()),
				List.of(new ReportAmendment(3L, 40L, "Visit 13 was cancelled by the family.", 11L,
								LocalDateTime.of(2026, 9, 21, 9, 30)),
						new ReportAmendment(4L, 40L, ReportAmendment.Kind.FOLLOW_UP, "Grab bar fitted.", 11L,
								LocalDateTime.of(2026, 9, 22, 10, 0))),
				ReportFixtures.GENERATED_AT);
		when(service.findDetail(40L)).thenReturn(family);
		when(service.metricsFor(List.of(family))).thenReturn(Map.of(8L, METRICS));

		mvc.perform(get("/api/reports/40").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(40))
				.andExpect(jsonPath("$.basisId").value(8))
				.andExpect(jsonPath("$.audience").value("FAMILY"))
				.andExpect(jsonPath("$.dataComplete").value(false))
				.andExpect(jsonPath("$.metrics.visitsPlanned").value(3))
				.andExpect(jsonPath("$.metrics.visitsCompleted").value(2))
				.andExpect(jsonPath("$.metrics.fulfilmentRate").value(66.67))
				.andExpect(jsonPath("$.metrics.averageElderRating").value(3.5))
				.andExpect(jsonPath("$.sections.length()").value(7))
				.andExpect(jsonPath("$.sections[0].key").value("overview"))
				.andExpect(jsonPath("$.sections[0].figures[0].key").value("visits"))
				.andExpect(jsonPath("$.sections[0].figures[0].value").value(2))
				.andExpect(jsonPath("$.sections[0].figures[0].outOf").value(3))
				.andExpect(jsonPath("$.sections[2].title").value("Service completion"))
				.andExpect(jsonPath("$.sections[3].title").value("Vital signs"))
				.andExpect(jsonPath("$.sections[3].body").value(
						"Systolic 128–142 mmHg\nDiastolic 82–88 mmHg\nPulse 72–76 bpm\nTemperature 36.6–36.8 °C"))
				.andExpect(jsonPath("$.sections[3].series[0].key").value("systolic"))
				.andExpect(jsonPath("$.sections[3].series[0].unit").value("mmHg"))
				.andExpect(jsonPath("$.sections[3].series[0].points[1].at").value("2026-09-16"))
				.andExpect(jsonPath("$.sections[3].series[0].points[1].high").value(142))
				.andExpect(jsonPath("$.sections[3].series[0].points[1].flagged").value(true))
				.andExpect(jsonPath("$.sections[4].title").value("Observations"))
				.andExpect(jsonPath("$.sections[5].title").value("Incidents"))
				.andExpect(jsonPath("$.sections[6].title").value("Ratings and spot checks"))
				.andExpect(jsonPath("$.disclaimer").value("This summary is prepared from care records for "
						+ "information only and does not constitute medical advice."))
				.andExpect(jsonPath("$.amendments[0].id").value(3))
				.andExpect(jsonPath("$.amendments[0].kind").value("CORRECTION"))
				.andExpect(jsonPath("$.amendments[0].note").value("Visit 13 was cancelled by the family."))
				.andExpect(jsonPath("$.amendments[0].authorUserId").value(11))
				.andExpect(jsonPath("$.amendments[0].createdAt").value("2026-09-21T09:30:00"))
				.andExpect(jsonPath("$.amendments[1].kind").value("FOLLOW_UP"));
	}

	@Test
	void anInternalReportHasNoDisclaimer() throws Exception {
		when(service.findDetail(42L)).thenReturn(ReportFixtures.stored(42L, Report.Audience.INTERNAL));

		mvc.perform(get("/api/reports/42").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disclaimer").isEmpty())
				.andExpect(jsonPath("$.amendments.length()").value(0))
				.andExpect(jsonPath("$.basisId").isEmpty())
				.andExpect(jsonPath("$.metrics").isEmpty());
	}

	@Test
	void anUnknownReportIs404() throws Exception {
		when(service.findDetail(404L)).thenThrow(new ResourceNotFound("Report", 404L));

		mvc.perform(get("/api/reports/404").with(asManager()))
				.andExpect(status().isNotFound());
	}

	@Test
	void listsOnePageOfReportsWithoutTheirText() throws Exception {
		when(service.page(1L, Report.Audience.FAMILY, 0, 20)).thenReturn(
				new ReportPage(List.of(ReportFixtures.stored(40L, Report.Audience.FAMILY)), 0, 20, 1));

		mvc.perform(get("/api/reports?elderId=1&audience=FAMILY&page=0&size=20").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(40))
				.andExpect(jsonPath("$.items[0].audience").value("FAMILY"))
				.andExpect(jsonPath("$.items[0].sections").doesNotExist())
				.andExpect(jsonPath("$.items[0].metrics").isEmpty());
	}

	@Test
	void eachRowOfTheListCarriesTheNumbersOfItsBasis() throws Exception {
		Report filed = new Report(40L, 1L, 8L, 7L, Report.Audience.INTERNAL, ReportFixtures.WEEK, Report.Status.PUBLISHED,
				ReportFixtures.content(), List.of(), ReportFixtures.GENERATED_AT);
		ReportPage page = new ReportPage(List.of(filed), 0, 20, 1);
		when(service.page(null, null, 0, 20)).thenReturn(page);
		when(service.metricsFor(page.items())).thenReturn(Map.of(8L, METRICS));

		mvc.perform(get("/api/reports").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].basisId").value(8))
				.andExpect(jsonPath("$.items[0].metrics.fulfilmentRate").value(66.67))
				.andExpect(jsonPath("$.items[0].metrics.incidentCount").value(1))
				.andExpect(jsonPath("$.items[0].metrics.ratingCount").value(2));
	}

	@Test
	void theListDefaultsToEveryElderEveryReaderFirstPageOfTwenty() throws Exception {
		when(service.page(any(), any(), anyInt(), anyInt())).thenReturn(new ReportPage(List.of(), 0, 20, 0));

		mvc.perform(get("/api/reports").with(asManager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));

		verify(service).page(null, null, 0, 20);
	}

	@Test
	void aReaderThatDoesNotExistIs400() throws Exception {
		mvc.perform(get("/api/reports?audience=NEIGHBOUR").with(asManager()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.audience").exists());
	}

	// ----------------------------------------------------------------- correcting ---

	@Test
	void aCorrectionAnswers201WithWhatWasStored() throws Exception {
		when(service.amend(40L, ReportAmendment.Kind.CORRECTION, "Visit 13 was cancelled by the family.", 11L)).thenReturn(
				new ReportAmendment(3L, 40L, "Visit 13 was cancelled by the family.", 11L,
						LocalDateTime.of(2026, 9, 21, 9, 30)));

		mvc.perform(post("/api/reports/40/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"Visit 13 was cancelled by the family.\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.kind").value("CORRECTION"))
				.andExpect(jsonPath("$.note").value("Visit 13 was cancelled by the family."))
				.andExpect(jsonPath("$.authorUserId").value(11))
				.andExpect(jsonPath("$.createdAt").value("2026-09-21T09:30:00"))
				.andExpect(jsonPath("$.reportId").doesNotExist());
	}

	@Test
	void aFollowUpIsAppendedAsOne() throws Exception {
		when(service.amend(40L, ReportAmendment.Kind.FOLLOW_UP, "Grab bar fitted on Thursday.", 11L)).thenReturn(
				new ReportAmendment(4L, 40L, ReportAmendment.Kind.FOLLOW_UP, "Grab bar fitted on Thursday.", 11L,
						LocalDateTime.of(2026, 9, 24, 15, 0)));

		mvc.perform(post("/api/reports/40/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"Grab bar fitted on Thursday.\",\"kind\":\"FOLLOW_UP\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(4))
				.andExpect(jsonPath("$.kind").value("FOLLOW_UP"));
	}

	@Test
	void aKindThatDoesNotExistIs400() throws Exception {
		mvc.perform(post("/api/reports/40/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"x\",\"kind\":\"GOSSIP\"}"))
				.andExpect(status().isBadRequest());

		verify(service, never()).amend(anyLong(), any(), anyString(), anyLong());
	}

	@Test
	void anEmptyCorrectionIs400() throws Exception {
		mvc.perform(post("/api/reports/40/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.note").value("note is required"));

		verify(service, never()).amend(anyLong(), any(), anyString(), anyLong());
	}

	@Test
	void aCorrectionLongerThanTheColumnIs400() throws Exception {
		mvc.perform(post("/api/reports/40/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"" + "x".repeat(1001) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.note").value("note must be at most 1000 characters"));
	}

	@Test
	void correctingAnUnknownReportIs404() throws Exception {
		when(service.amend(eq(404L), any(), anyString(), anyLong())).thenThrow(new ResourceNotFound("Report", 404L));

		mvc.perform(post("/api/reports/404/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"too late\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void aCorrectionTheRulesRefuseIs409() throws Exception {
		when(service.amend(eq(40L), any(), anyString(), anyLong())).thenThrow(
				new BusinessRuleViolation("REPORT_AMENDMENT_NOTE_REQUIRED", "A correction has to say what it corrects"));

		mvc.perform(post("/api/reports/40/amendments").with(asManager())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\":\"x\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REPORT_AMENDMENT_NOTE_REQUIRED"));
	}

	// ----------------------------------------------------------------- the surface ---

	/**
	 * A7: a filed report cannot be edited or deleted because nothing here offers to. Checked on
	 * the mappings rather than by sending a DELETE, which only proves that this one path was not
	 * routed today.
	 */
	@Test
	void noEndpointEditsOrDeletesAReport() {
		for (Method method : ReportController.class.getDeclaredMethods()) {
			assertThat(AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)).isFalse();
			assertThat(AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)).isFalse();
			assertThat(AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)).isFalse();
			RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
			if (mapping != null) {
				assertThat(mapping.method())
						.as(method.getName())
						.isNotEmpty()
						.containsAnyOf(RequestMethod.GET, RequestMethod.POST)
						.doesNotContain(RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
			}
		}
	}

	/** List and detail reads are shared with UC-FM04; writes remain manager-only. */
	@Test
	void onlyReadsAlsoAllowFamilies() {
		List<Method> endpoints = Arrays.stream(ReportController.class.getDeclaredMethods())
				.filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
				.toList();

		assertThat(endpoints).hasSize(4).allSatisfy(method ->
				assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(
						Set.of("list", "get").contains(method.getName())
								? "hasAnyRole('MANAGER', 'FAMILY')" : "hasRole('MANAGER')"));
	}
}
