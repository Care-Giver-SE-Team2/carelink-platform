package sg.nus.carelink.report.infrastructure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;
import sg.nus.carelink.report.application.CaregiverDirectory.CaregiverPublicProfile;
import sg.nus.carelink.report.application.VisitCover;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * report's adapters on core's internal API hand report what core answers, in report's own types,
 * and core's refusals become the errors report threw when core ran in the same process.
 */
class CoreAdaptersTest {

	private final CoreApi core = mock(CoreApi.class);

	@Test
	void familyAccessIsCoresAnswerAndARefusalReachesTheCaller() {
		CoreFamilyAccessQuery access = new CoreFamilyAccessQuery(core);
		when(core.readableElders("family-a")).thenReturn(new CoreApi.ReadableElders(Set.of(101L, 102L)));
		doThrow(new AccessDeniedException("A writable elder binding is required"))
				.when(core).checkElderAccess("family-a", 102L, CoreApi.Access.WRITE);

		assertThat(access.readableElderIds("family-a")).containsExactlyInAnyOrder(101L, 102L);
		access.requireReadableElder("family-a", 102L);
		verify(core).checkElderAccess("family-a", 102L, CoreApi.Access.READ);
		assertThatThrownBy(() -> access.requireWritableElder("family-a", 102L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void aCaregiversPublicProfileIsCoresOrNone() {
		CoreCaregiverDirectory caregivers = new CoreCaregiverDirectory(core);
		when(core.findCaregiverPublicProfile(31L))
				.thenReturn(Optional.of(new CoreApi.CaregiverPublicProfile(31L, "John Tan", List.of("Hokkien"))));
		when(core.findCaregiverPublicProfile(99L)).thenReturn(Optional.empty());

		assertThat(caregivers.findPublicProfile(31L))
				.contains(new CaregiverPublicProfile(31L, "John Tan", List.of("Hokkien")));
		assertThat(caregivers.findPublicProfile(99L)).isEmpty();
	}

	@Test
	void anAccountsFamilyProfileIsCoresOrNone() {
		when(core.findFamilyMemberIdByUser(7L)).thenReturn(Optional.of(42L));

		assertThat(new CoreFamilyMembers(core).findIdByUserId(7L)).contains(42L);
	}

	@Test
	void anElderAccountsElderIsCoresAndAMissingOneIsNotFound() {
		CoreElders elders = new CoreElders(core);
		when(core.elderByUser(15L)).thenReturn(new CoreApi.ElderRef(101L));
		when(core.elderByUser(16L)).thenThrow(new CoreNotFound("Elder for user [16] does not exist"));

		assertThat(elders.requireElderIdOfUser(15L)).isEqualTo(101L);
		assertThatThrownBy(() -> elders.requireElderIdOfUser(16L))
				.isInstanceOf(ResourceNotFound.class)
				.hasMessage("Elder for user [16] does not exist");
	}

	@Test
	void coverOptionsAreRosteringsRankingInReportsTypes() {
		when(core.coverOptions(812L)).thenReturn(List.of(new CoreApi.CoverOption(9L, "Farah", 1, "Continuity"),
				new CoreApi.CoverOption(5L, "Aisha", null, "Busy then")));

		List<VisitCover.Option> options = new CoreVisitCover(core).options(812L);

		assertThat(options).containsExactly(new VisitCover.Option(9L, "Farah", 1, "Continuity"),
				new VisitCover.Option(5L, "Aisha", null, "Busy then"));
		assertThat(options).extracting(VisitCover.Option::eligible).containsExactly(true, false);
	}

	@Test
	void coveringAVisitKeepsRosteringsRefusalAndItsCode() {
		CoreVisitCover cover = new CoreVisitCover(core);
		doThrow(new CoreRuleViolation("VISIT_ALREADY_COVERED", "Somebody is already on this visit."))
				.when(core).cover(812L, new CoreApi.CoverRequest(9L, 10L));
		when(core.coverOptions(999L)).thenThrow(new CoreNotFound("Visit [999] does not exist"));
		doThrow(new CoreNotFound("Visit [998] does not exist")).when(core).cover(998L, new CoreApi.CoverRequest(9L, 10L));

		assertThatThrownBy(() -> cover.cover(812L, 9L, 10L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("Somebody is already on this visit.")
				.extracting(error -> ((BusinessRuleViolation) error).code()).isEqualTo("VISIT_ALREADY_COVERED");
		assertThatThrownBy(() -> cover.options(999L)).isInstanceOf(ResourceNotFound.class)
				.hasMessage("Visit [999] does not exist");
		assertThatThrownBy(() -> cover.cover(998L, 9L, 10L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void coveringAVisitAsksRostering() {
		new CoreVisitCover(core).cover(812L, 9L, 10L);

		verify(core).cover(812L, new CoreApi.CoverRequest(9L, 10L));
	}

}
