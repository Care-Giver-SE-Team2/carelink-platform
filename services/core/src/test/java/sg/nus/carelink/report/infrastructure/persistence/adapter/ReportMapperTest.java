package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.service.ReportAssembler;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportAmendmentJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportJpaEntity;
import sg.nus.carelink.report.support.ReportFixtures;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class ReportMapperTest {

	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 20, 23, 0);

	@Test
	void mapsEveryColumnInBothDirections() {
		ReportContent content = ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(ReportFixtures.week());
		ReportJpaEntity entity = new ReportJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setGeneratedByUserId(3L);
		entity.setAudience(ReportJpaEntity.Audience.FAMILY);
		entity.setPeriodStart(LocalDate.of(2026, 9, 14));
		entity.setPeriodEnd(LocalDate.of(2026, 9, 20));
		entity.setStatus(ReportJpaEntity.Status.PUBLISHED);
		entity.setContent(ReportContentJson.write(content));
		entity.setCreatedAt(CREATED);

		ReportAmendmentJpaEntity correction = amendmentRow();

		Report domain = ReportMapper.toDomain(entity, List.of(correction));
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.generatedByUserId()).isEqualTo(entity.getGeneratedByUserId());
		assertThat(domain.audience().name()).isEqualTo(entity.getAudience().name());
		assertThat(domain.period().start()).isEqualTo(entity.getPeriodStart());
		assertThat(domain.period().end()).isEqualTo(entity.getPeriodEnd());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.content()).isEqualTo(content);
		assertThat(domain.createdAt()).isEqualTo(entity.getCreatedAt());
		assertThat(domain.amendments()).containsExactly(
				new ReportAmendment(5L, 1L, "Visit 13 was cancelled by the family.", 9L, CREATED.plusDays(1)));

		ReportJpaEntity back = ReportMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getGeneratedByUserId()).isEqualTo(entity.getGeneratedByUserId());
		assertThat(back.getAudience()).isEqualTo(entity.getAudience());
		assertThat(back.getPeriodStart()).isEqualTo(entity.getPeriodStart());
		assertThat(back.getPeriodEnd()).isEqualTo(entity.getPeriodEnd());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(ReportContentJson.read(back.getContent())).isEqualTo(content);
		assertThat(back.getCreatedAt()).isEqualTo(entity.getCreatedAt());
	}

	@Test
	void mapsEveryAmendmentColumnInBothDirections() {
		ReportAmendmentJpaEntity row = amendmentRow();

		ReportAmendment domain = ReportAmendmentMapper.toDomain(row);
		ReportAmendmentJpaEntity back = ReportAmendmentMapper.toEntity(domain);

		assertThat(back.getId()).isEqualTo(row.getId());
		assertThat(back.getReportId()).isEqualTo(row.getReportId());
		assertThat(back.getNote()).isEqualTo(row.getNote());
		assertThat(back.getAuthorUserId()).isEqualTo(row.getAuthorUserId());
		assertThat(back.getCreatedAt()).isEqualTo(row.getCreatedAt());
	}

	/** The stored shape is spelled out, so a renamed domain field cannot silently change what is on disk. */
	@Test
	void theContentIsStoredInItsOwnDocumentedShape() {
		String json = ReportContentJson.write(ReportFixtures.content());
		JsonNode stored = JsonMapper.builder().build().readTree(json);

		assertThat(stored.propertyNames()).containsExactlyInAnyOrder(
				"sections", "dataComplete", "missingItems", "disclaimer", "generatedBy");
		assertThat(stored.get("sections").get(0).get("title").asString()).isEqualTo("Service completion");
		assertThat(stored.get("sections").get(0).get("body").asString()).isEqualTo("3 visits: 1 scheduled, 2 verified.");
		assertThat(stored.get("dataComplete").asBoolean()).isFalse();
		assertThat(stored.get("missingItems").get(0).asString()).isEqualTo("Visit 13 on 2026-09-18 not closed");
		assertThat(stored.get("disclaimer").isNull()).isTrue();
		assertThat(stored.get("generatedBy").asString()).isEqualTo("TEMPLATE");
		assertThat(ReportContentJson.read(json)).isEqualTo(ReportFixtures.content());
	}

	/** An archived report written by a later version, with a field this one does not know, still reads. */
	@Test
	void aFieldThisVersionDoesNotKnowIsIgnored() {
		ReportContent read = ReportContentJson.read("""
				{"sections":[{"title":"Service completion","body":null}],"dataComplete":true,
				 "missingItems":null,"disclaimer":null,"generatedBy":"MODEL","model":"a later addition"}
				""");

		assertThat(read.sections().getFirst().body()).isEmpty();
		assertThat(read.missingItems()).isEmpty();
		assertThat(read.generatedBy()).isEqualTo(ReportContent.GeneratedBy.MODEL);
	}

	@Test
	void aReportStoredWithoutContentIsDamageNotAState() {
		assertThatThrownBy(() -> ReportContentJson.read(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ReportContentJson.read(" ")).isInstanceOf(IllegalStateException.class);
	}

	private static ReportAmendmentJpaEntity amendmentRow() {
		ReportAmendmentJpaEntity row = new ReportAmendmentJpaEntity();
		row.setId(5L);
		row.setReportId(1L);
		row.setNote("Visit 13 was cancelled by the family.");
		row.setAuthorUserId(9L);
		row.setCreatedAt(CREATED.plusDays(1));
		return row;
	}
}
