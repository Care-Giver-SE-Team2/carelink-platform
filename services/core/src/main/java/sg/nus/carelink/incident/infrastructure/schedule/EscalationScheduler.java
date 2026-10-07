package sg.nus.carelink.incident.infrastructure.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.application.EscalationScanService;

/**
 * The clock that drives UC-SYS02.
 *
 * <p>Deliberately nothing but a trigger. Keeping the schedule here and the rule in
 * {@link EscalationScanService} means the sweep can be unit tested by calling it, without a
 * scheduler, a container or a wait; and swapping the timer for a different mechanism later
 * touches this file only.
 *
 * <p>{@code fixedDelayString} rather than {@code fixedRate}: the delay is measured from the
 * end of the previous sweep, so a slow sweep cannot start overlapping with itself and
 * escalate the same incident twice.
 */
@Component
class EscalationScheduler {

	private final EscalationScanService scan;

	EscalationScheduler(EscalationScanService scan) {
		this.scan = scan;
	}

	@Scheduled(
			fixedDelayString = "${carelink.escalation.scan-interval:PT60S}",
			initialDelayString = "${carelink.escalation.scan-initial-delay:PT30S}")
	void sweep() {
		scan.sweep();
	}
}
