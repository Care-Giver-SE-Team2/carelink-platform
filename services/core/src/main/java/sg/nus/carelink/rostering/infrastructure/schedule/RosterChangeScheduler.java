package sg.nus.carelink.rostering.infrastructure.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.application.RosterChangeScanService;

/**
 * The clock behind UC-MG04 alternative 4a: once a minute by default, every change whose family
 * did not answer in time gets the default plan. Nothing but a trigger; the rule is in
 * {@link RosterChangeScanService}, which a test calls directly.
 *
 * <p>{@code fixedDelayString}, measured from the end of the last sweep, so a slow sweep cannot
 * overlap itself and settle a change twice.
 */
@Component
class RosterChangeScheduler {

	private final RosterChangeScanService scan;

	RosterChangeScheduler(RosterChangeScanService scan) {
		this.scan = scan;
	}

	@Scheduled(
			fixedDelayString = "${carelink.rerostering.scan-interval:PT60S}",
			initialDelayString = "${carelink.rerostering.scan-initial-delay:PT45S}")
	void sweep() {
		scan.sweep();
	}
}
