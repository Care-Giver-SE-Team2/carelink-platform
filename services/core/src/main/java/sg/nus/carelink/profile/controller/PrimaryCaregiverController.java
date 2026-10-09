package sg.nus.carelink.profile.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.PrimaryCaregiverService;
import sg.nus.carelink.profile.controller.dto.CaregiverOptionResponse;
import sg.nus.carelink.profile.controller.dto.PrimaryCaregiverRequest;
import sg.nus.carelink.profile.controller.dto.PrimaryCaregiverResponse;

/** The manager's caregiver picker and an elder's primary-caregiver assignment (UC-MG02). */
@RestController
@PreAuthorize("hasRole('MANAGER')")
public class PrimaryCaregiverController {

	private final PrimaryCaregiverService service;

	public PrimaryCaregiverController(PrimaryCaregiverService service) {
		this.service = service;
	}

	@GetMapping("/api/caregivers")
	public List<CaregiverOptionResponse> listCaregivers() {
		return service.listCaregivers().stream().map(CaregiverOptionResponse::from).toList();
	}

	@PutMapping("/api/elders/{elderId}/primary-caregiver")
	public PrimaryCaregiverResponse assign(@PathVariable Long elderId,
			@Valid @RequestBody PrimaryCaregiverRequest request) {
		return PrimaryCaregiverResponse.from(service.assign(elderId, request.caregiverId()));
	}

	@DeleteMapping("/api/elders/{elderId}/primary-caregiver")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unassign(@PathVariable Long elderId) {
		service.unassign(elderId);
	}
}
