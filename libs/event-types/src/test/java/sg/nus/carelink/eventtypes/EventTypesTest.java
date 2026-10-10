package sg.nus.carelink.eventtypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The records write the JSON the catalogue sets, and read it back: field names as listed, business
 * times on Singapore's clock with their offset, and a handler gets the same wall-clock time back
 * whatever offset its JSON reader hands it.
 */
class EventTypesTest {

	private static final LocalDateTime HALF_NINE = LocalDateTime.of(2026, 10, 12, 9, 35);

	private final JsonMapper json = JsonMapper.builder().build();

	private final IncidentRaised raised = new IncidentRaised(601L, 101L, 812L, "CAREGIVER", "FALL", "HIGH", "OPEN",
			"Slipped in the bathroom", SingaporeTime.of(HALF_NINE));

	@Test
	void aBusinessTimeIsSingaporeWallClockTimeWithItsOffset() {
		assertThat(SingaporeTime.of(HALF_NINE)).isEqualTo(OffsetDateTime.of(HALF_NINE, ZoneOffset.ofHours(8)));
		assertThat(SingaporeTime.of(null)).isNull();
	}

	@Test
	void aTimeIsRoundedToTheSecondAsTheDatabaseKeepsIt() {
		assertThat(SingaporeTime.of(HALF_NINE.plusNanos(600_000_000)))
				.isEqualTo(OffsetDateTime.of(HALF_NINE.plusSeconds(1), ZoneOffset.ofHours(8)));
		assertThat(SingaporeTime.of(HALF_NINE.plusNanos(499_999_999)))
				.isEqualTo(OffsetDateTime.of(HALF_NINE, ZoneOffset.ofHours(8)));
	}

	@Test
	void anEventIsWrittenWithTheCataloguesFieldsAndTimes() {
		JsonNode written = json.readTree(json.writeValueAsString(raised));

		assertThat(written.propertyNames()).containsExactly("incidentId", "elderId", "visitId", "source", "category",
				"severity", "status", "description", "reportedAt");
		assertThat(written.get("reportedAt").asString()).isEqualTo("2026-10-12T09:35:00+08:00");
		assertThat(written.get("incidentId").isNumber()).isTrue();
	}

	@Test
	void aHandlerGetsTheSameWallClockTimeBackWhateverOffsetItReads() {
		IncidentRaised read = json.readValue(json.writeValueAsString(raised), IncidentRaised.class);

		assertThat(SingaporeTime.local(read.reportedAt())).isEqualTo(HALF_NINE);
		assertThat(SingaporeTime.local(OffsetDateTime.parse("2026-10-12T01:35:00Z"))).isEqualTo(HALF_NINE);
		assertThat(SingaporeTime.local(null)).isNull();
	}

	@Test
	void everyEventReadsBackFromItsJson() {
		var updated = new IncidentUpdated(601L, 101L, 9001L, "RESOLVED", "lee.manager",
				"HANDLED_ON_SITE :: Checked and settled", SingaporeTime.of(HALF_NINE), "RESOLVED", "HIGH",
				SingaporeTime.of(HALF_NINE));
		var spotCheck = new SpotCheckUpdated(41L, 101L, 812L, 9L, SingaporeTime.of(HALF_NINE), "APPROVED",
				"MEETS_STANDARD", "COMPLETED", "All tasks done", null, SingaporeTime.of(HALF_NINE),
				SingaporeTime.of(HALF_NINE));
		var rosterChange = new RosterChangeUpdated(55L, 812L, 101L, 33L, SingaporeTime.of(HALF_NINE), 7L, "RESOLVED",
				"REPLACED", "FAMILY", 9L, SingaporeTime.of(HALF_NINE));

		assertThat(json.readValue(json.writeValueAsString(updated), IncidentUpdated.class).logId()).isEqualTo(9001L);
		assertThat(json.readValue(json.writeValueAsString(spotCheck), SpotCheckUpdated.class).result())
				.isEqualTo("MEETS_STANDARD");
		assertThat(json.readValue(json.writeValueAsString(rosterChange), RosterChangeUpdated.class).decidedBy())
				.isEqualTo("FAMILY");
		var requested = new NotificationRequested(7L, "CREDENTIAL_EXPIRED", "IN_APP", "Your First aid certificate expired",
				"Upload the renewed certificate.", "CREDENTIAL", 8732L, null, SingaporeTime.of(HALF_NINE));
		assertThat(json.readValue(json.writeValueAsString(requested), NotificationRequested.class).recipientUserId())
				.isEqualTo(7L);
		assertThat(NotificationRequested.TYPE).isEqualTo("NotificationRequested");
		assertThat(IncidentRaised.TYPE).isEqualTo("IncidentRaised");
		assertThat(IncidentUpdated.TYPE).isEqualTo("IncidentUpdated");
		assertThat(SpotCheckUpdated.TYPE).isEqualTo("SpotCheckUpdated");
		assertThat(RosterChangeUpdated.TYPE).isEqualTo("RosterChangeUpdated");
	}

}
