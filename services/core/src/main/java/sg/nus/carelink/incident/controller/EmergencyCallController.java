package sg.nus.carelink.incident.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.controller.dto.EmergencyCallCreateRequest;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * HTTP endpoints for UC-EL03: elder one-tap emergency call.
 *
 * <p>An emergency call is represented internally as an Incident whose
 * source is ELDER_SOS. There is intentionally no separate emergency-call
 * persistence model or table.
 */
@RestController
@RequestMapping("/api")
public class EmergencyCallController {

    private final IncidentService incidentService;
    private final IdentityService identityService;
    private final ProfileService profileService;
    private final FamilyAccessQuery familyAccess;

    public EmergencyCallController(
            IncidentService incidentService,
            IdentityService identityService,
            ProfileService profileService,
            FamilyAccessQuery familyAccess) {

        this.incidentService = incidentService;
        this.identityService = identityService;
        this.profileService = profileService;
        this.familyAccess = familyAccess;
    }

    /**
     * Elder triggers an emergency SOS for their own elder profile.
     *
     * POST /api/elders/me/emergency-calls
     *
     * <p>The elder id is intentionally not supplied by the client.
     * The server resolves the authenticated app_user to the linked
     * elder profile, preventing one elder from raising an SOS on
     * behalf of another elder by changing an id in the URL.
     */
    @PostMapping("/elders/me/emergency-calls")
    @PreAuthorize("hasRole('ELDER')")
    public ResponseEntity<Incident> createMyEmergencyCall(
            @Valid @RequestBody(required = false)
            EmergencyCallCreateRequest request,
            Principal principal) {

        AppUser currentUser =
                identityService.require(principal.getName());

        Elder elder =
                profileService.requireElderByUserId(currentUser.id());

        if (request == null) {
            request = new EmergencyCallCreateRequest(
                    null,
                    null,
                    null,
                    null
            );
        }

        Incident incident =
                incidentService.createElderEmergency(
                        elder.id(),
                        currentUser.id(),
                        request.latitude(),
                        request.longitude(),
                        request.locationText(),
                        request.description()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(incident);
    }

    /**
     * Reads the status of an emergency call.
     *
     * GET /api/emergency-calls/{id}
     *
     * <p>A manager may read any. An elder may read only one about themselves, and a family
     * member only one about an elder they are bound to now; for anybody else the answer is
     * 404, the same as for an id that does not exist, so the endpoint does not confirm
     * which ids are in use.
     */
    @GetMapping("/emergency-calls/{id}")
    @PreAuthorize("hasAnyRole('ELDER', 'FAMILY', 'MANAGER')")
    public ResponseEntity<Incident> getEmergencyCall(
            @PathVariable Long id,
            Authentication who) {

        return ResponseEntity.of(
                incidentService.findIncident(id)
                        .filter(incident -> mayRead(who, incident))
        );
    }

    private boolean mayRead(Authentication who, Incident incident) {
        if (hasRole(who, Role.MANAGER)) {
            return true;
        }
        if (hasRole(who, Role.ELDER) && isTheElder(who.getName(), incident.elderId())) {
            return true;
        }
        return hasRole(who, Role.FAMILY) && isBoundTo(who.getName(), incident.elderId());
    }

    private boolean isTheElder(String username, Long elderId) {
        try {
            AppUser user = identityService.require(username);
            return profileService.requireElderByUserId(user.id()).id().equals(elderId);
        } catch (ResourceNotFound noElderProfile) {
            return false;
        }
    }

    private boolean isBoundTo(String username, Long elderId) {
        try {
            return familyAccess.readableElderIds(username).contains(elderId);
        } catch (AccessDeniedException noFamilyProfile) {
            return false;
        }
    }

    private static boolean hasRole(Authentication who, Role role) {
        String authority = "ROLE_" + role.name();
        return who.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}