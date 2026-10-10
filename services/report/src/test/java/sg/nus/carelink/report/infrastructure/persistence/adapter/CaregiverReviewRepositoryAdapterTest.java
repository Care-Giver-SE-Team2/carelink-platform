package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.infrastructure.persistence.entity.CaregiverReviewJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.CaregiverReviewJpaRepository;

class CaregiverReviewRepositoryAdapterTest {

    private final CaregiverReviewJpaRepository jpa = mock(CaregiverReviewJpaRepository.class);
    private final CaregiverReviewRepositoryAdapter adapter = new CaregiverReviewRepositoryAdapter(jpa);

    @Test
    void findByIdMapsTheEntityToTheDomainModel() {
        CaregiverReviewJpaEntity entity = entity(7L);
        when(jpa.findById(7L)).thenReturn(Optional.of(entity));

        Optional<CaregiverReview> found = adapter.findById(7L);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(7L);
        assertThat(found.get().elderId()).isEqualTo(21L);
        assertThat(found.get().renewalDecision()).isEqualTo(CaregiverReview.RenewalDecision.RENEW_CURRENT);
    }

    @Test
    void findByIdIsEmptyWhenThereIsNoRow() {
        when(jpa.findById(any())).thenReturn(Optional.empty());
        assertThat(adapter.findById(7L)).isEmpty();
    }

    @Test
    void findsElderReviewsInRepositoryOrder() {
        when(jpa.findByElderIdOrderByCreatedAtDescIdDesc(21L))
                .thenReturn(List.of(entity(9L), entity(8L)));

        assertThat(adapter.findByElderId(21L))
                .extracting(CaregiverReview::id)
                .containsExactly(9L, 8L);
        verify(jpa).findByElderIdOrderByCreatedAtDescIdDesc(21L);
    }

    @Test
    void delegatesDuplicatePeriodCheck() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(jpa.existsByFamilyMemberIdAndElderIdAndCaregiverIdAndPeriodStartAndPeriodEnd(
                11L, 21L, 31L, start, end)).thenReturn(true);

        assertThat(adapter.existsForPeriod(11L, 21L, 31L, start, end)).isTrue();
    }

    @Test
    void saveGoesThroughSpringDataAndComesBackAsDomain() {
        CaregiverReviewJpaEntity entity = entity(7L);
        when(jpa.save(any(CaregiverReviewJpaEntity.class))).thenReturn(entity);

        CaregiverReview saved = adapter.save(CaregiverReviewMapper.toDomain(entity));

        assertThat(saved.id()).isEqualTo(7L);
        assertThat(saved.feedbackNotes()).isEqualTo("Good care");
    }

    private static CaregiverReviewJpaEntity entity(Long id) {
        CaregiverReviewJpaEntity entity = new CaregiverReviewJpaEntity();
        entity.setId(id);
        entity.setFamilyMemberId(11L);
        entity.setElderId(21L);
        entity.setCaregiverId(31L);
        entity.setPeriodStart(LocalDate.of(2026, 9, 1));
        entity.setPeriodEnd(LocalDate.of(2026, 9, 30));
        entity.setOverallRating((byte) 5);
        entity.setPunctualityScore((byte) 4);
        entity.setCareQualityScore((byte) 5);
        entity.setFeedbackNotes("Good care");
        entity.setRenewalDecision(CaregiverReviewJpaEntity.RenewalDecision.RENEW_CURRENT);
        return entity;
    }
}
