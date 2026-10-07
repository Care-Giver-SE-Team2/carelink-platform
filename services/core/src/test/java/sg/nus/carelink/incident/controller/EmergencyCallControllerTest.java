package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.controller.dto.EmergencyCallCreateRequest;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.shared.security.Role;

class EmergencyCallControllerTest {

    private final IncidentService incidentService =
            mock(IncidentService.class);

    private final IdentityService identityService =
            mock(IdentityService.class);

    private final ProfileService profileService =
            mock(ProfileService.class);

    private final EmergencyCallController controller =
            new EmergencyCallController(
                    incidentService,
                    identityService,
                    profileService
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
                controller.getEmergencyCall(3L);

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
                controller.getEmergencyCall(999L);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isNull();
    }
}