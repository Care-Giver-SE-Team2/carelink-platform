package sg.nus.carelink.report.messaging;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.domain.model.ReportPeriod;

/** Handles the scheduler's empty ReportRequested payload using the scheduled instant. */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
class ReportRequestedHandler {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    @Bean
    EventHandler<Map> reportRequestedEventHandler(ReportService reports) {
        return new EventHandler<>() {
            @Override
            public String type() {
                return "ReportRequested";
            }

            @Override
            public Class<Map> payloadType() {
                return Map.class;
            }

            @Override
            public void handle(Map payload, EventMetadata metadata) {
                if (metadata.occurredAt() == null) {
                    throw new IllegalArgumentException("ReportRequested requires occurredAt");
                }
                LocalDate scheduledDate = metadata.occurredAt().atZone(SINGAPORE).toLocalDate();
                // Monday's scheduled run covers the Monday-Sunday week just completed.
                ReportPeriod period = ReportPeriod.weekEndingOnOrBefore(scheduledDate.minusDays(1));
                reports.generate(null, period.start(), period.end(), null);
            }
        };
    }
}
