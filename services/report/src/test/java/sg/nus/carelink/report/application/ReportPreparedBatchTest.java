package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.repository.ReportBasisRepository;
import sg.nus.carelink.report.domain.repository.ReportRepository;
import sg.nus.carelink.report.support.ReportFixtures;

/** Contract tests for the worker's prepare/commit split; database rollback needs an IT. */
class ReportPreparedBatchTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-20T15:00:00Z"), ZoneId.of("Asia/Singapore"));
    private static final LocalDate START = LocalDate.of(2026, 9, 14);
    private static final LocalDate END = LocalDate.of(2026, 9, 20);

    @Test
    void preparationCallsAiWithoutAnActiveTransactionAndDoesNotWriteAnything() {
        ReportRepository reports = mock(ReportRepository.class);
        ReportBasisRepository bases = mock(ReportBasisRepository.class);
        FakeReportFactsSource facts = new FakeReportFactsSource().with(ReportFixtures.week());
        AtomicInteger aiCalls = new AtomicInteger();
        ReportAiEnricher ai = new ReportAiEnricher(metrics -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            aiCalls.incrementAndGet();
            return Optional.of("Summary based on aggregate figures.");
        });
        ReportService service = new ReportService(reports, bases, facts, CLOCK, ai);

        ReportService.PreparedBatch prepared = service.prepareBatch(
                ReportFixtures.ELDER, START, END, null);

        assertThat(prepared.pending()).hasSize(1);
        assertThat(prepared.pending().getFirst().content()).hasSize(Report.Audience.values().length);
        assertThat(aiCalls.get()).isPositive();
        verify(reports, never()).save(any());
        verifyNoInteractions(bases);
    }

    @Test
    void commitSkipsAlreadyFiledReportsWithoutCreatingAnotherBasis() {
        InMemoryReportRepository reports = new InMemoryReportRepository();
        InMemoryReportBasisRepository bases = new InMemoryReportBasisRepository();
        FakeReportFactsSource facts = new FakeReportFactsSource().with(ReportFixtures.week());
        ReportService service = new ReportService(reports, bases, facts, CLOCK);

        ReportService.PreparedBatch prepared = service.prepareBatch(
                ReportFixtures.ELDER, START, END, null);
        service.commitPreparedBatch(prepared);
        assertThat(reports.reportCount()).isEqualTo(3);
        assertThat(bases.stored()).hasSize(1);

        service.commitPreparedBatch(prepared);
        assertThat(reports.reportCount()).isEqualTo(3);
        assertThat(bases.stored()).hasSize(1);
    }

    @Test
    void alreadyCompleteWeekNeedsNoAiAndNoPreparedWrites() {
        InMemoryReportRepository reports = new InMemoryReportRepository();
        InMemoryReportBasisRepository bases = new InMemoryReportBasisRepository();
        FakeReportFactsSource facts = new FakeReportFactsSource().with(ReportFixtures.week());
        ReportService service = new ReportService(reports, bases, facts, CLOCK);
        service.generate(ReportFixtures.ELDER, START, END, null);

        ReportService.PreparedBatch prepared = service.prepareBatch(
                ReportFixtures.ELDER, START, END, null);

        assertThat(prepared.pending()).isEmpty();
        assertThat(reports.reportCount()).isEqualTo(3);
        assertThat(bases.stored()).hasSize(1);
    }
}
