package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.ElderJpaRepository;

/**
 * Local-only mock data, so the Elders/Care plan screens have something to show against a
 * real database. Runs only under the "dev" profile (SPRING_PROFILES_ACTIVE=dev, or
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev}) and only when the table
 * is empty, so restarting the app never duplicates rows. Never runs in tests or in a
 * deployed environment.
 */
@Component
@Profile("dev")
class DevElderSeeder implements CommandLineRunner {

	private final ElderJpaRepository elders;

	DevElderSeeder(ElderJpaRepository elders) {
		this.elders = elders;
	}

	@Override
	public void run(String... args) {
		if (elders.count() > 0) {
			return;
		}
		elders.save(elder("Chan Bee Choo", LocalDate.of(1943, 3, 2), "S31",
				"Blk 217 Bishan St 23, #08-142", "570217", ElderJpaEntity.MobilityLevel.ASSISTIVE_CANE));
		elders.save(elder("Beatrice Lim Swee Hong", LocalDate.of(1947, 7, 19), "S31",
				"Blk 337 Ang Mo Kio Ave 3, #05-88", "560337", ElderJpaEntity.MobilityLevel.INDEPENDENT));
		elders.save(elder("Goh Bee Lian", LocalDate.of(1938, 11, 5), "S31",
				"Blk 154 Bishan St 11, #12-06", "570154", ElderJpaEntity.MobilityLevel.WHEELCHAIR_BEDBOUND));
		elders.save(elder("Kamala Devi Rajan", LocalDate.of(1950, 1, 27), "S34",
				"Blk 78 Toa Payoh Lor 4, #03-221", "310078", ElderJpaEntity.MobilityLevel.ASSISTIVE_CANE));
		elders.save(elder("Ong Kim Bee", LocalDate.of(1935, 9, 14), "S31",
				"Blk 221 Bishan St 22, #10-317", "570221", ElderJpaEntity.MobilityLevel.WHEELCHAIR_BEDBOUND));
		elders.save(elder("Tan Ah Bee", LocalDate.of(1942, 5, 30), "S34",
				"Blk 419 Ang Mo Kio Ave 10, #02-55", "560419", ElderJpaEntity.MobilityLevel.INDEPENDENT));
	}

	private static ElderJpaEntity elder(String fullName, LocalDate dateOfBirth, String sector,
			String address, String postalCode, ElderJpaEntity.MobilityLevel mobilityLevel) {
		ElderJpaEntity e = new ElderJpaEntity();
		e.setFullName(fullName);
		e.setDateOfBirth(dateOfBirth);
		e.setSector(sector);
		e.setAddress(address);
		e.setPostalCode(postalCode);
		e.setMobilityLevel(mobilityLevel);
		e.setLivesAlone(Boolean.TRUE);
		e.setContinuityPreference(ElderJpaEntity.ContinuityPreference.PREFERRED);
		return e;
	}
}
