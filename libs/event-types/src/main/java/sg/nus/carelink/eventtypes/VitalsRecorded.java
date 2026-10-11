package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A set of readings was recorded at a visit. Sensitive: health data, routed to report only.
 *
 * @param healthFlag the caregiver's own judgement: {@code NO_CONCERN}, {@code ATTENTION} or {@code MEDICAL_REVIEW}
 */
public record VitalsRecorded(Long healthRecordId, Long visitId, Long elderId, OffsetDateTime visitScheduledStart,
		OffsetDateTime recordedAt, String healthFlag, String healthNote, List<Reading> readings) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VitalsRecorded";

	public VitalsRecorded {
		readings = readings == null ? List.of() : List.copyOf(readings);
	}

	/**
	 * @param metric {@code systolic}, {@code diastolic}, {@code pulse} or {@code temperature}
	 * @param value the reading as stored, such as "37.80"
	 * @param outOfRange the flag as stored
	 */
	public record Reading(String metric, String value, String unit, boolean outOfRange) {
	}

}
