package sg.nus.carelink.profile.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.CaregiverApplicationReviewService;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Decision;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Item;
import sg.nus.carelink.profile.application.CaregiverApplicationReviewService.Page;
import sg.nus.carelink.profile.controller.dto.CaregiverApplicationDecisionRequests.Approve;
import sg.nus.carelink.profile.controller.dto.CaregiverApplicationDecisionRequests.Reject;

/**
 * UC-MG02: the manager reviews caregiver applications submitted from the caregiver app. A stale
 * version or a request key reused for a different decision is a 409; a reviewed application is
 * no longer pending, so it drops out of the list.
 */
@RestController
@RequestMapping("/api/caregiver-applications")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerCaregiverApplicationController {

	private final CaregiverApplicationReviewService service;

	public ManagerCaregiverApplicationController(CaregiverApplicationReviewService service) {
		this.service = service;
	}

	@GetMapping
	public Page pending(Principal manager, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		return service.pending(manager.getName(), page, size);
	}

	@GetMapping("/{id}")
	public Item one(Principal manager, @PathVariable Long id) {
		return service.one(manager.getName(), id);
	}

	@PostMapping("/{id}/approve")
	public Decision approve(Principal manager, @PathVariable Long id, @Valid @RequestBody Approve request) {
		return service.approve(manager.getName(), id, request.message(), request.expectedVersion(),
				request.clientRequestId());
	}

	@PostMapping("/{id}/reject")
	public Decision reject(Principal manager, @PathVariable Long id, @Valid @RequestBody Reject request) {
		return service.reject(manager.getName(), id, request.reason(), request.expectedVersion(),
				request.clientRequestId());
	}
}
