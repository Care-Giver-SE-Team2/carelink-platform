package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding.AccessScope;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding.Relationship;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding.Status;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;

class ElderFamilyContactServiceTest {

	/** 2026-09-23 00:30 in Singapore. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T16:30:00Z"), ZoneOffset.UTC);
	private static final LocalDateTime NOW_SGT = LocalDateTime.of(2026, 9, 23, 0, 30);

	private final InMemoryElderRepository elders = new InMemoryElderRepository();
	private final ElderFamilyBindingRepository bindings = mock(ElderFamilyBindingRepository.class);
	private final InMemoryFamilyMemberRepository familyMembers = new InMemoryFamilyMemberRepository();
	private final ElderFamilyContactService service =
			new ElderFamilyContactService(elders, bindings, familyMembers, CLOCK);

	@Test
	void listsOnlyBindingsThatGrantAccessNowWithThePrimaryContactFirst() {
		Long elderId = saveElder();
		family(11L, "Ah Kow");
		family(12L, "Wei Ling");
		family(13L, "Pending Son");
		family(14L, "Expired Guardian");
		when(bindings.findByElderId(elderId)).thenReturn(List.of(
				binding(elderId, 11L, Relationship.SON, false, Status.ACTIVE, null),
				binding(elderId, 12L, Relationship.DAUGHTER, true, Status.ACTIVE, null),
				binding(elderId, 13L, Relationship.SON, false, Status.PENDING_CONFIRMATION, null),
				binding(elderId, 14L, Relationship.GUARDIAN, false, Status.ACTIVE, NOW_SGT.minusMinutes(1))));

		assertThat(service.listForElder(elderId)).contains(List.of(
				new ElderFamilyContact("Wei Ling", Relationship.DAUGHTER, true),
				new ElderFamilyContact("Ah Kow", Relationship.SON, false)));
	}

	@Test
	void isEmptyListForAnElderWithNoFamily() {
		Long elderId = saveElder();
		when(bindings.findByElderId(elderId)).thenReturn(List.of());

		assertThat(service.listForElder(elderId)).contains(List.of());
	}

	@Test
	void isEmptyForAnUnknownElder() {
		assertThat(service.listForElder(999L)).isEmpty();
	}

	private Long saveElder() {
		return elders.save(new Elder(null, null, "Chan Bee Choo", Elder.Gender.FEMALE, LocalDate.of(1943, 1, 1),
				null, null, null, null, null, Boolean.TRUE, Elder.MobilityLevel.INDEPENDENT,
				Elder.ContinuityPreference.PREFERRED, null, NOW_SGT, NOW_SGT)).id();
	}

	private void family(Long id, String name) {
		familyMembers.save(new FamilyMember(id, null, name, null, null, NOW_SGT, NOW_SGT));
	}

	private static ElderFamilyBinding binding(Long elderId, Long familyMemberId, Relationship relationship,
			boolean primary, Status status, LocalDateTime expiresAt) {
		return new ElderFamilyBinding(null, elderId, familyMemberId, relationship, primary, AccessScope.FULL,
				status, NOW_SGT, expiresAt, NOW_SGT, NOW_SGT);
	}
}
