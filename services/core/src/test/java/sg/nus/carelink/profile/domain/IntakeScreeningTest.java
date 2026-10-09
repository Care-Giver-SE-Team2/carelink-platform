package sg.nus.carelink.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.service.IntakeScreening;
import sg.nus.carelink.profile.domain.service.IntakeScreening.Check;
import sg.nus.carelink.profile.domain.service.IntakeScreening.CheckKey;

/** The sector an application falls in, learned from nearby elders, and the four checks shown to the manager. */
class IntakeScreeningTest {

	private final IntakeScreening screening = new IntakeScreening();

	private static final FamilyMember GRACE =
			new FamilyMember(7L, 70L, "Grace Tan Wei Ling", "+65 9123 4488", null, null, null);

	@Test
	void theSectorIsTheOneMostEldersInTheSamePostalSectorHold() {
		List<Elder> elders = List.of(elder(1L, "A", "570217", "S31"), elder(2L, "B", "570154", "S31"),
				elder(3L, "C", "571000", "S34"), elder(4L, "D", "310078", "S34"));

		assertThat(screening.sectorFor("570230", elders)).isEqualTo("S31");
		assertThat(screening.sectorFor("310052", elders)).isEqualTo("S34");
	}

	@Test
	void aTieGoesToTheAlphabeticallyFirstSector() {
		List<Elder> elders = List.of(elder(1L, "A", "560337", "S34"), elder(2L, "B", "560419", "S31"));

		assertThat(screening.sectorFor("560112", elders)).isEqualTo("S31");
	}

	@Test
	void withNoElderNearbyOrNoUsablePostcodeTheSectorIsUnknown() {
		List<Elder> elders = List.of(elder(1L, "A", "570217", "S31"), elder(2L, "B", "760001", null));

		assertThat(screening.sectorFor("760708", elders)).isNull();
		assertThat(screening.sectorFor("A1", elders)).isNull();
		assertThat(screening.sectorFor(null, elders)).isNull();
	}

	@Test
	void everyCheckPassesForACoveredApplication() {
		List<Elder> elders = List.of(elder(1L, "Chan Bee Choo", "570217", "S31"));
		List<Caregiver> caregivers = List.of(
				caregiver(1L, "S31", "Hokkien, Mandarin", Caregiver.Status.AVAILABLE),
				caregiver(2L, "s31", "hokkien", Caregiver.Status.BUSY),
				caregiver(3L, "S31", "Hokkien", Caregiver.Status.ONBOARDING),
				caregiver(4L, "S31", "Malay", Caregiver.Status.AVAILABLE),
				caregiver(5L, "S34", "Hokkien", Caregiver.Status.AVAILABLE));

		IntakeScreening.Result result = screening.screen(application("Tan Bee Choo", "570230", "Hokkien"), GRACE,
				elders, caregivers);

		assertThat(result.sector()).isEqualTo("S31");
		assertThat(result.checks()).containsExactly(
				new Check(CheckKey.CONTACT, true, null),
				new Check(CheckKey.SECTOR, true, 2), // available only
				new Check(CheckKey.DIALECT, true, 2)); // available or busy, in the sector
	}

	@Test
	void anUnknownSectorFailsTheSectorAndDialectChecksAndAMissingPhoneFailsContact() {
		FamilyMember noPhone = new FamilyMember(8L, 80L, "Rachel Ng", " ", null, null, null);

		List<Check> checks = screening.screen(application("Ng Kim Lan", "760708", "Teochew"), noPhone, List.of(),
				List.of(caregiver(1L, "S31", "Teochew", Caregiver.Status.AVAILABLE))).checks();

		assertThat(checks).containsExactly(
				new Check(CheckKey.CONTACT, false, null),
				new Check(CheckKey.SECTOR, false, null),
				new Check(CheckKey.DIALECT, false, null));
	}

	@Test
	void aCoveredSectorWithNobodyFreeFailsWithACountOfZero() {
		List<Check> checks = screening.screen(application("Tan Bee Choo", "570230", "Hokkien"), GRACE,
				List.of(elder(1L, "A", "570217", "S31")),
				List.of(caregiver(1L, "S31", "Malay", Caregiver.Status.BUSY))).checks();

		assertThat(checks).contains(new Check(CheckKey.SECTOR, false, 0),
				new Check(CheckKey.DIALECT, false, 0));
	}

	private static IntakeApplication application(String name, String postcode, String dialects) {
		return new IntakeApplication(42L, 7L, name, 83, "Blk 230 Bishan St 23", postcode,
				IntakeApplication.MobilityLevel.INDEPENDENT, dialects, List.of(), null,
				IntakeApplication.Status.SUBMITTED, null, null, LocalDateTime.of(2026, 10, 5, 1, 0), null, null);
	}

	private static Elder elder(Long id, String name, String postcode, String sector) {
		return new Elder(id, null, name, null, null, null, null, postcode, sector, null, null, null,
				Elder.ContinuityPreference.PREFERRED, null, null, null);
	}

	private static Caregiver caregiver(Long id, String sector, String dialects, Caregiver.Status status) {
		return new Caregiver(id, 100L + id, "Caregiver " + id, null, sector, dialects, status, null, null);
	}
}
