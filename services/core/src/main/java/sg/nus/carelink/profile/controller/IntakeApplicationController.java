package sg.nus.carelink.profile.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.IntakeQueryService;
import sg.nus.carelink.profile.controller.dto.FamilyIntakeApplicationPageResponse;
import sg.nus.carelink.profile.controller.dto.FamilyIntakeApplicationResponse;
import sg.nus.carelink.profile.controller.dto.IntakeApplicationListRequest;

/**
 * Exposes list and detail endpoints for a family's earlier intake applications. New requests for
 * care go through FamilyServiceApplicationController.
 *
 * @author Wang Zhili
 */
@RestController
@RequestMapping("/api/intake-applications")
public class IntakeApplicationController {

	private final IntakeQueryService queries;

	public IntakeApplicationController(IntakeQueryService queries) {
		this.queries = queries;
	}

	/**
	 * List the logged-in family's applications with optional status filtering and pagination.
	 *
	 * @param request Status filter, zero-based page and page size
	 * @param principal Logged-in account supplied by Spring Security
	 * @return Family-visible applications ordered newest first, with the matching total count
	 *
	 * @author Wang Zhili
	 */
	@GetMapping
	@PreAuthorize("hasRole('FAMILY')")
	public FamilyIntakeApplicationPageResponse list(@Valid @ModelAttribute IntakeApplicationListRequest request,
			Principal principal) {
		return FamilyIntakeApplicationPageResponse.from(queries.listMine(principal.getName(), request.toStatus(),
				request.getPage(), request.getSize()));
	}

	/**
	 * Read the logged-in family's application details and review progress.
	 *
	 * @param id Application identifier from the request path
	 * @param principal Logged-in account supplied by Spring Security
	 * @return Family-visible application details and review result
	 *
	 * @author Wang Zhili
	 */
	@GetMapping("/{id}")
	@PreAuthorize("hasRole('FAMILY')")
	public FamilyIntakeApplicationResponse get(@PathVariable Long id, Principal principal) {
		return FamilyIntakeApplicationResponse.from(queries.getMine(principal.getName(), id));
	}
}
