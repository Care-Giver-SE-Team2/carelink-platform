package sg.nus.carelink.rostering.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.rostering.application.CredentialRiskService;
import sg.nus.carelink.rostering.application.CredentialRiskService.CredentialRisk;

/**
 * The Visits at risk column of the manager's certification register (UC-MG06), keyed by
 * credential id. Separate from GET /api/credentials because only rostering may read visits
 * and care plans together; profile cannot depend on visit without a module cycle.
 */
@RestController
@PreAuthorize("hasRole('MANAGER')")
public class CredentialRiskController {

	private final CredentialRiskService service;

	public CredentialRiskController(CredentialRiskService service) {
		this.service = service;
	}

	@GetMapping("/api/credentials/visits-at-risk")
	public List<CredentialRisk> visitsAtRisk() {
		return service.risks();
	}
}
