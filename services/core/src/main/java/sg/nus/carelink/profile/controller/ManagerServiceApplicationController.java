package sg.nus.carelink.profile.controller;

import jakarta.validation.Valid;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.ServiceApplicationDeclineService;
import sg.nus.carelink.profile.controller.dto.DeclineServiceApplicationRequest;

/**
 * The manager's answer to a family's service application when it won't be planned. Planning one
 * needs no endpoint: publishing a care plan version that includes its activities answers it.
 */
@RestController
@RequestMapping("/api/service-applications")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerServiceApplicationController {

	private final ServiceApplicationDeclineService declines;

	public ManagerServiceApplicationController(ServiceApplicationDeclineService declines) {
		this.declines = declines;
	}

	/** 409 when it is already declined, or everything it asked for is already planned. */
	@PostMapping("/{id}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void decline(@PathVariable Long id, @Valid @RequestBody DeclineServiceApplicationRequest request,
			Principal principal) {
		declines.decline(id, request.reason(), principal.getName());
	}
}
