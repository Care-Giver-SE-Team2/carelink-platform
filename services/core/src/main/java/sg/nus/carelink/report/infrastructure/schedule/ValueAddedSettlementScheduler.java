package sg.nus.carelink.report.infrastructure.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.application.ValueAddedServiceDispatchService;

/**
 * Nothing but a trigger: keeps extra-service requests in step with their visits
 * ({@link ValueAddedServiceDispatchService#settleWithVisits}) and follows up the ones the family
 * has not answered ({@link ValueAddedServiceDispatchService#followUpUnanswered}). Which requests
 * and why is decided there. Every five minutes by default ({@code carelink.value-added.settle-interval}).
 */
@Component
class ValueAddedSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(ValueAddedSettlementScheduler.class);

    private final ValueAddedServiceDispatchService dispatch;

    ValueAddedSettlementScheduler(ValueAddedServiceDispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Scheduled(fixedDelayString = "${carelink.value-added.settle-interval:PT5M}",
            initialDelayString = "${carelink.value-added.settle-initial-delay:PT1M}")
    void settle() {
        try {
            int settled = dispatch.settleWithVisits();
            if (settled > 0) {
                log.info("Settled {} value-added service requests with their visits", settled);
            }
        }
        catch (RuntimeException failure) {
            log.warn("Value-added service settlement failed: {}", failure.getClass().getSimpleName());
        }
        try {
            int lapsed = dispatch.followUpUnanswered();
            if (lapsed > 0) {
                log.info("Value-added service requests lapsed unanswered: {}", lapsed);
            }
        }
        catch (RuntimeException failure) {
            log.warn("Value-added service follow-up failed: {}", failure.getClass().getSimpleName());
        }
    }
}
