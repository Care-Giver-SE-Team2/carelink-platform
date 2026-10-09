package sg.nus.carelink.incident.controller;

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
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.controller.dto.IncidentRequests;
import sg.nus.carelink.incident.controller.dto.IncidentResponses;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * HTTP for UC-MG05: take over and handle a care exception.
 *
 * <p>Presentation only — HTTP in, HTTP out, status codes. Every rule is behind
 * {@link IncidentService}, and the interesting status codes come from exceptions the domain
 * throws rather than from checks written here:
 * <ul>
 *   <li>409 when a transition is illegal (already taken over, not taken over yet, already
 *       closed) — {@code BusinessRuleViolation}</li>
 *   <li>403 when a manager tries to close an incident somebody else is handling —
 *       {@code AccessDeniedException}</li>
 *   <li>404 when the incident or the playbook does not exist — {@code ResourceNotFound}</li>
 * </ul>
 *
 * <p>Both manager owners work on this module, so the paths here are exactly the ones drafted
 * in {@code docs/api/openapi.yaml}; the contract is written before the endpoint and the
 * endpoint is made to match it, not the other way round.
 */
@RestController
@RequestMapping("/api")
public class IncidentController {

	private final IncidentService service;
	private final IdentityService identity;

	public IncidentController(IncidentService service, IdentityService identity) {
		this.service = service;
		this.identity = identity;
	}

	/** The incident with its whole timeline, including refused and timed-out attempts. */
	@GetMapping("/incidents/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	public IncidentResponses.Detail get(@PathVariable Long id) {
		Incident incident = service.findIncident(id).orElseThrow(() -> notFound(id));
		return IncidentResponses.Detail.of(incident, service.timelineOf(id));
	}

	/**
	 * Step 2: the queue the manager's console opens on — everything still needing attention,
	 * nearest response deadline first.
	 *
	 * <p>Every parameter is optional, including {@code elderId}. It used to be required, and
	 * that was the wrong way round: a manager arriving at work knows nothing about elder
	 * ids, so the endpoint could not answer the one question the screen is for. Narrowing to
	 * one elder is still possible and is now what it always should have been, a filter.
	 */
	@GetMapping("/incidents")
	@PreAuthorize("hasRole('MANAGER')")
	public IncidentResponses.Queue list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Incident.Status status,
			@RequestParam(required = false) Incident.Severity severity,
			@RequestParam(required = false) Long elderId) {

		return IncidentResponses.Queue.of(service.queue(status, severity, elderId, page, size));
	}

	/**
	 * Step 3: take the incident over. The countdown stops here, which is what removes it
	 * from the scheduled scan.
	 */
	@PostMapping("/incidents/{id}/claim")
	@PreAuthorize("hasRole('MANAGER')")
	public Incident claim(@PathVariable Long id, Principal principal) {
		AppUser actor = currentUser(principal);
		return service.claim(id, actor.id(), label(actor));
	}

	/** Exception 3a, by hand: push the incident to the next level before the countdown ends. */
	@PostMapping("/incidents/{id}/escalate")
	@PreAuthorize("hasRole('MANAGER')")
	public Incident escalate(
			@PathVariable Long id,
			@Valid @RequestBody(required = false) IncidentRequests.Escalate body,
			Principal principal) {

		String reason = body == null ? null : body.reason();
		return service.escalate(id, reason, label(currentUser(principal)));
	}

	/** The chain as it would be walked right now. A plan, not a record. */
	@GetMapping("/incidents/{id}/escalation-chain")
	@PreAuthorize("hasRole('MANAGER')")
	public IncidentResponses.Chain escalationChain(@PathVariable Long id) {
		return IncidentResponses.Chain.of(service.escalationChainOf(id));
	}

	/**
	 * Step 4: record an attempt to reach the family, whether or not it worked.
	 *
	 * <p>When it did not, the response carries the playbook to fall back on, so the manager
	 * can act without a second round trip and without waiting for the family.
	 */
	@PostMapping("/incidents/{id}/contact-attempts")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('MANAGER')")
	public IncidentResponses.ContactAttemptResult recordContact(
			@PathVariable Long id,
			@Valid @RequestBody IncidentRequests.ContactAttemptBody body,
			Principal principal) {

		return IncidentResponses.ContactAttemptResult.of(
				service.recordContactAttempt(id, body.toDomain(), label(currentUser(principal))));
	}

	/** Step 5: apply the standard response for this category of incident. */
	@PostMapping("/incidents/{id}/playbook")
	@PreAuthorize("hasRole('MANAGER')")
	public Incident applyPlaybook(
			@PathVariable Long id,
			@Valid @RequestBody IncidentRequests.ApplyPlaybook body,
			Principal principal) {

		return service.applyPlaybook(id, body.playbookCode(), label(currentUser(principal)));
	}

	/** Alternative 5a: the situation changed. The chain is rebuilt, the timeline continues. */
	@PostMapping("/incidents/{id}/severity")
	@PreAuthorize("hasRole('MANAGER')")
	public Incident changeSeverity(
			@PathVariable Long id,
			@Valid @RequestBody IncidentRequests.ChangeSeverity body,
			Principal principal) {

		return service.changeSeverity(id, body.severity(), body.reason(), label(currentUser(principal)));
	}

	/** Step 6: record the conclusion and close the incident. */
	@PostMapping("/incidents/{id}/resolve")
	@PreAuthorize("hasRole('MANAGER')")
	public Incident resolve(
			@PathVariable Long id,
			@Valid @RequestBody IncidentRequests.Resolve body,
			Principal principal) {

		AppUser actor = currentUser(principal);
		String outcome = body.outcome() == null ? null : body.outcome().name();
		return service.resolve(id, actor.id(), body.resolutionNote(), outcome, label(actor));
	}

	/** The standard playbooks, so the front end does not hard-code them. */
	@GetMapping("/incident-playbooks")
	@PreAuthorize("hasRole('MANAGER')")
	public List<IncidentResponses.PlaybookView> playbooks() {
		return service.playbooks().stream().map(IncidentResponses.PlaybookView::of).toList();
	}

	private AppUser currentUser(Principal principal) {
		return identity.require(principal.getName());
	}

	/** What the timeline shows as the actor: readable, and still traceable to an account. */
	private static String label(AppUser user) {
		return "%s (%s)".formatted(user.displayName(), user.username());
	}

	private static ResourceNotFound notFound(Long id) {
		return new ResourceNotFound("Incident", id);
	}
}
