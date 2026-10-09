package sg.nus.carelink.visit.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.nus.carelink.shared.config.CareLinkProperties;
import sg.nus.carelink.visit.domain.model.VisitExecutionPolicy;

@Configuration
class VisitExecutionConfig {
    @Bean VisitExecutionPolicy executionPolicy(@Value("${carelink.visit-execution.early-arrival:PT30M}") Duration early, CareLinkProperties thresholds) {
        if (early.isNegative() || thresholds.lateArrivalThreshold().isNegative()) throw new IllegalArgumentException("Arrival thresholds must be nonnegative");
        return new VisitExecutionPolicy(early, thresholds.lateArrivalThreshold());
    }
}
