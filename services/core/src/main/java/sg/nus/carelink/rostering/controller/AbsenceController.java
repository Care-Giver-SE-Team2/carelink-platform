package sg.nus.carelink.rostering.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.rostering.application.AbsenceQueryService;
import sg.nus.carelink.rostering.application.AbsenceReRosteringService;
import sg.nus.carelink.rostering.application.AbsenceService;
import sg.nus.carelink.rostering.controller.dto.AbsenceRequests;
import sg.nus.carelink.rostering.controller.dto.AbsenceResponses;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;

/**
 * HTTP for UC-MG04 on the manager's side, and the caregiver's own absences (UC-CG02).
 *
 * <p>Presentation only. The status codes come from what the layers below throw: 409 for a broken
 * rule (an absence already reviewed, a family still deciding), 404 for a missing absence, 403 for
 * the wrong role, 400 for a malformed body.
 */
@RestController
@RequestMapping("/api")
public class AbsenceController {

	private final AbsenceService absences;
	private final AbsenceReRosteringService reRostering;
	private final AbsenceQueryService queries;
	private final IdentityService identity;

	public AbsenceController(AbsenceService absences, AbsenceReRosteringService reRostering,
			AbsenceQueryService queries, IdentityService identity) {
		this.absences = absences;
		this.reRostering = reRostering;
		this.queries = queries;
		this.identity = identity;
	}

	/** Every absence, latest first, with how far its re-rostering has got. */
	@GetMapping("/absences")
	@PreAuthorize("hasRole('MANAGER')")
	public List<AbsenceQueryService.AbsenceSummary> list(
			@RequestParam(required = false) AbsenceReport.Status status) {
		return queries.list(status);
	}

	/** Step 1: the manager records an absence they were told about; it is approved at once. */
	@PostMapping("/absences")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('MANAGER')")
	public AbsenceQueryService.AbsenceSummary record(@Valid @RequestBody AbsenceRequests.Record body,
			Principal principal) {
		AbsenceReport recorded = absences.recordForCaregiver(body.caregiverId(), body.type(), body.startDate(),
				body.endDate(), body.reason(), managerId(principal));
		return queries.caseOf(recorded.id()).absence();
	}

	/** One absence: what is left to re-roster, and every change with its candidates and rule results. */
	@GetMapping("/absences/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	public AbsenceQueryService.AbsenceCase get(@PathVariable Long id) {
		return queries.caseOf(id);
	}

	/** A manager accepts an absence a caregiver asked for. */
	@PostMapping("/absences/{id}/approve")
	@PreAuthorize("hasRole('MANAGER')")
	public AbsenceQueryService.AbsenceSummary approve(@PathVariable Long id, Principal principal) {
		absences.approve(id, managerId(principal));
		return queries.caseOf(id).absence();
	}

	/** A manager turns an absence a caregiver asked for down. */
	@PostMapping("/absences/{id}/reject")
	@PreAuthorize("hasRole('MANAGER')")
	public AbsenceQueryService.AbsenceSummary reject(@PathVariable Long id, Principal principal) {
		absences.reject(id, managerId(principal));
		return queries.caseOf(id).absence();
	}

	/** Steps 2 to 4: re-roster now. Safe to repeat; only visits nobody has handled are searched. */
	@PostMapping("/absences/{id}/rerostering-runs")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('MANAGER')")
	public AbsenceResponses.ReRostered reroster(@PathVariable Long id,
			@RequestBody(required = false) AbsenceRequests.Reroster body, Principal principal) {
		AbsenceReRosteringService.ReRosterOutcome outcome = reRostering.reroster(id,
				body == null ? null : body.objective(), managerId(principal));
		return new AbsenceResponses.ReRostered(outcome, queries.caseOf(id));
	}

	/** Step 7: the manager confirms every vacated visit is accounted for. */
	@PostMapping("/absences/{id}/coverage-confirmation")
	@PreAuthorize("hasRole('MANAGER')")
	public AbsenceQueryService.AbsenceCase confirmCoverage(@PathVariable Long id, Principal principal) {
		reRostering.confirmCoverage(id, managerId(principal));
		return queries.caseOf(id);
	}

	/** UC-CG02: the signed-in caregiver's own absences. */
	@GetMapping("/caregivers/me/absences")
	@PreAuthorize("hasRole('CAREGIVER')")
	public List<AbsenceResponses.OwnAbsence> mine(Principal principal) {
		return absences.listForSelf(principal.getName()).stream().map(AbsenceResponses.OwnAbsence::of).toList();
	}

	/** UC-CG02: the signed-in caregiver asks for leave; a manager reviews it. */
	@PostMapping("/caregivers/me/absences")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CAREGIVER')")
	public AbsenceResponses.OwnAbsence request(@Valid @RequestBody AbsenceRequests.Request body, Principal principal) {
		return AbsenceResponses.OwnAbsence.of(absences.requestForSelf(principal.getName(), body.type(),
				body.startDate(), body.endDate(), body.reason()));
	}

	private Long managerId(Principal principal) {
		return identity.require(principal.getName()).id();
	}
}
