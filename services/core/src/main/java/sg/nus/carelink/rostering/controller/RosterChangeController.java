package sg.nus.carelink.rostering.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import sg.nus.carelink.rostering.application.AbsenceQueryService;
import sg.nus.carelink.rostering.application.AbsenceReRosteringService;
import sg.nus.carelink.rostering.controller.dto.AbsenceRequests;

/**
 * HTTP for the family's part of UC-MG04, steps 4 and 5: see the changes to their elders' visits
 * and answer them. A family member is only ever shown, and may only answer for, elders they are
 * bound to; the binding check is the profile module's {@code FamilyAccessQuery}.
 */
@RestController
@RequestMapping("/api/roster-changes")
public class RosterChangeController {

	private final AbsenceReRosteringService reRostering;
	private final AbsenceQueryService queries;

	public RosterChangeController(AbsenceReRosteringService reRostering, AbsenceQueryService queries) {
		this.reRostering = reRostering;
		this.queries = queries;
	}

	/** Changes to the family's elders' visits: those waiting for an answer first. */
	@GetMapping
	@PreAuthorize("hasRole('FAMILY')")
	public List<AbsenceQueryService.FamilyChange> list(Principal principal) {
		return queries.forFamily(principal.getName());
	}

	/**
	 * Step 5: keep the suggestion or pick another option, move the visit, or skip it. 409 when the
	 * change is already settled or the time to answer is up.
	 */
	@PostMapping("/{id}/decision")
	@PreAuthorize("hasRole('FAMILY')")
	public AbsenceQueryService.FamilyChange decide(@PathVariable Long id,
			@Valid @RequestBody AbsenceRequests.Decision body, Principal principal) {
		reRostering.decide(id, principal.getName(), body.toChoice());
		return queries.forFamily(principal.getName(), id);
	}
}
