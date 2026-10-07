package sg.nus.carelink.profile.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.IntakeSubmissionService;
import sg.nus.carelink.profile.application.IntakeQueryService;
import sg.nus.carelink.profile.controller.dto.FamilyIntakeApplicationPageResponse;
import sg.nus.carelink.profile.controller.dto.FamilyIntakeApplicationResponse;
import sg.nus.carelink.profile.controller.dto.IntakeApplicationCreateRequest;
import sg.nus.carelink.profile.controller.dto.IntakeApplicationListRequest;

/**
 * Exposes submission, list and detail endpoints for family intake applications.
 *
 * @author Wang Zhili
 */
@RestController
@RequestMapping("/api/intake-applications")
public class IntakeApplicationController {

	private final IntakeSubmissionService submissions;
	private final IntakeQueryService queries;

	public IntakeApplicationController(IntakeSubmissionService submissions, IntakeQueryService queries) {
		this.submissions = submissions;
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

	/**
	 * Create a SUBMITTED intake application for the logged-in family member.
	 *
	 * @param request Elder details and care needs supplied by the family
	 * @param principal Logged-in account supplied by Spring Security
	 * @return Saved application details with HTTP 201, including the identifier and creation time
	 *
	 * @author Wang Zhili
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('FAMILY')")
	public FamilyIntakeApplicationResponse submit(@Valid @RequestBody IntakeApplicationCreateRequest request,
			Principal principal) {
		return FamilyIntakeApplicationResponse.from(submissions.submit(principal.getName(), request.toSubmission()));
	}
}
