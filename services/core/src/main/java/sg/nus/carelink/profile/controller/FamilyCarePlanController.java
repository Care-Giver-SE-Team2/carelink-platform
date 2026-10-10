package sg.nus.carelink.profile.controller;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.FamilyCarePlan;
import sg.nus.carelink.profile.application.FamilyCarePlanService;

/**
 * A bound family member reads the care plan of an elder they can see: the version in force and
 * any published to start later. Lives in profile because family access and auditing are profile's;
 * the plan itself comes through careplan's contract.
 */
@RestController
@RequestMapping("/api/family/elders/{elderId}/care-plan")
@PreAuthorize("hasRole('FAMILY')")
public class FamilyCarePlanController {

	private final FamilyCarePlanService plans;

	public FamilyCarePlanController(FamilyCarePlanService plans) {
		this.plans = plans;
	}

	@GetMapping
	public FamilyCarePlan get(@PathVariable Long elderId, Principal principal) {
		return plans.view(principal.getName(), elderId);
	}
}
