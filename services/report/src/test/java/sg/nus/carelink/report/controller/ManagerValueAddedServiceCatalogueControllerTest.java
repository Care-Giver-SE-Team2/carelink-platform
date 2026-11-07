package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.application.ValueAddedServiceCatalogueService;
import sg.nus.carelink.report.controller.dto.CatalogueServiceResponse;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceCreate;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceResponse;
import sg.nus.carelink.report.controller.dto.ValueAddedServiceUpdate;
import sg.nus.carelink.report.controller.open.OpenServicesController;
import sg.nus.carelink.report.domain.model.ValueAddedService;

class ManagerValueAddedServiceCatalogueControllerTest {

    private final ValueAddedServiceCatalogueService catalogue = mock(ValueAddedServiceCatalogueService.class);
    private final ManagerValueAddedServiceCatalogueController controller =
            new ManagerValueAddedServiceCatalogueController(catalogue);

    @Test
    void listsTheWholeCatalogueSayingWhatMayBeRemoved() {
        when(catalogue.all()).thenReturn(List.of(new ValueAddedServiceCatalogueService.Entry(
                service(1L, "Companionship", ValueAddedService.Status.UNAVAILABLE), 3)));

        CatalogueServiceResponse row = controller.list().get(0);

        assertThat(row.name()).isEqualTo("Companionship");
        assertThat(row.status()).isEqualTo(ValueAddedService.Status.UNAVAILABLE);
        assertThat(row.requestCount()).isEqualTo(3);
        assertThat(row.removable()).isFalse();
    }

    @Test
    void removePassesTheId() {
        controller.remove(9L);

        verify(catalogue).remove(9L);
    }

    @Test
    void addReturnsTheSavedService() {
        when(catalogue.add("Laundry", "Wash and fold.", 90))
                .thenReturn(service(9L, "Laundry", ValueAddedService.Status.AVAILABLE));

        ValueAddedServiceResponse added = controller.add(new ValueAddedServiceCreate("Laundry", "Wash and fold.", 90));

        assertThat(added.id()).isEqualTo(9L);
    }

    @Test
    void revisePassesWhatMayChange() {
        when(catalogue.revise(1L, null, 60, ValueAddedService.Status.AVAILABLE))
                .thenReturn(service(1L, "Companionship", ValueAddedService.Status.AVAILABLE));

        ValueAddedServiceResponse revised = controller.revise(1L,
                new ValueAddedServiceUpdate(null, 60, ValueAddedService.Status.AVAILABLE));

        assertThat(revised.status()).isEqualTo(ValueAddedService.Status.AVAILABLE);
    }

    @Test
    void theOpenListNamesTheServicesAProviderMayOffer() {
        when(catalogue.offerableNames()).thenReturn(List.of("Hospital escort", "Laundry"));

        assertThat(new OpenServicesController(catalogue).list().services())
                .containsExactly("Hospital escort", "Laundry");
    }

    private static ValueAddedService service(Long id, String name, ValueAddedService.Status status) {
        return new ValueAddedService(id, name, null, 60, status, null, null);
    }
}
