package sg.nus.carelink.visit.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MissedCheckInPropertiesTest {
    @Configuration @EnableConfigurationProperties(MissedCheckInProperties.class) static class Setup {}
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Setup.class);
    @Test void defaultsAndDisableBindWithoutChangingCommonLateness() {
        runner.run(context->{
            assertThat(context).hasNotFailed();var p=context.getBean(MissedCheckInProperties.class);
            assertThat(p.enabled()).isTrue();assertThat(p.scanInterval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(p.lookback()).isEqualTo(Duration.ofDays(1));assertThat(p.batchSize()).isEqualTo(200);
        });
        runner.withPropertyValues("carelink.missed-check-in.enabled=false").run(c->assertThat(c.getBean(MissedCheckInProperties.class).enabled()).isFalse());
    }
    @Test void illegalDurationsAndBatchBoundsRejectStartup() {
        for(var bad: new String[]{"scan-interval=PT0S","scan-interval=-PT1S","scan-initial-delay=PT0S","lookback=PT0S","lookback=-PT1S","batch-size=0","batch-size=1001"}) {
            runner.withPropertyValues("carelink.missed-check-in."+bad).run(c->assertThat(c).hasFailed());
        }
    }
}
