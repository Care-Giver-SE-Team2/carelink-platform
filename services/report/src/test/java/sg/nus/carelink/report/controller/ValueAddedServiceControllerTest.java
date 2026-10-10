package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.application.Accounts;
import sg.nus.carelink.report.application.ValueAddedServiceDispatchService;
import sg.nus.carelink.report.application.ValueAddedServiceRequestService;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceDecisionRequest;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceRequestCreate;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceRequestResponse;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceResponse;
import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

class ValueAddedServiceControllerTest {

    private Accounts accounts;
    private ValueAddedServiceRequestService service;
    private ValueAddedServiceDispatchService dispatch;
    private ValueAddedServiceController controller;

    private Principal elderPrincipal;
    private Principal familyPrincipal;

    @BeforeEach
    void setUp() {
        accounts = mock(Accounts.class);
        service = mock(ValueAddedServiceRequestService.class);
        dispatch = mock(ValueAddedServiceDispatchService.class);

        controller =
                new ValueAddedServiceController(
                        accounts,
                        service,
                        dispatch
                );

        elderPrincipal =
                () -> "elder_test";

        familyPrincipal =
                () -> "family_test";

        when(accounts.idOf("elder_test"))
                .thenReturn(
                        1L
                );
    }

    @Test
    void returnsAvailableCatalogue() {
        when(service.availableServices())
                .thenReturn(
                        List.of(catalogue())
                );

        List<ValueAddedServiceResponse> result =
                controller.catalogue();

        assertThat(result)
                .hasSize(1);

        assertThat(result.get(0).id())
                .isEqualTo(2L);

        assertThat(result.get(0).name())
                .isEqualTo(
                        "Hospital escort"
                );
    }

    @Test
    void listsCurrentElderRequestsUsingAuthenticatedAccount() {
        when(service.listForElderUser(1L))
                .thenReturn(
                        List.of(pending())
                );

        when(service.requireService(2L))
                .thenReturn(catalogue());

        List<ValueAddedServiceRequestResponse> result =
                controller.elderRequests(
                        elderPrincipal
                );

        assertThat(result)
                .hasSize(1);

        assertThat(result.get(0).serviceName())
                .isEqualTo(
                        "Hospital escort"
                );

        assertThat(result.get(0).status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.PENDING_APPROVAL
                );

        verify(service)
                .listForElderUser(1L);
    }

    @Test
    void createsElderRequest() {
        ValueAddedServiceRequestCreate body =
                new ValueAddedServiceRequestCreate(
                        2L,
                        LocalDateTime.of(
                                2026,
                                10,
                                10,
                                10,
                                0
                        ),
                        "Need assistance"
                );

        when(
                service.requestForElderUser(
                        1L,
                        2L,
                        body.requestedSchedule(),
                        "Need assistance"
                )
        ).thenReturn(
                pending()
        );

        when(service.requireService(2L))
                .thenReturn(catalogue());

        ValueAddedServiceRequestResponse response =
                controller.create(
                        body,
                        elderPrincipal
                );

        assertThat(response.id())
                .isEqualTo(5L);

        assertThat(response.elderId())
                .isEqualTo(10L);

        assertThat(response.status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.PENDING_APPROVAL
                );
    }

    @Test
    void listsRequestsForFamilySelectedElder() {
        when(
                service.listForFamily(
                        "family_test",
                        10L
                )
        ).thenReturn(
                List.of(pending())
        );

        when(service.requireService(2L))
                .thenReturn(catalogue());

        List<ValueAddedServiceRequestResponse> response =
                controller.familyRequests(
                        10L,
                        familyPrincipal
                );

        assertThat(response)
                .hasSize(1);

        verify(service)
                .listForFamily(
                        "family_test",
                        10L
                );
    }

