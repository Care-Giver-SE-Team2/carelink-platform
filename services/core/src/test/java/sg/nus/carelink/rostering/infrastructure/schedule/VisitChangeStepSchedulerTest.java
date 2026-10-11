package sg.nus.carelink.rostering.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import sg.nus.carelink.rostering.application.VisitChangeStepScanService;

/** The trigger of the re-rostering saga's retries: that it sweeps, how often, and under which lock. */
class VisitChangeStepSchedulerTest {

	@Test
	void sweepsForChangesVisitHasNotAnsweredYet() {
		VisitChangeStepScanService scan = mock(VisitChangeStepScanService.class);

		new VisitChangeStepScheduler(scan).sweep();

		verify(scan).sweep();
	}

	@Test
	void sweepsEveryMinuteOnOneReplicaWithTheDefaultPlansSettings() throws NoSuchMethodException {
		Method sweep = VisitChangeStepScheduler.class.getDeclaredMethod("sweep");

		assertThat(sweep.getAnnotation(Scheduled.class).fixedDelayString())
				.isEqualTo("${carelink.rerostering.scan-interval:PT60S}");
		assertThat(sweep.getAnnotation(Scheduled.class).initialDelayString())
				.isEqualTo("${carelink.rerostering.scan-initial-delay:PT45S}");
		assertThat(sweep.getAnnotation(SchedulerLock.class).name()).isEqualTo("rostering.visit-change-steps");
	}
}
