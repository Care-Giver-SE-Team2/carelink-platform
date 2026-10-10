package sg.nus.carelink.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ValueAddedServiceTest {
    @Test void availableStatusIsAvailable() {
        assertThat(new ValueAddedService(1L, "Hospital escort", null,
                ValueAddedService.Status.AVAILABLE, null, null).available()).isTrue();
    }
    @Test void unavailableStatusIsNotAvailable() {
        assertThat(new ValueAddedService(1L, "Hospital escort", null,
                ValueAddedService.Status.UNAVAILABLE, null, null).available()).isFalse();
    }
}
