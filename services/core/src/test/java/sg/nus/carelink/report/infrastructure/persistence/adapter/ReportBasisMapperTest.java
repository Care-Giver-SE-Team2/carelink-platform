package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.report.domain.model.ReportBasis;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportBasisJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ReportBasisJpaRepository;
import sg.nus.carelink.report.support.ReportFixtures;

/**
 * A basis goes to its table column by column with the facts beside it as a document, and its
 * numbers come back the way they went in. The document's shape is spelled out, as the content's
 * is, so a renamed domain field cannot silently change what is on disk.
 */
class ReportBasisMapperTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 23, 0);

	private final ReportBasisJpaRepository rows = mock(ReportBasisJpaRepository.class);
	private final ReportBasisRepositoryAdapter adapter = new ReportBasisRepositoryAdapter(rows);

	@Test
	void mapsEveryColumnAndTheNumbersComeBackAsTheyWent() {
		ReportFacts week = ReportFixtures.week();
		ReportBasis basis = ReportBasis.of(week, NOW);

		ReportBasisJpaEntity entity = ReportBasisMapper.toEntity(basis, week);
		entity.setId(8L);
		ReportBasis back = ReportBasisMapper.toDomain(entity);

		assertThat(entity.getElderId()).isEqualTo(ReportFixtures.ELDER);
		assertThat(entity.getPeriodStart()).isEqualTo(ReportFixtures.WEEK.start());
		assertThat(entity.getPeriodEnd()).isEqualTo(ReportFixtures.WEEK.end());
		assertThat(entity.getFactsVersion()).isEqualTo((short) 1);
		assertThat(entity.getVisitsPlanned()).isEqualTo(3);
		assertThat(entity.getVisitsCompleted()).isEqualTo(2);
		assertThat(entity.getFulfilmentRate()).isEqualByComparingTo("66.67");
		assertThat(entity.getVitalsOutOfRange()).isEqualTo(1);
		assertThat(entity.getIncidentCount()).isEqualTo(1);
		assertThat(entity.getAvgElderRating()).isEqualByComparingTo("3.5");
		assertThat(entity.getRatingCount()).isEqualTo(2);
		assertThat(entity.isDataComplete()).isFalse();
		assertThat(entity.getCreatedAt()).isEqualTo(NOW);
		assertThat(back).isEqualTo(new ReportBasis(8L, basis.elderId(), basis.period(), basis.metrics(), NOW));
	}

	@Test
	void theFactsAreStoredInTheirOwnDocumentedShape() {
		JsonNode facts = JsonMapper.builder().build().readTree(ReportBasisJson.write(ReportFixtures.week()));

		assertThat(facts.propertyNames()).containsExactlyInAnyOrder(
				"elderId", "periodStart", "periodEnd", "elder", "visits", "vitals", "observations", "incidents",
				"confirmations", "reviews", "spotChecks", "rosterChanges", "valueAdded");
		assertThat(facts.get("periodStart").asString()).isEqualTo("2026-09-14");
		assertThat(facts.get("elder").get("fullName").asString()).isEqualTo("Tan Ah Mei");
		assertThat(facts.get("elder").get("dateOfBirth").asString()).isEqualTo("1941-03-02");
		assertThat(facts.get("elder").get("medicalNotes").asString()).isEqualTo(ReportFixtures.MEDICAL_NOTES);
		assertThat(facts.get("visits").get(0).get("scheduledStart").asString()).isEqualTo("2026-09-14T09:00");
		assertThat(facts.get("visits").get(0).get("workedMinutes").asInt()).isEqualTo(55);
		assertThat(facts.get("vitals")).hasSize(8);
		assertThat(facts.get("incidents").get(0).get("timeline")).hasSize(4);
		assertThat(facts.get("confirmations").get(1).get("comment").asString()).isEqualTo(ReportFixtures.ELDER_COMMENT);
		assertThat(facts.get("reviews").get(0).get("renewalDecision").asString()).isEqualTo("RENEW_CURRENT");
		assertThat(facts.get("spotChecks").get(0).get("finding").asString()).isEqualTo(ReportFixtures.FINDING);
		assertThat(facts.get("rosterChanges").get(0).get("status").asString()).isEqualTo("AWAITING_FAMILY");
		assertThat(facts.get("valueAdded").get(0).get("service").asString()).isEqualTo("Hospital escort");
	}

	@Test
	void aBasisIsInsertedOnceWithTheFactsItWasMadeFrom() {
		when(rows.save(any(ReportBasisJpaEntity.class))).thenAnswer(invocation -> {
			ReportBasisJpaEntity inserted = invocation.getArgument(0);
			inserted.setId(8L);
			return inserted;
		});

		ReportBasis saved = adapter.save(ReportBasis.of(ReportFixtures.week(), NOW), ReportFixtures.week());

		assertThat(saved.id()).isEqualTo(8L);
		assertThat(saved.metrics().visitsPlanned()).isEqualTo(3);
	}

	@Test
	void aBasisAlreadyOnFileOrStoredWithSomebodyElsesFactsIsRefused() {
		ReportFacts week = ReportFixtures.week();
		ReportBasis onFile = new ReportBasis(8L, ReportFixtures.ELDER, ReportFixtures.WEEK, ReportMetrics.of(week), NOW);
		ReportBasis otherWeek = new ReportBasis(null, ReportFixtures.ELDER,
				new ReportPeriod(ReportFixtures.WEEK.start().minusWeeks(1), ReportFixtures.WEEK.end().minusWeeks(1)),
				ReportMetrics.of(week), NOW);
		ReportBasis otherElder = new ReportBasis(null, 2L, ReportFixtures.WEEK, ReportMetrics.of(week), NOW);

		assertThatThrownBy(() -> adapter.save(onFile, week)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.save(otherWeek, week)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.save(otherElder, week)).isInstanceOf(IllegalArgumentException.class);
		verify(rows, never()).save(any());
	}

	@Test
	void theNumbersOfEachBasisAreFoundByIdInOneRead() {
		ReportBasisJpaEntity row = ReportBasisMapper.toEntity(ReportBasis.of(ReportFixtures.quietWeek(), NOW),
				ReportFixtures.quietWeek());
		row.setId(9L);
		when(rows.findAllById(List.of(9L, 10L))).thenReturn(List.of(row));

		Map<Long, ReportMetrics> metrics = adapter.findMetrics(List.of(9L, 10L));

		assertThat(metrics).containsOnlyKeys(9L);
		assertThat(metrics.get(9L)).isEqualTo(new ReportMetrics(0, 0, null, 0, 0, null, 0, true));
		assertThat(metrics.get(9L).fulfilmentRate()).as("nothing planned is not a fulfilment of zero").isNull();
	}
}
