package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.application.ValueAddedServiceDispatchService;
import sg.nus.carelink.report.application.ValueAddedServiceDispatchService.ManagedRequest;
import sg.nus.carelink.report.controller.dto.CaregiverAssignmentRequest;
import sg.nus.carelink.report.controller.dto.CaregiverOptionResponse;
import sg.nus.carelink.report.controller.dto.ManagedValueAddedServiceRequestResponse;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.rostering.application.VisitCover;

class ManagerValueAddedServiceControllerTest {

    private static final LocalDateTime SCHEDULE = LocalDateTime.of(2026, 10, 10, 10, 0);
    private final Principal manager = () -> "manager";

    private ValueAddedServiceDispatchService service;
    private ManagerValueAddedServiceController controller;

    @BeforeEach
    void setUp() {
        service = mock(ValueAddedServiceDispatchService.class);
        controller = new ManagerValueAddedServiceController(service);
    }

    @Test
    void listFlattensEachRequestWithItsVisit() {
        when(service.listForManager()).thenReturn(List.of(managed(null, "SCHEDULED")));

        ManagedValueAddedServiceRequestResponse row = controller.list().get(0);

        assertThat(row.id()).isEqualTo(1L);
        assertThat(row.serviceName()).isEqualTo("Hospital escort");
        assertThat(row.visitId()).isEqualTo(40L);
        assertThat(row.visitStatus()).isEqualTo("SCHEDULED");
        assertThat(row.caregiverId()).isNull();
        assertThat(row.needsCaregiver()).isTrue();
    }

    @Test
    void optionsSayWhetherEachCaregiverIsEligible() {
        when(service.caregiverOptions(1L)).thenReturn(List.of(
                new VisitCover.Option(9L, "Farah", 1, "Continuity"),
                new VisitCover.Option(5L, "Aisha", null, "Busy then")));

        List<CaregiverOptionResponse> options = controller.caregiverOptions(1L);

        assertThat(options).extracting(CaregiverOptionResponse::eligible).containsExactly(true, false);
        assertThat(options.get(1).reason()).isEqualTo("Busy then");
    }

    @Test
    void assignPassesTheManagersName() {
        when(service.assignCaregiver(1L, 9L, "manager")).thenReturn(managed(9L, "SCHEDULED"));

        var row = controller.assignCaregiver(1L, new CaregiverAssignmentRequest(9L), manager);

        assertThat(row.caregiverId()).isEqualTo(9L);
        assertThat(row.needsCaregiver()).isFalse();
    }

    @Test
    void cancelReturnsTheCancelledRequest() {
        ManagedRequest cancelled = new ManagedRequest(request(ValueAddedServiceRequest.Status.CANCELLED),
                "Hospital escort", "CANCELLED", 9L);
        when(service.cancel(1L, "manager")).thenReturn(cancelled);

        assertThat(controller.cancel(1L, manager).status()).isEqualTo(ValueAddedServiceRequest.Status.CANCELLED);
    }

    private static ManagedRequest managed(Long caregiverId, String visitStatus) {
        return new ManagedRequest(request(ValueAddedServiceRequest.Status.DISPATCHED), "Hospital escort",
                visitStatus, caregiverId);
    }

    private static ValueAddedServiceRequest request(ValueAddedServiceRequest.Status status) {
        return new ValueAddedServiceRequest(1L, 7L, 2L, null, 3L, 40L, SCHEDULE, null, status,
                SCHEDULE.minusDays(2), SCHEDULE.minusDays(3), null);
    }
}
