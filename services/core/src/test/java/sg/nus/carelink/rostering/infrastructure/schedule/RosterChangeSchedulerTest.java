package sg.nus.carelink.rostering.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import sg.nus.carelink.rostering.application.RosterChangeScanService;

/** The trigger of UC-MG04 alternative 4a: that it sweeps, and how often by default. */
class RosterChangeSchedulerTest {

	@Test
	void sweepsForChangesWhoseFamilyDidNotAnswer() {
		RosterChangeScanService scan = mock(RosterChangeScanService.class);

		new RosterChangeScheduler(scan).sweep();

		verify(scan).sweep();
	}

	@Test
	void sweepsEveryMinuteMeasuredFromTheEndOfTheLastSweep() throws NoSuchMethodException {
		Scheduled scheduled = RosterChangeScheduler.class.getDeclaredMethod("sweep").getAnnotation(Scheduled.class);

		assertThat(scheduled.fixedDelayString()).isEqualTo("${carelink.rerostering.scan-interval:PT60S}");
		assertThat(scheduled.initialDelayString()).isEqualTo("${carelink.rerostering.scan-initial-delay:PT45S}");
	}
}
