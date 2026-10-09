package sg.nus.carelink.profile.controller;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.FamilyElderProfile;
import sg.nus.carelink.profile.application.FamilyElderProfileService;
import sg.nus.carelink.profile.controller.dto.FamilyElderUpdateRequest;

/** Basic profiles of elders already bound to the authenticated family member. */
@RestController
@RequestMapping("/api/family/elders")
@PreAuthorize("hasRole('FAMILY')")
public class FamilyElderProfileController {

    private final FamilyElderProfileService profiles;

    public FamilyElderProfileController(FamilyElderProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public List<FamilyElderProfile> list(Principal principal) {
        return profiles.list(principal.getName());
    }

    @GetMapping("/{elderId}")
    public FamilyElderProfile get(@PathVariable Long elderId, Principal principal) {
        return profiles.get(principal.getName(), elderId);
    }

    @PutMapping("/{elderId}")
    public FamilyElderProfile update(@PathVariable Long elderId,
            @Valid @RequestBody FamilyElderUpdateRequest request, Principal principal) {
        return profiles.update(principal.getName(), elderId, request.toDetails());
    }
}
