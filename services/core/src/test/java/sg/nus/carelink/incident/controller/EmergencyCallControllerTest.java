package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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

class EmergencyCallControllerTest {

    private final IncidentService incidentService =
            mock(IncidentService.class);

    private final IdentityService identityService =
            mock(IdentityService.class);

    private final ProfileService profileService =
            mock(ProfileService.class);

    private final FamilyAccessQuery familyAccess =
            mock(FamilyAccessQuery.class);

    private final EmergencyCallController controller =
            new EmergencyCallController(
                    incidentService,
                    identityService,
                    profileService,
                    familyAccess
            );

    @Test
    void createsEmergencyCallForAuthenticatedElder() {

        Principal principal = () -> "elder_test";

        AppUser user = new AppUser(
                7L,
                "elder_test",
                "Test Elder",
                Set.of(Role.ELDER),
                true
        );

        Elder elder = new Elder(
                1L,
                7L,
                "Test Elder",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        EmergencyCallCreateRequest request =
                new EmergencyCallCreateRequest(
                        new BigDecimal("1.2966"),
                        new BigDecimal("103.7764"),
                        "Test Elder Home",
                        "EL03 emergency call test"
                );

        Incident created = new Incident(
                1L,
                1L,
                null,
                7L,
                null,
                Incident.Source.ELDER_SOS,
                Incident.Category.SOS,
                Incident.Severity.HIGH,
                Incident.Status.OPEN,
                new BigDecimal("1.2966"),
                new BigDecimal("103.7764"),
                "Test Elder Home",
                "EL03 emergency call test",
                null,
                LocalDateTime.of(
                        2026,
                        9,
                        16,
                        10,
                        0
                ),
                null
        );

        when(identityService.require("elder_test"))
                .thenReturn(user);

        when(profileService.requireElderByUserId(7L))
                .thenReturn(elder);

        when(incidentService.createElderEmergency(
                1L,
                7L,
                new BigDecimal("1.2966"),
                new BigDecimal("103.7764"),
                "Test Elder Home",
                "EL03 emergency call test"
        )).thenReturn(created);

        ResponseEntity<Incident> response =
                controller.createMyEmergencyCall(
                        request,
                        principal
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isEqualTo(created);

        verify(identityService)
                .require("elder_test");

        verify(profileService)
                .requireElderByUserId(7L);

        verify(incidentService)
                .createElderEmergency(
                        1L,
                        7L,
                        new BigDecimal("1.2966"),
                        new BigDecimal("103.7764"),
                        "Test Elder Home",
                        "EL03 emergency call test"
                );
    }

    @Test
    void createsEmergencyCallWhenRequestBodyIsAbsent() {

        Principal principal = () -> "elder_test";

        AppUser user = new AppUser(
                7L,
                "elder_test",
                "Test Elder",
                Set.of(Role.ELDER),
                true
        );

        Elder elder = new Elder(
                1L,
                7L,
                "Test Elder",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Incident created = new Incident(
                2L,
                1L,
                null,
                7L,
                null,
                Incident.Source.ELDER_SOS,
                Incident.Category.SOS,
                Incident.Severity.HIGH,
                Incident.Status.OPEN,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.of(
                        2026,
                        9,
                        16,
                        10,
                        5
                ),
                null
        );

        when(identityService.require("elder_test"))
                .thenReturn(user);

        when(profileService.requireElderByUserId(7L))
                .thenReturn(elder);

        when(incidentService.createElderEmergency(
                1L,
                7L,
                null,
                null,
                null,
                null
        )).thenReturn(created);

        ResponseEntity<Incident> response =
                controller.createMyEmergencyCall(
                        null,
                        principal
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isEqualTo(created);

        verify(identityService)
                .require("elder_test");

        verify(profileService)
                .requireElderByUserId(7L);

        verify(incidentService)
                .createElderEmergency(
                        1L,
                        7L,
                        null,
                        null,
                        null,
                        null
                );
    }

    @Test
    void returnsEmergencyCallWhenItExists() {

        Incident incident = new Incident(
                3L,
                1L,
                null,
                7L,
                null,
                Incident.Source.ELDER_SOS,
                Incident.Category.SOS,
                Incident.Severity.HIGH,
                Incident.Status.OPEN,
                null,
                null,
                "Home",
                "Emergency",
                null,
                LocalDateTime.of(
                        2026,
                        9,
                        16,
                        10,
                        10
                ),
                null
        );

        when(incidentService.findIncident(3L))
                .thenReturn(Optional.of(incident));

        ResponseEntity<Incident> response =
                controller.getEmergencyCall(3L, as("manager_test", Role.MANAGER));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isEqualTo(incident);
    }

    @Test
    void returns404WhenEmergencyCallDoesNotExist() {

        when(incidentService.findIncident(999L))
                .thenReturn(Optional.empty());

        ResponseEntity<Incident> response =
                controller.getEmergencyCall(999L, as("manager_test", Role.MANAGER));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isNull();
    }

    /** An elder reads their own call; anybody else's is not there for them. */
    @Test
    void anElderReadsOnlyTheirOwnCall() {
        when(incidentService.findIncident(3L)).thenReturn(Optional.of(callAbout(1L)));
        when(incidentService.findIncident(4L)).thenReturn(Optional.of(callAbout(2L)));
        when(identityService.require("elder_test"))
                .thenReturn(new AppUser(7L, "elder_test", "Test Elder", Set.of(Role.ELDER), true));
        when(profileService.requireElderByUserId(7L)).thenReturn(elder(1L));

        assertThat(controller.getEmergencyCall(3L, as("elder_test", Role.ELDER)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(controller.getEmergencyCall(4L, as("elder_test", Role.ELDER)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anElderAccountWithNoElderProfileReadsNothing() {
        when(incidentService.findIncident(3L)).thenReturn(Optional.of(callAbout(1L)));
        when(identityService.require("elder_test"))
                .thenReturn(new AppUser(7L, "elder_test", "Test Elder", Set.of(Role.ELDER), true));
        when(profileService.requireElderByUserId(7L))
                .thenThrow(new ResourceNotFound("Elder for user", 7L));

        assertThat(controller.getEmergencyCall(3L, as("elder_test", Role.ELDER)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** A family member reads a call only about an elder they are bound to now. */
    @Test
    void aFamilyMemberReadsOnlyCallsAboutTheirBoundElders() {
        when(incidentService.findIncident(3L)).thenReturn(Optional.of(callAbout(1L)));
        when(incidentService.findIncident(4L)).thenReturn(Optional.of(callAbout(2L)));
        when(familyAccess.readableElderIds("family_test")).thenReturn(Set.of(1L));

        assertThat(controller.getEmergencyCall(3L, as("family_test", Role.FAMILY)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(controller.getEmergencyCall(4L, as("family_test", Role.FAMILY)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aFamilyAccountWithNoFamilyProfileReadsNothing() {
        when(incidentService.findIncident(3L)).thenReturn(Optional.of(callAbout(1L)));
        when(familyAccess.readableElderIds("family_test"))
                .thenThrow(new AccessDeniedException("An authenticated family account is required"));

        assertThat(controller.getEmergencyCall(3L, as("family_test", Role.FAMILY)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Incident callAbout(Long elderId) {
        return new Incident(3L, elderId, null, 7L, null, Incident.Source.ELDER_SOS, Incident.Category.SOS,
                Incident.Severity.HIGH, Incident.Status.OPEN, null, null, "Home", "Emergency", null,
                LocalDateTime.of(2026, 9, 16, 10, 10), null);
    }

    private static Elder elder(Long id) {
        return new Elder(id, 7L, "Test Elder", null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static Authentication as(String username, Role role) {
        return new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
