package sg.nus.carelink.report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.events.PreparedEventHandler;
import sg.nus.carelink.eventtypes.VisitEvidenceUpdated;
import sg.nus.carelink.report.application.ReportTaskProducer;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.KeptFacts;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;
import sg.nus.carelink.testsupport.SharedMySql;

/** Producer, private-evidence projection and worker preparation share the combined schema.
 * Core is stubbed; queue delivery itself is covered by the separate local runtime check. */
@SpringBootTest
@ActiveProfiles("worker")
@TestPropertySource(properties = {
        "carelink.report.ai.enabled=false",
        "carelink.report.schedule-cron=-",
        "carelink.report.task-producer-enabled=false",
        "carelink.events.queue-url=false",
        "carelink.events.topic-arn=false"
})
class ReportWorkerEvidenceCompatibilityIT {
    private static final AtomicLong IDS = new AtomicLong(910_000L);
    private static final ReportPeriod WEEK = new ReportPeriod(
            LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-09-15T09:00:00+08:00");
    private static final Instant NOW = Instant.parse("2026-10-19T01:00:00Z");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, ReportWorkerEvidenceCompatibilityIT.class, null,
                "connectionTimeZone=Asia/Singapore");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired List<EventHandler<?>> handlers;
    @Autowired ReportFactsSource facts;
    @Autowired KeptFacts keptFacts;
    @Autowired Events events;
    @MockitoBean CoreApi core;

    @Test
    void dispatchedWorkerUsesEvidenceSnapshotAndReplaysWithoutDuplicateReports() {
        Fixture fixture = fixture();
        int repeated = transactions.execute(_ -> fixture.producer().publishPeriod(WEEK));
        assertThat(repeated).isZero();
        Runnable commit = worker().prepare(payload(fixture.elder()), metadata("ReportRequested"));
        assertThat(count("report", fixture.elder())).isZero();
        transactions.executeWithoutResult(_ -> commit.run());

        assertThat(count("report", fixture.elder())).isEqualTo(3);
        assertThat(count("report_basis", fixture.elder())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select JSON_EXTRACT(facts,'$.visits[0].evidenceCount') "
                + "from report_basis where elder_id=?", Integer.class, fixture.elder())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select JSON_EXTRACT(facts,'$.visits[0].verifiedEvidenceCount') "
                + "from report_basis where elder_id=?", Integer.class, fixture.elder())).isZero();
        Runnable replay = worker().prepare(payload(fixture.elder()), metadata("ReportRequested"));
        transactions.executeWithoutResult(_ -> replay.run());
        assertThat(count("report", fixture.elder())).isEqualTo(3);
        assertThat(count("report_basis", fixture.elder())).isEqualTo(1);
        assertThat(count("report_task_dispatch", fixture.elder())).isEqualTo(1);
    }

    @Test
    void rolledBackWorkerCommitCanRetryWithoutLosingEvidenceOrDispatchClaim() {
        Fixture fixture = fixture();
        Runnable commit = worker().prepare(payload(fixture.elder()), metadata("ReportRequested"));
        transactions.executeWithoutResult(tx -> { commit.run(); tx.setRollbackOnly(); });
        assertThat(count("report", fixture.elder())).isZero();
        assertThat(count("report_basis", fixture.elder())).isZero();
        assertThat(count("report_task_dispatch", fixture.elder())).isEqualTo(1);
        assertThat(facts.gather(fixture.elder(), WEEK).orElseThrow().visits().getFirst().evidenceCount())
                .isEqualTo(2);
        transactions.executeWithoutResult(_ -> commit.run());
        assertThat(count("report", fixture.elder())).isEqualTo(3);
        assertThat(count("report_basis", fixture.elder())).isEqualTo(1);
    }

    private Fixture fixture() {
        long elder = IDS.incrementAndGet(), visit = IDS.incrementAndGet();
        when(core.elderReportProfile(elder)).thenReturn(new CoreApi.ElderReportProfile(
                elder, "Synthetic compatibility elder", null, null, null, null, null, null, null, null, null));
        jdbc.update("insert into report_visit(visit_id,elder_id,scheduled_start,status,version,"
                        + "evidence_count,verified_evidence_count) values (?,?,?,'COMPLETED',9,7,3)",
                visit, elder, Timestamp.from(START.toInstant()));
        var snapshot = new VisitEvidenceUpdated(visit, elder, START, 5, 2, 0);
        // The API consumes fact events; worker reads the resulting tables. Preserve that separation.
        assertThat(handlers).noneMatch(h -> h.type().equals(VisitEvidenceUpdated.TYPE));
        transactions.executeWithoutResult(_ -> {
            keptFacts.keep(snapshot);
            keptFacts.keep(snapshot);
            keptFacts.keep(new VisitEvidenceUpdated(visit, elder, START, 2, 1, 0));
        });
        var producer = new ReportTaskProducer(facts, events, Clock.fixed(NOW, ZoneOffset.UTC), jdbc);
        int published = transactions.execute(_ -> producer.publishPeriod(WEEK));
        assertThat(published).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where type='ReportRequested' "
                + "and JSON_UNQUOTE(JSON_EXTRACT(payload,'$.elderId'))=?", Integer.class,
                Long.toString(elder))).isEqualTo(1);
        return new Fixture(elder, producer);
    }

    private long count(String table, long elder) {
        if (!List.of("report", "report_basis", "report_task_dispatch").contains(table)) {
            throw new IllegalArgumentException("Unexpected table");
        }
        return jdbc.queryForObject("select count(*) from " + table + " where elder_id=?", Long.class, elder);
    }

    @SuppressWarnings("unchecked")
    private PreparedEventHandler<Map> worker() {
        return (PreparedEventHandler<Map>) handlers.stream()
                .filter(h -> h.type().equals("ReportRequested")).findFirst().orElseThrow();
    }

    private static Map<String, Object> payload(long elder) {
        return Map.of("schemaVersion", 1, "elderId", elder, "periodStart", WEEK.start().toString(),
                "periodEnd", WEEK.end().toString(), "runId", "weekly-20260914");
    }

    private static EventMetadata metadata(String type) {
        return new EventMetadata("combined-test", type, "report", NOW, 1L);
    }

    private record Fixture(long elder, ReportTaskProducer producer) { }
}
