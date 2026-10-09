package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringRun;

class RosteringServiceTest {

	private final InMemoryRosteringRunRepository repository = new InMemoryRosteringRunRepository();
	private final RosteringService service = new RosteringService(repository);

	@Test
	void findsWhatWasSaved() {
		RosteringRun saved = repository.save(new RosteringRun(
				null,
				RosteringRun.TriggerType.NEW_VISIT,
				3L,
				RosteringRun.Objective.CONTINUITY,
				5L,
				RosteringRun.Status.PROPOSED,
				7,
				8,
				9,
				new BigDecimal("10.5"),
				LocalDateTime.of(2026, 9, 6, 10, 11),
				LocalDateTime.of(2026, 9, 6, 10, 12)));

		assertThat(service.findRosteringRun(saved.id())).contains(saved);
	}

	@Test
	void isEmptyForAnUnknownId() {
		assertThat(service.findRosteringRun(999L)).isEmpty();
	}
}
