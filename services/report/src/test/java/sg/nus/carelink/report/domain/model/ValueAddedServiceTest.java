package sg.nus.carelink.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

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

    @Test void anAddedServiceIsAvailableWithItsNameAndDescriptionStripped() {
        ValueAddedService added = ValueAddedService.added("  Medication collection ", "  From the clinic. ", 60);

        assertThat(added.id()).isNull();
        assertThat(added.name()).isEqualTo("Medication collection");
        assertThat(added.description()).isEqualTo("From the clinic.");
        assertThat(added.durationMinutes()).isEqualTo(60);
        assertThat(added.available()).isTrue();
    }

    @Test void aBlankDescriptionIsNone() {
        assertThat(ValueAddedService.added("Laundry", "   ", 60).description()).isNull();
    }

    @Test void anAddedServiceNeedsAName() {
        assertThatThrownBy(() -> ValueAddedService.added("   ", null, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValueAddedService.added(null, null, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValueAddedService.added("x".repeat(101), null, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aServiceLastsAMinuteToTwelveHours() {
        assertThat(ValueAddedService.added("Short", null, 1).durationMinutes()).isEqualTo(1);
        assertThat(ValueAddedService.added("Long", null, 720).durationMinutes()).isEqualTo(720);
        assertThatThrownBy(() -> ValueAddedService.added("None", null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValueAddedService.added("Too long", null, 721))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void revisingKeepsTheIdNameAndTimes() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 9, 0);
        ValueAddedService escort = new ValueAddedService(4L, "Hospital escort", "Old", 180,
                ValueAddedService.Status.AVAILABLE, created, created);

        ValueAddedService revised = escort.revised("New", 240, ValueAddedService.Status.UNAVAILABLE);

        assertThat(revised.id()).isEqualTo(4L);
        assertThat(revised.name()).isEqualTo("Hospital escort");
        assertThat(revised.description()).isEqualTo("New");
        assertThat(revised.durationMinutes()).isEqualTo(240);
        assertThat(revised.available()).isFalse();
        assertThat(revised.createdAt()).isEqualTo(created);
    }

    @Test void revisingChecksTheLength() {
        ValueAddedService escort = new ValueAddedService(4L, "Hospital escort", null,
                ValueAddedService.Status.AVAILABLE, null, null);

        assertThatThrownBy(() -> escort.revised(null, 0, ValueAddedService.Status.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
