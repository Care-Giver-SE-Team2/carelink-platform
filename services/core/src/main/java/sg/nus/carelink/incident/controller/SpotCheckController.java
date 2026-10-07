package sg.nus.carelink.incident.controller;

import java.security.Principal;
import java.util.List;

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

import jakarta.validation.Valid;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.SpotCheckService;
import sg.nus.carelink.incident.controller.dto.SpotCheckRequests;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.shared.security.Role;

/**
 * HTTP for UC-MG08: the manager asks for and records spot checks, the family consents or
 * declines (the former UC-FM07), and the caregiver reads and answers the conclusions.
 *
 * <p>Presentation only: 409 when a step is not allowed in the check's stage (recording before
 * the family agreed, answering a check already settled), 403 for a family member not bound to
 * the elder, 404 for a missing check or visit, 400 for a malformed body.
 */
@RestController
@RequestMapping("/api")
public class SpotCheckController {

	private final SpotCheckService service;
	private final IdentityService identity;

	public SpotCheckController(SpotCheckService service, IdentityService identity) {
		this.service = service;
		this.identity = identity;
	}

	/**
	 * The spot checks the caller may see: a manager every one, narrowed when asked; a family
	 * member those of their elders, waiting ones first; a caregiver their own conclusions.
	 */
	@GetMapping("/spot-checks")
	@PreAuthorize("hasAnyRole('MANAGER', 'FAMILY', 'CAREGIVER')")
	public List<SpotCheckService.SpotCheckView> list(
			@RequestParam(required = false) SpotCheck.Stage stage,
			@RequestParam(required = false) Long caregiverId,
			@RequestParam(required = false) Long elderId,
			Authentication authentication) {

		if (has(authentication, Role.MANAGER)) {
			return service.list(stage, caregiverId, elderId);
		}
		return has(authentication, Role.FAMILY)
				? service.forFamily(authentication.getName())
				: service.forCaregiver(authentication.getName());
	}

	/** Steps 1 and 2: ask to watch a visit; the family is asked. */
	@PostMapping("/spot-checks")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('MANAGER')")
	public SpotCheckService.SpotCheckView request(@Valid @RequestBody SpotCheckRequests.Request body,
			Principal principal) {
		AppUser manager = identity.require(principal.getName());
		return service.view(service.request(body.visitId(), body.purpose(), manager.id()).id());
	}

	/** The elder's visits a manager may choose to check. */
	@GetMapping("/spot-checks/visits")
	@PreAuthorize("hasRole('MANAGER')")
	public List<SpotCheckService.VisitChoice> visitsToCheck(@RequestParam Long elderId) {
		return service.visitsToCheck(elderId);
	}

	/** Step 5's other half: the conclusions on a caregiver's record. */
	@GetMapping("/caregivers/{caregiverId}/spot-checks")
	@PreAuthorize("hasRole('MANAGER')")
	public List<SpotCheckService.SpotCheckView> conclusionsFor(@PathVariable Long caregiverId) {
		return service.conclusionsFor(caregiverId);
	}

	/** Step 3 or alternative 3a: the family agrees, or declines and says why. */
	@PostMapping("/spot-checks/{id}/decision")
	@PreAuthorize("hasRole('FAMILY')")
	public SpotCheckService.SpotCheckView decide(@PathVariable Long id,
			@Valid @RequestBody SpotCheckRequests.Decision body, Principal principal) {
		return service.view(service.decide(id, principal.getName(), body.approve(), body.reason()).id());
	}

	/** Steps 4 and 5: the conclusion recorded on site. */
	@PostMapping("/spot-checks/{id}/conclusion")
	@PreAuthorize("hasRole('MANAGER')")
	public SpotCheckService.SpotCheckView conclude(@PathVariable Long id,
			@Valid @RequestBody SpotCheckRequests.Conclusion body) {
		return service.view(service.conclude(id, body.result(), body.notes()).id());
	}

	/** Exception 4a: the caregiver did not turn up; the missed visit goes to the exception queue. */
	@PostMapping("/spot-checks/{id}/no-show")
	@PreAuthorize("hasRole('MANAGER')")
	public SpotCheckService.SpotCheckView noShow(@PathVariable Long id,
			@Valid @RequestBody(required = false) SpotCheckRequests.NoShow body, Principal principal) {
		AppUser manager = identity.require(principal.getName());
		String actor = "%s (%s)".formatted(manager.displayName(), manager.username());
		return service.view(service.reportNoShow(id, body == null ? null : body.notes(), actor).id());
	}

	/** Exception 4b: the elder was out; the check moves to another of their visits. */
	@PostMapping("/spot-checks/{id}/move")
	@PreAuthorize("hasRole('MANAGER')")
	public SpotCheckService.SpotCheckView move(@PathVariable Long id, @Valid @RequestBody SpotCheckRequests.Move body) {
		return service.view(service.moveTo(id, body.visitId()).id());
	}

	/** The manager calls the request off and says why. */
	@PostMapping("/spot-checks/{id}/withdrawal")
	@PreAuthorize("hasRole('MANAGER')")
	public SpotCheckService.SpotCheckView withdraw(@PathVariable Long id,
			@Valid @RequestBody SpotCheckRequests.Withdrawal body) {
		return service.view(service.withdraw(id, body.reason()).id());
	}

	/** The checked caregiver answers the conclusion. */
	@PostMapping("/spot-checks/{id}/response")
	@PreAuthorize("hasRole('CAREGIVER')")
	public SpotCheckService.SpotCheckView respond(@PathVariable Long id,
			@Valid @RequestBody SpotCheckRequests.Response body, Principal principal) {
		return service.view(service.respond(id, principal.getName(), body.response()).id());
	}

	private static boolean has(Authentication authentication, Role role) {
		return authentication.getAuthorities().stream().anyMatch(a -> role.authority().equals(a.getAuthority()));
	}
}
