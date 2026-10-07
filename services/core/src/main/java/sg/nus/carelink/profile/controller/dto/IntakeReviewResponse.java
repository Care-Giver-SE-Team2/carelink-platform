package sg.nus.carelink.profile.controller.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import sg.nus.carelink.profile.application.IntakeReviewRow;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeApplication.MobilityLevel;
import sg.nus.carelink.profile.domain.model.IntakeApplication.Status;
import sg.nus.carelink.profile.domain.service.IntakeScreening;

/**
 * One row of GET /api/intake-reviews: the application exactly as the family app returns it
 * (same field names), plus the applicant, the sector and the screening checks. Check keys are
 * lower-case: contact, sector, dialect.
 */
public record IntakeReviewResponse(
		Long id, Long applicantFamilyMemberId, Applicant applicant, String targetElderName, Integer targetElderAge,
		String targetAddress, String postalCode, MobilityLevel mobilityLevel, String preferredDialects,
		List<String> careNeeds, String medicalNotes, Status status, String reviewRemarks,
		OffsetDateTime createdAt, OffsetDateTime reviewedAt, Long elderId, String sector, List<Check> checks) {

	public record Applicant(String fullName, String username, String phone) {
	}

	public record Check(String key, boolean pass, Integer count) {
	}

	public static IntakeReviewResponse from(IntakeReviewRow row) {
		IntakeApplication a = row.application();
		return new IntakeReviewResponse(a.id(), a.applicantFamilyMemberId(),
				new Applicant(row.applicant().fullName(), row.applicant().username(), row.applicant().phone()),
				a.targetElderName(), a.targetElderAge(), a.targetAddress(), a.postalCode(), a.mobilityLevel(),
				a.preferredDialects(), a.careNeeds(), a.medicalNotes(), a.status(), a.reviewRemarks(),
				utc(a.createdAt()), utc(a.reviewedAt()), a.elderId(), row.sector(),
				row.checks().stream().map(IntakeReviewResponse::check).toList());
	}

	private static Check check(IntakeScreening.Check c) {
		return new Check(c.key().name().toLowerCase(Locale.ROOT), c.pass(), c.count());
	}

	/** Stored timestamps are UTC, as in FamilyIntakeApplicationResponse. */
	private static OffsetDateTime utc(LocalDateTime time) {
		return time == null ? null : time.atOffset(ZoneOffset.UTC);
	}
}
