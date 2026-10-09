package sg.nus.carelink.incident.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Verifies independent first facts without persistence or Spring. @author Wang Zhili */
class IncidentAcknowledgementTest {

	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 10, 7, 16, 0);
	private final IncidentAcknowledgement empty = new IncidentAcknowledgement(1L, 601L, 42L, null, null, null, FIRST);

	@Test
	void viewAndAcknowledgementPreserveTheirFirstValuesInEitherOrder() {
		var viewed = empty.viewAt(FIRST);
		assertThat(viewed.acknowledgedAt()).isNull();
		assertThat(viewed.viewAt(FIRST.plusMinutes(1))).isSameAs(viewed);
		var acknowledged = viewed.acknowledgeAt(FIRST.plusMinutes(2), " First note ");
		assertThat(acknowledged.viewedAt()).isEqualTo(FIRST);
		assertThat(acknowledged.acknowledgedAt()).isEqualTo(FIRST.plusMinutes(2));
		assertThat(acknowledged.responseNote()).isEqualTo(" First note ");
		assertThat(acknowledged.acknowledgeAt(FIRST.plusMinutes(3), "Replacement")).isSameAs(acknowledged);
		assertThat(acknowledged.viewAt(FIRST.plusMinutes(4))).isSameAs(acknowledged);
		assertThat(acknowledged.id()).isEqualTo(1L);
		assertThat(acknowledged.createdAt()).isEqualTo(FIRST);
	}

	@Test
	void acknowledgeFirstNeverInventsAViewOrOverwritesAnInitiallyNullNote() {
		var acknowledged = empty.acknowledgeAt(FIRST, null);
		assertThat(acknowledged.viewedAt()).isNull();
		assertThat(acknowledged.acknowledgeAt(FIRST.plusMinutes(1), "Later note").responseNote()).isNull();
		var viewed = acknowledged.viewAt(FIRST.plusMinutes(2));
		assertThat(viewed.viewedAt()).isEqualTo(FIRST.plusMinutes(2));
		assertThat(viewed.acknowledgedAt()).isEqualTo(FIRST);
		assertThat(viewed.responseNote()).isNull();
		assertThat(empty.viewedAt()).isNull();
		assertThat(empty.acknowledgedAt()).isNull();
	}

	@Test
	void aReceiptRequiresAServerTime() {
		assertThatThrownBy(() -> empty.viewAt(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> empty.acknowledgeAt(null, "Note")).isInstanceOf(NullPointerException.class);
	}
}
