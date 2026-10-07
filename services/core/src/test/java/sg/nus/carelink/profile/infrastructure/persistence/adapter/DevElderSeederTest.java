package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.ElderJpaRepository;

/** Runs only under the "dev" profile; here it is exercised directly, bypassing @Profile. */
class DevElderSeederTest {

	private final ElderJpaRepository elders = mock(ElderJpaRepository.class);
	private final DevElderSeeder seeder = new DevElderSeeder(elders);

	@Test
	void seedsSixMockEldersWhenTheTableIsEmpty() {
		when(elders.count()).thenReturn(0L);

		seeder.run();

		verify(elders, times(6)).save(any(ElderJpaEntity.class));
	}

	@Test
	void doesNothingWhenTheTableAlreadyHasRows() {
		when(elders.count()).thenReturn(1L);

		seeder.run();

		verify(elders, never()).save(any());
	}
}
