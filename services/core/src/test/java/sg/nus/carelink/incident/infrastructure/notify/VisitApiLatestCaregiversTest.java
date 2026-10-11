package sg.nus.carelink.incident.infrastructure.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.visitapi.VisitApi;

/** visit's internal API names the elder's latest caregiver, or nobody. */
class VisitApiLatestCaregiversTest {

	private final VisitApi visit = mock(VisitApi.class);

	@Test
	void visitNamesTheCaregiverOrNobody() {
		when(visit.findLatestCaregiverId(101L)).thenReturn(Optional.of(7L));
		when(visit.findLatestCaregiverId(102L)).thenReturn(Optional.empty());
		VisitApiLatestCaregivers caregivers = new VisitApiLatestCaregivers(visit);

		assertThat(caregivers.latestCaregiverId(101L)).contains(7L);
		assertThat(caregivers.latestCaregiverId(102L)).isEmpty();
	}

}
