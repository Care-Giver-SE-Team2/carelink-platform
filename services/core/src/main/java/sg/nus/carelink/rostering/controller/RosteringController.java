package sg.nus.carelink.rostering.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.rostering.application.RosteringService;
import sg.nus.carelink.rostering.domain.model.RosteringRun;

/**
 * Presentation layer of the rostering module: HTTP in, HTTP out, status codes. No business
 * rules. Talks to RosteringService only, never to a repository (ArchUnit enforces it). Which role
 * may call each endpoint is declared on the method with @PreAuthorize.
 * identity.controller.AuthController is the template; use dto/ for request and response
 * shapes once they differ from the domain model.
 */
@RestController
@RequestMapping("/api/rostering-runs")
public class RosteringController {

	private final RosteringService service;

	public RosteringController(RosteringService service) {
		this.service = service;
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<RosteringRun> get(@PathVariable Long id) {
		return ResponseEntity.of(service.findRosteringRun(id));
	}
}
