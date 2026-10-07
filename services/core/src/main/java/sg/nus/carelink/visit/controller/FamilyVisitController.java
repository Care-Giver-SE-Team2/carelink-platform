package sg.nus.carelink.visit.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.visit.application.FamilyVisitQueryService;
import sg.nus.carelink.visit.controller.dto.FamilyVisitListRequest;
import sg.nus.carelink.visit.controller.dto.FamilyVisitPageResponse;

/**
 * Provides the authenticated family's schedule list.
 *
 * @author Wang Zhili
 */
@RestController
@RequestMapping("/api/visits")
public class FamilyVisitController {

	private final FamilyVisitQueryService queries;

	public FamilyVisitController(FamilyVisitQueryService queries) {
		this.queries = queries;
	}

	/**
	 * Lists visits after current binding checks and optional schedule filtering.
	 *
	 * @param request Elder, caregiver, date, status and pagination parameters
	 * @param principal Account supplied by the authenticated session
	 * @return Family-visible visits with the filtered total count
	 * @author Wang Zhili
	 */
	@GetMapping
	@PreAuthorize("hasRole('FAMILY')")
	public FamilyVisitPageResponse list(@Valid @ModelAttribute FamilyVisitListRequest request, Principal principal) {
		return FamilyVisitPageResponse.from(queries.listMine(principal.getName(), request.toFilter()));
	}
}
