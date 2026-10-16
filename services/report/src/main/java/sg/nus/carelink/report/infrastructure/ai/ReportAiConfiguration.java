package sg.nus.carelink.report.infrastructure.ai;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.nus.carelink.report.application.ReportAiEnricher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration(proxyBeanMethods = false)
public class ReportAiConfiguration {
    @Bean
    @ConditionalOnProperty(name = "carelink.report.ai.enabled", havingValue = "false", matchIfMissing = true)
    ReportAiSummary disabledAi() { return ignored -> Optional.empty(); }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "carelink.report.ai.enabled", havingValue = "true")
    BedrockRuntimeClient bedrockRuntimeClient(@Value("${carelink.report.ai.region:ap-southeast-1}") String region) {
        return BedrockRuntimeClient.builder().region(Region.of(region))
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))
                .apiCallAttemptTimeout(Duration.ofSeconds(4))
                .retryStrategy(r -> r.maxAttempts(1)))
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "carelink.report.ai.enabled", havingValue = "true")
    ReportAiSummary bedrockAi(BedrockRuntimeClient client,
            @Value("${carelink.report.ai.model-id}") String modelId) {
        return new BedrockSummaryAdapter(client, modelId, Clock.systemUTC(), 2, 3, Duration.ofSeconds(60));
    }

    @Bean
    ReportAiEnricher reportAiEnricher(ReportAiSummary summary) { return new ReportAiEnricher(summary); }
}
