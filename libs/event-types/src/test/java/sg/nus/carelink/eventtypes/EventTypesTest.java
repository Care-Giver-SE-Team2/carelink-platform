package sg.nus.carelink.eventtypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	/** The Open Platform's provider, field by field as the catalogue lists it, and back. */
	@Test
	void providerUpdatedIsWrittenWithTheCataloguesFields() {
		var updated = new ProviderUpdated(509L, "cl_prov_BrightHome9", "BrightHome Care", "ACTIVE",
				List.of("Hospital escort", "Companionship"), 3, SingaporeTime.of(HALF_NINE));
		JsonNode written = json.readTree(json.writeValueAsString(updated));

		assertThat(written.propertyNames()).containsExactly("providerId", "clientId", "organisationName", "status",
				"services", "version", "changedAt");
		assertThat(written.get("changedAt").asString()).isEqualTo("2026-10-12T09:35:00+08:00");
		ProviderUpdated read = json.readValue(json.writeValueAsString(updated), ProviderUpdated.class);
		assertThat(read.services()).containsExactly("Hospital escort", "Companionship");
		assertThat(read.clientId()).isEqualTo("cl_prov_BrightHome9");
		assertThat(SingaporeTime.local(read.changedAt())).isEqualTo(HALF_NINE);
		assertThat(new ProviderUpdated(509L, null, "BrightHome Care", "ACTIVE", null, 0, null).services()).isEmpty();
		assertThat(ProviderUpdated.TYPE).isEqualTo("ProviderUpdated");
	}

	/** Visit's events, field by field as the catalogue lists them (section 3.2), and back. */
	@Test
	void visitsEventsAreWrittenWithTheCataloguesFields() {
		OffsetDateTime start = SingaporeTime.of(HALF_NINE);
		OffsetDateTime later = SingaporeTime.of(HALF_NINE.plusMinutes(40));
		Map<Object, List<String>> fields = new LinkedHashMap<>();
		fields.put(new VisitScheduled(812L, 101L, 7L, 21L, 40L, "BATHING", start, later, "PLAN", null, 0),
				List.of("visitId", "elderId", "caregiverId", "carePlanId", "carePlanNodeId", "serviceType",
						"scheduledStart", "scheduledEnd", "origin", "movedFromVisitId", "version"));
		fields.put(new VisitCaregiverChanged(812L, 101L, start, 9L, 7L, 33L, 1, later),
				List.of("visitId", "elderId", "scheduledStart", "caregiverId", "previousCaregiverId", "absenceId",
						"version", "changedAt"));
		fields.put(new VisitCancelled(812L, 101L, start, 9L, "ABSENCE", 33L, "Family skipped the visit", 2, later),
				List.of("visitId", "elderId", "scheduledStart", "caregiverId", "reason", "absenceId", "note", "version",
						"cancelledAt"));
		fields.put(new VisitCheckedIn(812L, 101L, start, 9L, later, false, 3),
				List.of("visitId", "elderId", "scheduledStart", "caregiverId", "checkedInAt", "resumedAfterMissedCheckIn",
						"version"));
		fields.put(new VisitCompleted(812L, 101L, start, 9L, start, later, 4),
				List.of("visitId", "elderId", "scheduledStart", "caregiverId", "checkedInAt", "checkedOutAt", "version"));
		fields.put(new VisitExceptionRaised(812L, 101L, start, 9L, "CAREGIVER_REPORT", null, 601L, true, 5),
				List.of("visitId", "elderId", "scheduledStart", "caregiverId", "cause", "absenceId", "incidentId",
						"checkedIn", "version"));
		fields.put(new VisitTaskRecorded(3051L, 812L, 101L, start, "Bathing", "DONE", null, "Enjoyed the bath", later),
				List.of("taskId", "visitId", "elderId", "visitScheduledStart", "name", "status", "outcome", "caregiverNote",
						"recordedAt"));
		fields.put(new VitalsRecorded(77L, 812L, 101L, start, later, "ATTENTION", "Slightly warm",
				List.of(new VitalsRecorded.Reading("temperature", "37.80", "°C", false))),
				List.of("healthRecordId", "visitId", "elderId", "visitScheduledStart", "recordedAt", "healthFlag",
						"healthNote", "readings"));
		fields.put(new ElderConfirmationSubmitted(812L, 101L, start, "CONFIRMED", 5, "Very kind", later, null),
				List.of("visitId", "elderId", "visitScheduledStart", "status", "rating", "comment", "confirmedAt",
						"incidentId"));

		fields.forEach((event, names) -> {
			JsonNode written = json.readTree(json.writeValueAsString(event));
			assertThat(written.propertyNames()).as(event.getClass().getSimpleName()).containsExactlyElementsOf(names);
			// The same moments come back, in whatever offset the reader hands them (UTC here)
			assertThat(json.readValue(json.writeValueAsString(event), event.getClass())).usingRecursiveComparison()
					.withComparatorForType(Comparator.comparing(OffsetDateTime::toInstant), OffsetDateTime.class)
					.isEqualTo(event);
		});
		JsonNode vitals = json.readTree(json.writeValueAsString(fields.keySet().stream()
				.filter(VitalsRecorded.class::isInstance).findFirst().orElseThrow()));
		assertThat(vitals.get("readings").get(0).get("value").asString()).isEqualTo("37.80");
		assertThat(vitals.get("visitScheduledStart").asString()).isEqualTo("2026-10-12T09:35:00+08:00");
		assertThat(List.of(VisitScheduled.TYPE, VisitCaregiverChanged.TYPE, VisitCancelled.TYPE, VisitCheckedIn.TYPE,
				VisitCompleted.TYPE, VisitExceptionRaised.TYPE, VisitTaskRecorded.TYPE, VitalsRecorded.TYPE,
				ElderConfirmationSubmitted.TYPE))
				.containsExactly("VisitScheduled", "VisitCaregiverChanged", "VisitCancelled", "VisitCheckedIn",
						"VisitCompleted", "VisitExceptionRaised", "VisitTaskRecorded", "VitalsRecorded",
						"ElderConfirmationSubmitted");
	}

	@Test
	void aSetOfReadingsIsNeverNull() {
		assertThat(new VitalsRecorded(77L, 812L, 101L, null, null, null, null, null).readings()).isEmpty();
	}

	@Test
	void anInAppMessageIsCutToWhatTheInboxKeeps() {
		var message = NotificationRequested.inApp(7L, "INCIDENT_RAISED", "t".repeat(200), "b".repeat(1200), "INCIDENT",
				42L, 101L, HALF_NINE);

		assertThat(message.channel()).isEqualTo("IN_APP");
		assertThat(message.title()).hasSize(150).endsWith("...");
		assertThat(message.body()).hasSize(1000).endsWith("...");
		assertThat(message.elderId()).isEqualTo(101L);
		assertThat(message.requestedAt()).isEqualTo(SingaporeTime.of(HALF_NINE));
		assertThat(NotificationRequested.inApp(7L, "K", "Short", null, null, null, null, HALF_NINE))
				.extracting(NotificationRequested::title, NotificationRequested::body).containsExactly("Short", null);
	}

}
