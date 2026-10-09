package sg.nus.carelink.visit.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.nus.carelink.shared.config.CareLinkProperties;
import sg.nus.carelink.visit.application.MissedCheckInScanService;
import sg.nus.carelink.visit.domain.service.MissedCheckInPolicy;

@Configuration
@EnableConfigurationProperties(MissedCheckInProperties.class)
class MissedCheckInConfig {
    @Bean MissedCheckInPolicy missedCheckInPolicy(CareLinkProperties common, MissedCheckInProperties scan) {
        return new MissedCheckInPolicy(common.lateArrivalThreshold(), scan.lookback());
    }
    @Bean MissedCheckInScanService.Settings missedCheckInSettings(MissedCheckInProperties scan) {
        return new MissedCheckInScanService.Settings(scan.enabled(), scan.batchSize());
    }
}
