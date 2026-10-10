package sg.nus.carelink.report.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.application.FamilyReportQueryService;
import sg.nus.carelink.report.controller.dto.FamilyReportDetailResponse;
import sg.nus.carelink.report.controller.dto.FamilyReportPageResponse;
import sg.nus.carelink.report.controller.dto.ReportRequests;
import sg.nus.carelink.report.controller.dto.ReportResponses;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportPage;

/**
 * HTTP for UC-MG07: generate the period's reports, list them, read one, append a correction or
 * a follow-up.
 *
 * <p>Presentation only - HTTP in, HTTP out, status codes. Talks to application services, never
 * to a repository (ArchUnit enforces it). The paths are the ones drafted in
 * {@code docs/api/openapi-draft.yaml}; the contract was written first and this is made to
 * match it.
 *
 * <p>There is no PUT, PATCH or DELETE here, on purpose and permanently: a filed report cannot
 * be edited or removed (UC-MG07 5a), only corrected by appending. The rule is enforced by the
 * endpoints not existing rather than by an endpoint that always refuses.
 *
 * <p>UC-FM04 shares list and detail paths, with separate family projections and current
 * binding checks. Generation and corrections remain manager-only.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

	private final ReportService service;
	private final IdentityService identity;
	private final FamilyReportQueryService familyReports;

	public ReportController(ReportService service, IdentityService identity, FamilyReportQueryService familyReports) {
		this.service = service;
		this.identity = identity;
		this.familyReports = familyReports;
	}

	/**
	 * Steps 1 to 4, by hand: the three readers' reports for a period, for one elder or for
	 * every elder with a visit in it.
	 *
	 * <p>202 as the contract says. Generation is finished when this answers - the reports are
	 * filed and returned - but asking again for the same period hands back the same reports
	 * rather than new ones, which is not what 201 would promise.
	 */
	@PostMapping("/generate")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasRole('MANAGER')")
	public List<ReportResponses.ReportView> generate(
			@Valid @RequestBody ReportRequests.Generate body,
			Principal principal) {

		Long requestedBy = identity.require(principal.getName()).id();
		List<Report> filed = service.generate(body.elderId(), body.periodStart(), body.periodEnd(), requestedBy);
		Map<Long, ReportMetrics> metrics = service.metricsFor(filed);
		return filed.stream().map(report -> ReportResponses.ReportView.of(report, metrics)).toList();
	}

	/** Lists the authenticated role's report projection; family scope comes only from the session. */
	@GetMapping
	@PreAuthorize("hasAnyRole('MANAGER', 'FAMILY')")
	public Object list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Long elderId,
			@RequestParam(required = false) Report.Audience audience,
			Authentication authentication) {

		if (authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_FAMILY"))) {
			if (page < 0 || size < 1 || size > 200) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"page must be nonnegative and size must be between 1 and 200");
			}
			return FamilyReportPageResponse.of(familyReports.page(authentication.getName(), elderId, audience, page, size));
		}
		ReportPage found = service.page(elderId, audience, page, size);
		return ReportResponses.Page.of(found, service.metricsFor(found.items()));
	}

	/** One report: its sections, its disclaimer if it has one, and every correction. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('MANAGER', 'FAMILY')")
	public Object get(@PathVariable Long id, Authentication authentication) {
		if (authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_FAMILY"))) {
			return FamilyReportDetailResponse.of(familyReports.findDetail(authentication.getName(), id));
		}
		Report report = service.findDetail(id);
		return ReportResponses.Detail.of(report, service.metricsFor(List.of(report)));
	}

	/**
	 * The one change a filed report accepts: a dated, signed note appended to it - a correction,
	 * or a follow-up on something it recorded.
	 */
	@PostMapping("/{id}/amendments")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('MANAGER')")
	public ReportResponses.Amendment amend(
			@PathVariable Long id,
			@Valid @RequestBody ReportRequests.Amend body,
			Principal principal) {

		Long author = identity.require(principal.getName()).id();
		return ReportResponses.Amendment.of(service.amend(id, body.kindOrCorrection(), body.note(), author));
	}
}
