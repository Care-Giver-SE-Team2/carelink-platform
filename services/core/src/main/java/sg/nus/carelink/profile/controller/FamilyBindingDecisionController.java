package sg.nus.carelink.profile.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.profile.application.FamilyBindingService;
import sg.nus.carelink.profile.controller.dto.FamilyBindingDecisionRequest;
import sg.nus.carelink.profile.controller.dto.FamilyIncomingBindingResponse;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

@RestController
@RequestMapping("/api/family/family-bindings")
@PreAuthorize("hasRole('FAMILY')")
public class FamilyBindingDecisionController {
    private final IdentityService identity;
    private final FamilyBindingService service;

    public FamilyBindingDecisionController(IdentityService identity, FamilyBindingService service) {
        this.identity = identity;
        this.service = service;
    }

    @GetMapping
    public List<FamilyIncomingBindingResponse> list(Principal principal) {
        Long userId = identity.require(principal.getName()).id();
        return service.listForFamilyUser(userId).stream().map(this::response).toList();
    }

    @PostMapping("/{bindingId}/decision")
    public FamilyIncomingBindingResponse decide(@PathVariable Long bindingId,
            @Valid @RequestBody FamilyBindingDecisionRequest request, Principal principal) {
        Long userId = identity.require(principal.getName()).id();
        return response(service.decideForFamilyUser(userId, bindingId, request.approve()));
    }

    private FamilyIncomingBindingResponse response(ElderFamilyBinding binding) {
        return FamilyIncomingBindingResponse.from(binding, service.requireElder(binding.elderId()).fullName());
    }
}
