package sg.nus.carelink.incident.controller;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.incident.application.FamilyIncidentQueryService;
import sg.nus.carelink.incident.controller.dto.FamilyIncidentDetailResponse;

/**
 * FM05 family incident reads, separate from the manager's internal timeline.
 *
 * @author Wang Zhili
 */
@RestController
@RequestMapping("/api/family/incidents")
public class FamilyIncidentController {

	private final FamilyIncidentQueryService queries;

	public FamilyIncidentController(FamilyIncidentQueryService queries) {
		this.queries = queries;
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('FAMILY')")
	public FamilyIncidentDetailResponse get(@PathVariable Long id, Principal principal) {
		return FamilyIncidentDetailResponse.of(queries.findDetail(principal.getName(), id));
	}
}
