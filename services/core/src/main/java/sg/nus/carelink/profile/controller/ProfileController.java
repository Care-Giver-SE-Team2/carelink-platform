package sg.nus.carelink.profile.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.ElderFamilyContact;
import sg.nus.carelink.profile.application.ElderFamilyContactService;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.application.FamilyElderQueryService;
import sg.nus.carelink.profile.controller.dto.ElderListItemResponse;
import sg.nus.carelink.profile.domain.model.Elder;

/**
 * Provides elder profile and list endpoints using application services.
 */
@RestController
@RequestMapping("/api/elders")
public class ProfileController {

	private final ProfileService service;
	private final FamilyElderQueryService familyElders;
	private final ElderFamilyContactService familyContacts;

	public ProfileController(ProfileService service, FamilyElderQueryService familyElders,
			ElderFamilyContactService familyContacts) {
		this.service = service;
		this.familyElders = familyElders;
		this.familyContacts = familyContacts;
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<Elder> get(@PathVariable Long id) {
		return ResponseEntity.of(service.findElder(id));
	}

	/** The elder's currently bound family, primary contact first. */
	@GetMapping("/{id}/family")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<List<ElderFamilyContact>> family(@PathVariable Long id) {
		return ResponseEntity.of(familyContacts.listForElder(id));
	}

	/**
	 * Lists all elders for a manager or readable bound elders for a family member.
	 *
	 * @param authentication Current session identity and granted roles
	 * @return Elder summaries as an array, empty when no elders are available
	 * @author Wang Zhili
	 */
	@GetMapping
	@PreAuthorize("hasAnyRole('MANAGER', 'FAMILY')")
	public List<ElderListItemResponse> list(Authentication authentication) {
		boolean family = authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_FAMILY"));
		var elders = family ? familyElders.listForFamily(authentication.getName()) : service.listElders();
		return elders.stream().map(ElderListItemResponse::from).toList();
	}
}
