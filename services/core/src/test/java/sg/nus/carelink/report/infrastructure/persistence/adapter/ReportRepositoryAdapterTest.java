package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportAmendmentJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ReportAmendmentJpaRepository;
import sg.nus.carelink.report.infrastructure.persistence.repository.ReportJpaRepository;
import sg.nus.carelink.report.support.ReportFixtures;

/**
 * The adapter delegates to Spring Data and maps at the boundary. What it adds is tested here:
 * a report never comes back without its corrections, nothing is stored twice, and "any
 * reader" reaches the query as all three rather than as a null that matches nothing.
 */
class ReportRepositoryAdapterTest {

	private final ReportJpaRepository reports = mock(ReportJpaRepository.class);
	private final ReportAmendmentJpaRepository amendments = mock(ReportAmendmentJpaRepository.class);
	private final ReportRepositoryAdapter adapter = new ReportRepositoryAdapter(reports, amendments);

	@Test
	void findByIdReturnsTheReportWithItsCorrections() {
		when(reports.findById(40L)).thenReturn(Optional.of(row(40L, ReportJpaEntity.Audience.FAMILY)));
		when(amendments.findByReportIdOrderByCreatedAtAscIdAsc(40L)).thenReturn(List.of(correction(5L, 40L)));

		Optional<Report> found = adapter.findById(40L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(40L);
		assertThat(found.get().content()).isEqualTo(ReportFixtures.content());
		assertThat(found.get().amendments()).extracting(ReportAmendment::id).containsExactly(5L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(reports.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void saveInsertsANewReportAndComesBackAsDomain() {
		when(reports.save(any(ReportJpaEntity.class))).thenAnswer(invocation -> {
			ReportJpaEntity inserted = invocation.getArgument(0);
			inserted.setId(40L);
			return inserted;
		});
		Report unsaved = Report.generate(ReportFixtures.ELDER, Report.Audience.FAMILY, ReportFixtures.WEEK,
				ReportFixtures.content(), 7L, ReportFixtures.GENERATED_AT);

		Report saved = adapter.save(unsaved);

		assertThat(saved.id()).isEqualTo(40L);
		assertThat(saved.createdAt()).isEqualTo(ReportFixtures.GENERATED_AT);
		assertThat(saved.content()).isEqualTo(ReportFixtures.content());
	}

	@Test
	void aReportAlreadyOnFileIsNeverWrittenAgain() {
		Report onFile = ReportFixtures.stored(40L, Report.Audience.FAMILY);

		assertThatThrownBy(() -> adapter.save(onFile)).isInstanceOf(IllegalArgumentException.class);
		verify(reports, never()).save(any());
	}

	@Test
	void aCorrectionIsInsertedOnItsOwn() {
		when(amendments.save(any(ReportAmendmentJpaEntity.class))).thenAnswer(invocation -> {
			ReportAmendmentJpaEntity inserted = invocation.getArgument(0);
			inserted.setId(5L);
			return inserted;
		});

		ReportAmendment saved = adapter.saveAmendment(
				new ReportAmendment(null, 40L, "note", 9L, LocalDateTime.of(2026, 9, 21, 9, 30)));

		assertThat(saved.id()).isEqualTo(5L);
		assertThat(saved.reportId()).isEqualTo(40L);
		verify(reports, never()).save(any());
	}

	@Test
	void aCorrectionAlreadyOnFileIsNeverWrittenAgain() {
		ReportAmendment onFile = new ReportAmendment(5L, 40L, "note", 9L, LocalDateTime.of(2026, 9, 21, 9, 30));

		assertThatThrownBy(() -> adapter.saveAmendment(onFile)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void findsTheReportAlreadyFiledForAnElderReaderAndPeriod() {
		when(reports.findFirstByElderIdAndAudienceAndPeriodStartAndPeriodEndOrderByIdAsc(
				1L, ReportJpaEntity.Audience.REGULATOR, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)))
				.thenReturn(Optional.of(row(41L, ReportJpaEntity.Audience.REGULATOR)));

		assertThat(adapter.findFor(1L, Report.Audience.REGULATOR, ReportFixtures.WEEK))
				.get()
				.extracting(Report::id)
				.isEqualTo(41L);
		assertThat(adapter.findFor(1L, Report.Audience.FAMILY, ReportFixtures.WEEK)).isEmpty();
	}

	@SuppressWarnings("unchecked")
	@Test
	void anyReaderMeansAllThreeSpelledOutAndTheOrderIsLatestPeriodFirst() {
		ArgumentCaptor<Collection<ReportJpaEntity.Audience>> audiences = ArgumentCaptor.forClass(Collection.class);
		ArgumentCaptor<Pageable> request = ArgumentCaptor.forClass(Pageable.class);
		when(reports.findByAudienceIn(audiences.capture(), request.capture())).thenReturn(
				new PageImpl<>(List.of(row(40L, ReportJpaEntity.Audience.FAMILY), row(41L, ReportJpaEntity.Audience.REGULATOR)),
						PageRequest.of(0, 20), 2));
		when(amendments.findByReportIdInOrderByCreatedAtAscIdAsc(anyCollection()))
				.thenReturn(List.of(correction(5L, 41L), correction(6L, 41L)));

		ReportPage page = adapter.findPage(null, null, 0, 20);

		assertThat(audiences.getValue()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(ReportJpaEntity.Audience.class));
		assertThat(request.getValue().getSort()).containsExactly(
				Sort.Order.desc("periodEnd"), Sort.Order.asc("audience"), Sort.Order.asc("elderId"), Sort.Order.desc("id"));
		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.items()).extracting(Report::id).containsExactly(40L, 41L);
		assertThat(page.items().get(0).amendments()).isEmpty();
		assertThat(page.items().get(1).amendments()).extracting(ReportAmendment::id).containsExactly(5L, 6L);
	}

	@SuppressWarnings("unchecked")
	@Test
	void oneElderAndOneReaderNarrowTheQuery() {
		ArgumentCaptor<Collection<ReportJpaEntity.Audience>> audiences = ArgumentCaptor.forClass(Collection.class);
		when(reports.findByElderIdAndAudienceIn(eq(1L), audiences.capture(), any()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		ReportPage page = adapter.findPage(1L, Report.Audience.FAMILY, 0, 20);

		assertThat(audiences.getValue()).containsExactly(ReportJpaEntity.Audience.FAMILY);
		assertThat(page.items()).isEmpty();
		verify(amendments, never()).findByReportIdInOrderByCreatedAtAscIdAsc(anyCollection());
	}

	private static ReportJpaEntity row(Long id, ReportJpaEntity.Audience audience) {
		ReportJpaEntity row = new ReportJpaEntity();
		row.setId(id);
		row.setElderId(ReportFixtures.ELDER);
		row.setGeneratedByUserId(7L);
		row.setAudience(audience);
		row.setPeriodStart(ReportFixtures.WEEK.start());
		row.setPeriodEnd(ReportFixtures.WEEK.end());
		row.setStatus(ReportJpaEntity.Status.PUBLISHED);
		row.setContent(ReportContentJson.write(ReportFixtures.content()));
		row.setCreatedAt(ReportFixtures.GENERATED_AT);
		return row;
	}

	private static ReportAmendmentJpaEntity correction(Long id, Long reportId) {
		ReportAmendmentJpaEntity row = new ReportAmendmentJpaEntity();
		row.setId(id);
		row.setReportId(reportId);
		row.setNote("correction " + id);
		row.setAuthorUserId(9L);
		row.setCreatedAt(LocalDateTime.of(2026, 9, 21, 9, 30));
		return row;
	}
}
