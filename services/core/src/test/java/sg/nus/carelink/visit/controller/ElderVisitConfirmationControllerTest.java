package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.shared.security.Role;
import sg.nus.carelink.visit.application.VisitService;
import sg.nus.carelink.visit.controller.dto.ElderVisitConfirmationRequest;
import sg.nus.carelink.visit.controller.dto.ElderVisitConfirmationResponse;
import sg.nus.carelink.visit.controller.dto.PendingElderVisitResponse;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.Visit;

class ElderVisitConfirmationControllerTest {

    private IdentityService identityService;
    private ProfileService profileService;
    private VisitService visitService;

    private ElderVisitConfirmationController controller;

    private Principal principal;
    private AppUser elderUser;
    private Elder elder;

    @BeforeEach
    void setUp() {
        identityService =
                mock(IdentityService.class);

        profileService =
                mock(ProfileService.class);

        visitService =
                mock(VisitService.class);

        controller =
                new ElderVisitConfirmationController(
                        identityService,
                        profileService,
                        visitService
                );

        principal =
                () -> "elder_test";

        elderUser =
                new AppUser(
                        1L,
                        "elder_test",
                        "Test Elder",
                        Set.of(Role.ELDER),
                        true
                );

        elder =
                new Elder(
                        1L,
                        1L,
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
                        Elder.ContinuityPreference.PREFERRED,
                        null,
                        null,
                        null
                );

        when(
                identityService.require(
                        "elder_test"
                )
        ).thenReturn(
                elderUser
        );

        when(
                profileService.requireElderByUserId(
                        1L
                )
        ).thenReturn(
                elder
        );
    }

    @Test
    void listsVisitsAwaitingConfirmationForCurrentElder() {
        Visit visit =
                completedVisit();

        when(
                visitService.findAwaitingConfirmation(
                        1L
                )
        ).thenReturn(
                List.of(visit)
        );

        List<PendingElderVisitResponse> response =
                controller.awaitingConfirmation(
                        principal
                );

        assertThat(response)
                .hasSize(1);

        assertThat(response.get(0).visitId())
                .isEqualTo(15L);

        assertThat(response.get(0).serviceType())
                .isEqualTo(
                        "Personal care"
                );

        assertThat(response.get(0).status())
                .isEqualTo(
                        Visit.Status.COMPLETED
                );

        verify(visitService)
                .findAwaitingConfirmation(
                        1L
                );
    }

    @Test
    void returnsEmptyListWhenNothingNeedsConfirmation() {
        when(
                visitService.findAwaitingConfirmation(
                        1L
                )
        ).thenReturn(
                List.of()
        );

        assertThat(
                controller.awaitingConfirmation(
                        principal
                )
        ).isEmpty();
    }

    @Test
    void submitsConfirmationForCurrentElder() {
        ElderVisitConfirmationRequest request =
                new ElderVisitConfirmationRequest(
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "Good service"
                );

        ElderConfirmation saved =
                new ElderConfirmation(
                        8L,
                        15L,
                        1L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "Good service",
                        LocalDateTime.of(
                                2026,
                                9,
                                24,
                                11,
                                0
                        )
                );

        when(
                visitService.submitElderConfirmation(
                        1L,
                        1L,
                        15L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "Good service"
                )
        ).thenReturn(saved);

        ElderVisitConfirmationResponse response =
                controller.confirm(
                        15L,
                        request,
                        principal
                );

        assertThat(response.id())
                .isEqualTo(8L);

        assertThat(response.visitId())
                .isEqualTo(15L);

        assertThat(response.confirmationStatus())
                .isEqualTo(
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED
                );

        assertThat(response.rating())
                .isEqualTo((byte) 5);

        verify(visitService)
                .submitElderConfirmation(
                        1L,
                        1L,
                        15L,
                        ElderConfirmation
                                .ConfirmationStatus
                                .CONFIRMED,
                        (byte) 5,
                        "Good service"
                );
    }

    private Visit completedVisit() {
        return new Visit(
                15L,
                1L,
                3L,
                4L,
                null,
                "Personal care",
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        10,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        11,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        10,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        10,
                        55
                ),
                Visit.Status.COMPLETED,
                null,
                13L,
                1,
                LocalDateTime.of(
                        2026,
                        9,
                        23,
                        10,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        9,
                        24,
                        10,
                        55
                )
        );
    }
}