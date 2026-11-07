package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

class ValueAddedServiceCatalogueServiceTest {

    private final ValueAddedServiceRepository services = mock(ValueAddedServiceRepository.class);
    private final ValueAddedServiceRequestRepository requests = mock(ValueAddedServiceRequestRepository.class);
    private final ValueAddedServiceCatalogueService catalogue = new ValueAddedServiceCatalogueService(services, requests);

    @Test
    void listsEveryServiceByNameWithdrawnOnesTooWithItsRequestsCounted() {
        when(services.findAll()).thenReturn(List.of(
                service(1L, "Hospital escort", ValueAddedService.Status.AVAILABLE),
                service(2L, "companionship", ValueAddedService.Status.UNAVAILABLE),
                service(3L, "Grocery assistance", ValueAddedService.Status.AVAILABLE)));
        when(requests.countByService()).thenReturn(Map.of(1L, 4L));

        List<ValueAddedServiceCatalogueService.Entry> all = catalogue.all();

        assertThat(all).extracting(entry -> entry.service().name())
                .containsExactly("companionship", "Grocery assistance", "Hospital escort");
        assertThat(all).extracting(ValueAddedServiceCatalogueService.Entry::requests).containsExactly(0L, 0L, 4L);
        assertThat(all).extracting(ValueAddedServiceCatalogueService.Entry::removable).containsExactly(true, true, false);
    }

    @Test
    void removesAServiceNobodyAskedFor() {
        when(services.findById(3L)).thenReturn(Optional.of(service(3L, "Massage", ValueAddedService.Status.AVAILABLE)));
        when(requests.existsForService(3L)).thenReturn(false);

        catalogue.remove(3L);

        verify(services).delete(3L);
    }

    @Test
    void aServiceThatWasAskedForIsNotRemoved() {
        when(services.findById(1L)).thenReturn(Optional.of(service(1L, "Hospital escort",
                ValueAddedService.Status.UNAVAILABLE)));
        when(requests.existsForService(1L)).thenReturn(true);

        assertThatThrownBy(() -> catalogue.remove(1L))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo(ValueAddedServiceCatalogueService.IN_USE));
        verify(services, never()).delete(any());
    }

    @Test
    void aServiceAskedForWhileBeingRemovedIsKept() {
        when(services.findById(3L)).thenReturn(Optional.of(service(3L, "Massage", ValueAddedService.Status.AVAILABLE)));
        when(requests.existsForService(3L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_vas_request_service")).when(services).delete(3L);

        assertThatThrownBy(() -> catalogue.remove(3L))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo(ValueAddedServiceCatalogueService.IN_USE));
    }

    @Test
    void removingAnUnknownServiceIsNotFound() {
        when(services.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogue.remove(5L)).isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void providersMayOfferOnlyTheAvailableServices() {
        when(services.findAvailable()).thenReturn(List.of(service(1L, "Hospital escort",
                ValueAddedService.Status.AVAILABLE)));

        assertThat(catalogue.offerableNames()).containsExactly("Hospital escort");
    }

    @Test
    void addsAnAvailableService() {
        when(services.findByName("Medication collection")).thenReturn(Optional.empty());
        when(services.save(any())).thenAnswer(call -> {
            ValueAddedService given = call.getArgument(0);
            return new ValueAddedService(9L, given.name(), given.description(), given.durationMinutes(),
                    given.status(), null, null);
        });

        ValueAddedService added = catalogue.add(" Medication collection ", "From the clinic.", 60);

        assertThat(added.id()).isEqualTo(9L);
        assertThat(added.name()).isEqualTo("Medication collection");
        assertThat(added.available()).isTrue();
    }

    @Test
    void aNameAlreadyInTheCatalogueIsRefused() {
        when(services.findByName("Companionship"))
                .thenReturn(Optional.of(service(2L, "companionship", ValueAddedService.Status.UNAVAILABLE)));

        assertThatThrownBy(() -> catalogue.add("Companionship", null, 60))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo(ValueAddedServiceCatalogueService.NAME_TAKEN));
        verify(services, never()).save(any());
    }

    @Test
    void aNameAddedByAnotherManagerAtTheSameTimeIsRefusedToo() {
        when(services.findByName("Laundry")).thenReturn(Optional.empty());
        when(services.save(any())).thenThrow(new DataIntegrityViolationException("uk_value_added_service_name"));

        assertThatThrownBy(() -> catalogue.add("Laundry", null, 60))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo(ValueAddedServiceCatalogueService.NAME_TAKEN));
    }

    @Test
    void revisingSavesTheNewDetailsUnderTheSameName() {
        when(services.findById(1L)).thenReturn(Optional.of(service(1L, "Hospital escort",
                ValueAddedService.Status.AVAILABLE)));
        when(services.save(any())).thenAnswer(call -> call.getArgument(0));

        catalogue.revise(1L, "Longer now.", 240, ValueAddedService.Status.UNAVAILABLE);

        ArgumentCaptor<ValueAddedService> saved = ArgumentCaptor.forClass(ValueAddedService.class);
        verify(services).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(1L);
        assertThat(saved.getValue().name()).isEqualTo("Hospital escort");
        assertThat(saved.getValue().durationMinutes()).isEqualTo(240);
        assertThat(saved.getValue().status()).isEqualTo(ValueAddedService.Status.UNAVAILABLE);
    }

    @Test
    void revisingAnUnknownServiceIsNotFound() {
        when(services.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogue.revise(5L, null, 60, ValueAddedService.Status.AVAILABLE))
                .isInstanceOf(ResourceNotFound.class);
    }

    private static ValueAddedService service(Long id, String name, ValueAddedService.Status status) {
        return new ValueAddedService(id, name, null, 120, status, null, null);
    }
}
