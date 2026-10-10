package sg.nus.carelink.visit.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("carelink.missed-check-in")
public record MissedCheckInProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("PT60S") Duration scanInterval, @DefaultValue("PT30S") Duration scanInitialDelay,
        @DefaultValue("PT24H") Duration lookback, @DefaultValue("200") int batchSize) {
    public MissedCheckInProperties {
        if (scanInterval == null || scanInterval.isNegative() || scanInterval.isZero()
                || scanInitialDelay == null || scanInitialDelay.isNegative() || scanInitialDelay.isZero()
                || lookback == null || lookback.isNegative() || lookback.isZero() || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("SYS03 durations must be positive; batch-size must be 1..1000");
        }
    }
}
