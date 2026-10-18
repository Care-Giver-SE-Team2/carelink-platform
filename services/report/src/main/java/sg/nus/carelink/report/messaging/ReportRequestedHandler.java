
package sg.nus.carelink.report.messaging;

import java.time.Duration;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeParseException;
import java.util.Set;
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

                if (payload == null || !payload.keySet().equals(Set.of(
                        "schemaVersion", "elderId", "periodStart", "periodEnd", "runId"))) {
                    throw new IllegalArgumentException("Invalid ReportRequested v1 payload fields");
                }
                if (!(payload.get("schemaVersion") instanceof Integer version) || version != 1) {
                    throw new IllegalArgumentException("Unsupported ReportRequested schemaVersion");
                }
                Object rawElderId = payload.get("elderId");
                if (!(rawElderId instanceof Number number)
                        || rawElderId instanceof Float || rawElderId instanceof Double
                        || number.longValue() <= 0
                        || number.doubleValue() != number.longValue()) {
                    throw new IllegalArgumentException("ReportRequested requires positive integer elderId");
                }
                long elderId = number.longValue();
                Object rawRunId = payload.get("runId");
                if (!(rawRunId instanceof String runId)
                        || !runId.matches("[a-z0-9-]{1,64}")) {
                    throw new IllegalArgumentException("Invalid ReportRequested runId");
                }
                LocalDate start;
                LocalDate end;
                try {
                    if (!(payload.get("periodStart") instanceof String startText)
                            || !(payload.get("periodEnd") instanceof String endText)) {
                        throw new IllegalArgumentException("Missing ReportRequested period");
                    }
                    start = LocalDate.parse(startText);
                    end = LocalDate.parse(endText);
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("Invalid ReportRequested period date", ex);
                }
                if (metadata == null || metadata.occurredAt() == null) {
                    throw new IllegalArgumentException("ReportRequested requires occurredAt");
                }
                LocalDate occurredDate = metadata.occurredAt().atZone(SINGAPORE).toLocalDate();
                if (!DayOfWeek.MONDAY.equals(start.getDayOfWeek())
                        || !end.equals(start.plusDays(6))
                        || !end.isBefore(occurredDate)) {
                    throw new IllegalArgumentException("ReportRequested requires completed Monday-Sunday period");
                }
                ReportPeriod period = new ReportPeriod(start, end);

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
                                elderId,
                                period.start(),
                                period.end(),
                                null);

                return () -> reports.commitPreparedBatch(batch);
            }
        };
    }
}
