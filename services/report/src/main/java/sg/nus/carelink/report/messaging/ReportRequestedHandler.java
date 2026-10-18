
package sg.nus.carelink.report.messaging;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.events.PreparedEventHandler;
import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.domain.model.ReportPeriod;

/**
 * Handles scheduled ReportRequested events.
 *
 * <p>The event timestamp is interpreted in the Singapore
 * timezone. The generated reports cover the preceding
 * completed Monday-Sunday reporting period.
 *
 * <p>Report preparation, including Bedrock AI enrichment,
 * happens outside the SQS consumer's write transaction.
 * The returned Runnable performs persistence inside the
 * consumer's atomic transaction.
 *
 * <p>An optional preparation delay is available exclusively
 * under the fault-drill Spring profile. It is disabled in
 * normal worker operation.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
class ReportRequestedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ReportRequestedHandler.class);

    private static final ZoneId SINGAPORE =
            ZoneId.of("Asia/Singapore");

    @Bean
    EventHandler<Map> reportRequestedEventHandler(
            ReportService reports,
            @Value(
                "${carelink.report.worker.test-prepare-delay:0s}"
            )
            Duration configuredDelay,
            Environment environment) {

        boolean faultDrill = Arrays.asList(
                environment.getActiveProfiles()
        ).contains("fault-drill");

        Duration prepareDelay = faultDrill
                ? configuredDelay
                : Duration.ZERO;

        if (configuredDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "Report worker preparation delay "
                    + "must not be negative");
        }

        if (!faultDrill && !configuredDelay.isZero()) {
            log.warn(
                    "Ignoring report worker preparation delay "
                    + "{} because fault-drill profile is inactive",
                    configuredDelay);
        }

        if (faultDrill) {
            log.warn(
                    "Report Worker FAULT-DRILL mode enabled. "
                    + "Preparation delay: {}",
                    prepareDelay);
        }

        return new PreparedEventHandler<>() {

            @Override
            public String type() {
                return "ReportRequested";
            }

            @Override
            public Class<Map> payloadType() {
                return Map.class;
            }

            @Override
            public Runnable prepare(
                    Map payload,
                    EventMetadata metadata) {

                if (metadata.occurredAt() == null) {
                    throw new IllegalArgumentException(
                            "ReportRequested requires occurredAt");
                }

                LocalDate scheduledDate =
                        metadata.occurredAt()
                                .atZone(SINGAPORE)
                                .toLocalDate();

                ReportPeriod period =
                        ReportPeriod.weekEndingOnOrBefore(
                                scheduledDate.minusDays(1));

                if (!prepareDelay.isZero()) {
                    log.info(
                            "FAULT_DRILL_PREPARE_STARTED "
                            + "period={} to {} delay={}",
                            period.start(),
                            period.end(),
                            prepareDelay);

                    try {
                        Thread.sleep(prepareDelay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();

                        throw new IllegalStateException(
                                "Report preparation interrupted",
                                interrupted);
                    }

                    log.info(
                            "FAULT_DRILL_PREPARE_DELAY_COMPLETED "
                            + "period={} to {}",
                            period.start(),
                            period.end());
                }

                ReportService.PreparedBatch batch =
                        reports.prepareBatch(
                                null,
                                period.start(),
                                period.end(),
                                null);

                return () -> reports.commitPreparedBatch(batch);
            }
        };
    }
}
