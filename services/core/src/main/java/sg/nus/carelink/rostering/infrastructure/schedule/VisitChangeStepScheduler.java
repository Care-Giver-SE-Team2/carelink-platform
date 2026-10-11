package sg.nus.carelink.rostering.infrastructure.schedule;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.application.VisitChangeStepScanService;

/**
 * The clock behind the re-rostering saga's retries: once a minute by default, every change visit has not
 * answered yet whose next try is due is sent again. Nothing but a trigger; the rule is in
 * {@link VisitChangeStepScanService}, which a test calls directly.
 *
 * <p>It shares the default plan's sweep settings, so a test that keeps that sweep out of the way keeps
 * this one out too. {@code fixedDelayString}, measured from the end of the last sweep, so a slow sweep
 * cannot overlap itself; a sweep on another replica could, and the steps are safe against that.
 */
@Component
class VisitChangeStepScheduler {

	private final VisitChangeStepScanService scan;

	VisitChangeStepScheduler(VisitChangeStepScanService scan) {
		this.scan = scan;
	}

	@SchedulerLock(name = "rostering.visit-change-steps", lockAtMostFor = "PT10M", lockAtLeastFor = "PT50S")
	@Scheduled(
			fixedDelayString = "${carelink.rerostering.scan-interval:PT60S}",
			initialDelayString = "${carelink.rerostering.scan-initial-delay:PT45S}")
	void sweep() {
		scan.sweep();
	}
}
