
package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Integration tests for ReportTaskProducer.
 *
 * Uses real MySQL, Flyway migrations and the real Events outbox.
 *
 * Verifies:
 * 1. Repeated dispatch is idempotent.
 * 2. Dispatch claim and outbox roll back together.
 * 3. Concurrent producers publish only one event per elder/week.
 *
 * No AWS connection is required.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "carelink.report.schedule-cron=-",
        "carelink.report.legacy-scheduler-enabled=false",
        "carelink.report.task-producer-enabled=false",
        "carelink.events.service=report"
})
class ReportTaskProducerIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(
                registry,
                ReportTaskProducerIT.class,
                null,
                "connectionTimeZone=Asia/Singapore"
        );
    }

    private static final AtomicLong IDS =
            new AtomicLong(800_000_000L);

    private static final ReportPeriod WEEK =
            new ReportPeriod(
                    LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 9, 20)
            );

    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse("2026-10-18T09:00:00Z"),
                    ZoneOffset.UTC
            );

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Events events;

    /**
     * Creates a producer using an isolated mocked facts source.
     *
     * Since this producer is constructed directly rather than
     * obtained from Spring, tests explicitly open transactions
     * using TransactionTemplate.
     */
    private ReportTaskProducer producer(List<Long> ids) {
        ReportFactsSource facts = mock(ReportFactsSource.class);

        when(facts.eldersWithVisitsIn(WEEK)).thenReturn(ids);

        return new ReportTaskProducer(
                facts,
                events,
                CLOCK,
                jdbc
        );
    }

    /**
     * Counts dispatch claims for one isolated elder.
     */
    private long claims(long elder) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_task_dispatch "
                        + "WHERE elder_id = ?",
                Long.class,
                elder
        );
    }

    /**
     * Counts ReportRequested outbox events for one elder.
     */
    private long outbox(long elder) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event "
                        + "WHERE source = 'report' "
                        + "AND type = 'ReportRequested' "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT("
                        + "payload, '$.elderId')) = ?",
                Long.class,
                Long.toString(elder)
        );
    }

    /**
     * Test 1:
     * Repeated runs must not publish duplicate events.
     *
     * Two unique elders should produce two events.
     * Repeating the same period should produce zero.
     */
    @Test
    void repeatedRunsPublishOnlyOneEventPerElder() {
        long a = IDS.incrementAndGet();
        long b = IDS.incrementAndGet();

        ReportTaskProducer producer =
                producer(List.of(a, b, a));

        int firstPublished = transactions.execute(
                status -> producer.publishPeriod(WEEK)
        );

        assertThat(firstPublished).isEqualTo(2);

        int secondPublished = transactions.execute(
                status -> producer.publishPeriod(WEEK)
        );

        assertThat(secondPublished).isZero();

        assertThat(claims(a)).isEqualTo(1L);
        assertThat(claims(b)).isEqualTo(1L);

        assertThat(outbox(a)).isEqualTo(1L);
        assertThat(outbox(b)).isEqualTo(1L);
    }

    /**
     * Test 2:
     * A failed transaction must roll back both the
     * dispatch claim and the outbox event.
     *
     * Retrying afterward must succeed.
     */
    @Test
    void rollbackRestoresClaimAndOutbox() {
        long elder = IDS.incrementAndGet();

        ReportTaskProducer producer =
                producer(List.of(elder));

        assertThatThrownBy(() ->
                transactions.executeWithoutResult(status -> {
                    producer.publishPeriod(WEEK);

                    throw new IllegalStateException(
                            "injected rollback"
                    );
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(claims(elder)).isZero();
        assertThat(outbox(elder)).isZero();

        int retryPublished = transactions.execute(
                status -> producer.publishPeriod(WEEK)
        );

        assertThat(retryPublished).isEqualTo(1);

        assertThat(claims(elder)).isEqualTo(1L);
        assertThat(outbox(elder)).isEqualTo(1L);
    }

    /**
     * Test 3:
     * Two concurrent producers attempt to dispatch the
     * same elder and reporting period.
     *
     * Each thread runs in its own database transaction.
     *
     * Expected:
     * - One producer inserts the dispatch claim.
     * - The other producer sees the existing claim.
     * - Exactly one outbox event is committed.
     */
    @Test
    void concurrentProducersPublishOnlyOneOutboxEvent()
            throws Exception {

        long elder = IDS.incrementAndGet();

        ReportTaskProducer producer =
                producer(List.of(elder));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool =
                Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = pool.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "First producer start gate timeout"
                    );
                }

                TransactionTemplate tx =
                        new TransactionTemplate(transactionManager);

                return tx.execute(
                        status -> producer.publishPeriod(WEEK)
                );
            });

            Future<Integer> second = pool.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Second producer start gate timeout"
                    );
                }

                TransactionTemplate tx =
                        new TransactionTemplate(transactionManager);

                return tx.execute(
                        status -> producer.publishPeriod(WEEK)
                );
            });

            assertThat(
                    ready.await(10, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            int firstCount =
                    first.get(45, TimeUnit.SECONDS);

            int secondCount =
                    second.get(45, TimeUnit.SECONDS);

            // Only one producer may publish the event.
            assertThat(firstCount + secondCount)
                    .isEqualTo(1);

            // Only one dispatch claim may exist.
            assertThat(claims(elder))
                    .isEqualTo(1L);

            // Only one outbox event may exist.
            assertThat(outbox(elder))
                    .isEqualTo(1L);

            // A later retry must not publish again.
            int retryCount = transactions.execute(
                    status -> producer.publishPeriod(WEEK)
            );

            assertThat(retryCount).isZero();

            assertThat(claims(elder))
                    .isEqualTo(1L);

            assertThat(outbox(elder))
                    .isEqualTo(1L);

        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
