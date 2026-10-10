package sg.nus.carelink.careplan.controller;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.careplan.application.CarePlanService;
import sg.nus.carelink.careplan.application.PlanNodeInput;
import sg.nus.carelink.careplan.application.VisitInput;
import sg.nus.carelink.careplan.controller.dto.CarePlanNodeResponse;
import sg.nus.carelink.careplan.controller.dto.CreateCarePlanRequest;
import sg.nus.carelink.careplan.controller.dto.PlanNodeRequest;
import sg.nus.carelink.careplan.controller.dto.PublishCarePlanRequest;
import sg.nus.carelink.careplan.controller.dto.SaveCarePlanDraftRequest;
import sg.nus.carelink.careplan.controller.dto.StopCarePlanRequest;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Presentation layer of the careplan module: HTTP in, HTTP out, status codes. No business
 * rules. Talks to CarePlanService only, never to a repository (ArchUnit enforces it). Which role
 * may call each endpoint is declared on the method with @PreAuthorize.
 * identity.controller.AuthController is the template; use dto/ for request and response
 * shapes once they differ from the domain model.
 */
@RestController
@RequestMapping("/api/care-plans")
public class CarePlanController {

	private final CarePlanService service;
	private final UserDirectory users;

	public CarePlanController(CarePlanService service, UserDirectory users) {
		this.service = service;
		this.users = users;
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<CarePlan> get(@PathVariable Long id) {
		return ResponseEntity.of(service.findCarePlan(id));
	}

	/** Every version of the elder's plan, drafts included, newest first. */
	@GetMapping
	@PreAuthorize("hasRole('MANAGER')")
	public List<CarePlan> versionsForElder(@RequestParam Long elderId) {
		return service.findVersions(elderId);
	}

	/** The elder's highest-version plan (draft, published or superseded), if any. */
	@GetMapping("/latest")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<CarePlan> latestForElder(@RequestParam Long elderId) {
		return ResponseEntity.of(service.findLatestByElderId(elderId));
	}

	@GetMapping("/{id}/nodes")
	@PreAuthorize("hasRole('MANAGER')")
	public List<CarePlanNodeResponse> nodes(@PathVariable Long id) {
		return CarePlanNodeResponse.listFrom(service.findNodes(id));
	}

	@PostMapping
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<CarePlan> create(@Valid @RequestBody CreateCarePlanRequest request,
			Authentication authentication) {
		Long actingUserId = actingUserId(authentication);
		CarePlan created = service.createDraft(request.elderId(), actingUserId);
		return ResponseEntity.created(URI.create("/api/care-plans/" + created.id())).body(created);
	}

	/** Saves the editor's work in progress on a draft; nothing is scheduled from it. */
	@PutMapping("/{id}/draft")
	@PreAuthorize("hasRole('MANAGER')")
	public CarePlan saveDraft(@PathVariable Long id, @Valid @RequestBody SaveCarePlanDraftRequest request) {
		return service.saveDraft(id, request.startDate(),
				request.nodes().stream().map(CarePlanController::toInput).toList());
	}

	/** Throws a draft away; an issued version is refused (409) — stop it instead. */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discardDraft(@PathVariable Long id) {
		service.discardDraft(id);
	}

	@PostMapping("/{id}/publish")
	@PreAuthorize("hasRole('MANAGER')")
	public CarePlan publish(@PathVariable Long id, @Valid @RequestBody PublishCarePlanRequest request) {
		return service.publish(id, request.startDate(), request.nodes().stream().map(CarePlanController::toInput).toList());
	}

	@PostMapping("/{id}/stop")
	@PreAuthorize("hasRole('MANAGER')")
	public CarePlan stop(@PathVariable Long id, @Valid @RequestBody StopCarePlanRequest request,
			Authentication authentication) {
		return service.stop(id, request.effectiveDate(), request.reason(), actingUserId(authentication));
	}

	private Long actingUserId(Authentication authentication) {
		return users.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResourceNotFound("Account", authentication.getName()))
				.id();
	}

	private static PlanNodeInput toInput(PlanNodeRequest request) {
		return new PlanNodeInput(
				request.groupName(),
				request.activityCode(),
				request.name(),
				request.visits() == null
						? List.of()
						: request.visits().stream().map(v -> new VisitInput(v.day(), v.startTime(), v.minutes())).toList(),
				request.evidenceType());
	}
}