    @Test
    void familyApprovesRequest() {
        ValueAddedServiceRequest dispatched =
                new ValueAddedServiceRequest(
                        5L,
                        10L,
                        2L,
                        null,
                        20L,
                        77L,
                        LocalDateTime.of(
                                2026,
                                10,
                                10,
                                10,
                                0
                        ),
                        "Need assistance",
                        ValueAddedServiceRequest.Status.DISPATCHED,
                        LocalDateTime.of(
                                2026,
                                10,
                                7,
                                17,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                10,
                                7,
                                12,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                10,
                                7,
                                17,
                                0
                        )
                );

        when(
                service.decideForFamily(
                        "family_test",
                        5L,
                        ValueAddedServiceRequestService.Decision.APPROVED
                )
        ).thenReturn(dispatched);

        when(service.requireService(2L))
                .thenReturn(catalogue());

        ValueAddedServiceRequestResponse response =
                controller.decide(
                        5L,
                        new ValueAddedServiceDecisionRequest(
                                ValueAddedServiceRequestService.Decision.APPROVED
                        ),
                        familyPrincipal
                );

        assertThat(response.status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.DISPATCHED
                );

        assertThat(response.visitId())
                .isEqualTo(77L);
    }

    @Test
    void familyRejectsRequest() {
        ValueAddedServiceRequest rejected =
                pending().reject(
                        20L,
                        LocalDateTime.of(
                                2026,
                                10,
                                7,
                                17,
                                0
                        )
                );

        when(
                service.decideForFamily(
                        "family_test",
                        5L,
                        ValueAddedServiceRequestService.Decision.REJECTED
                )
        ).thenReturn(rejected);

        when(service.requireService(2L))
                .thenReturn(catalogue());

        ValueAddedServiceRequestResponse response =
                controller.decide(
                        5L,
                        new ValueAddedServiceDecisionRequest(
                                ValueAddedServiceRequestService.Decision.REJECTED
                        ),
                        familyPrincipal
                );

        assertThat(response.status())
                .isEqualTo(
                        ValueAddedServiceRequest.Status.REJECTED
                );

        assertThat(response.visitId())
                .isNull();
    }

    private ValueAddedService catalogue() {
        return new ValueAddedService(
                2L,
                "Hospital escort",
                "Escort to medical appointments",
                ValueAddedService.Status.AVAILABLE,
                null,
                null
        );
    }

    private ValueAddedServiceRequest pending() {
        return new ValueAddedServiceRequest(
                5L,
                10L,
                2L,
                null,
                null,
                null,
                LocalDateTime.of(
                        2026,
                        10,
                        10,
                        10,
                        0
                ),
                "Need assistance",
                ValueAddedServiceRequest.Status.PENDING_APPROVAL,
                null,
                LocalDateTime.of(
                        2026,
                        10,
                        7,
                        12,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        10,
                        7,
                        12,
                        0
                )
        );
    }

    @Test
    void elderWithdrawsTheirRequestThroughTheDispatchService() {
        var cancelled = new ValueAddedServiceRequest(5L, 10L, 2L, null, null, null,
                LocalDateTime.of(2026, 10, 10, 10, 0), null, ValueAddedServiceRequest.Status.CANCELLED,
                null, null, null);
        when(dispatch.cancelForElderUser(1L, 5L)).thenReturn(cancelled);
        when(service.requireService(2L)).thenReturn(catalogue());

        var result = controller.withdraw(5L, elderPrincipal);

        assertThat(result.status()).isEqualTo(ValueAddedServiceRequest.Status.CANCELLED);
    }

    @Test
    void familyRequestsOnTheEldersBehalf() {
        var schedule = LocalDateTime.of(2026, 10, 10, 10, 0);
        var dispatched = new ValueAddedServiceRequest(6L, 10L, 2L, 20L, 20L, 77L, schedule, "Wheelchair",
                ValueAddedServiceRequest.Status.DISPATCHED, schedule.minusDays(1), null, null);
        when(service.requestForFamily("family_test", 10L, 2L, schedule, "Wheelchair")).thenReturn(dispatched);
        when(service.requireService(2L)).thenReturn(catalogue());

        var result = controller.familyCreate(
                new sg.nus.carelink.report.controller.dto.FamilyValueAddedServiceRequestCreate(10L, 2L, schedule, "Wheelchair"),
                familyPrincipal);

        assertThat(result.status()).isEqualTo(ValueAddedServiceRequest.Status.DISPATCHED);
        assertThat(result.requestedByFamilyMemberId()).isEqualTo(20L);
        assertThat(controller.familyCatalogue()).isEqualTo(controller.catalogue());
    }
}
