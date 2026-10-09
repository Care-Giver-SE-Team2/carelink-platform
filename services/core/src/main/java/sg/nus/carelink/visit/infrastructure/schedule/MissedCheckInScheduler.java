package sg.nus.carelink.visit.infrastructure.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sg.nus.carelink.visit.application.MissedCheckInScanService;

@Component
class MissedCheckInScheduler {
    private static final Logger log = LoggerFactory.getLogger(MissedCheckInScheduler.class);
    private final MissedCheckInScanService scan;
    MissedCheckInScheduler(MissedCheckInScanService scan) { this.scan = scan; }
    @Scheduled(fixedDelayString="${carelink.missed-check-in.scan-interval:PT60S}",
            initialDelayString="${carelink.missed-check-in.scan-initial-delay:PT30S}")
    void scan() {
        try {
            var result = scan.scan();
            if (result.considered() > 0) log.info("SYS03 considered={}, triggered={}, failed={}", result.considered(), result.triggered(), result.failed());
        } catch (RuntimeException failure) {
            log.warn("SYS03 candidate scan failed: {}", failure.getClass().getSimpleName());
        }
    }
}
