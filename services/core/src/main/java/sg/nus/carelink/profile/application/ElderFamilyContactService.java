package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;

/**
 * UC-MG01: who the elder's family is, for the manager drawing up the care plan. Only bindings that
 * currently grant access count — a pending, rejected, revoked or expired one is not the elder's
 * family as far as the plan is concerned.
 */
@Service
@Transactional(readOnly = true)
public class ElderFamilyContactService {

	private static final ZoneId CARELINK_ZONE = ZoneId.of("Asia/Singapore");

	private final ElderRepository elders;
	private final ElderFamilyBindingRepository bindings;
	private final FamilyMemberRepository familyMembers;
	private final Clock clock;

	public ElderFamilyContactService(ElderRepository elders, ElderFamilyBindingRepository bindings,
			FamilyMemberRepository familyMembers, Clock clock) {
		this.elders = elders;
		this.bindings = bindings;
		this.familyMembers = familyMembers;
		this.clock = clock;
	}

	/** Primary contact first; empty when the elder is unknown, so the caller can tell 404 from none. */
	public Optional<List<ElderFamilyContact>> listForElder(Long elderId) {
		if (elders.findById(elderId).isEmpty()) {
			return Optional.empty();
		}
		LocalDateTime now = LocalDateTime.now(clock.withZone(CARELINK_ZONE));
		return Optional.of(bindings.findByElderId(elderId).stream()
				.filter(binding -> binding.allowsReadAt(now))
				.sorted(Comparator.comparing(ElderFamilyBinding::isPrimaryContact).reversed())
				.flatMap(binding -> familyMembers.findById(binding.familyMemberId())
						.map(member -> new ElderFamilyContact(
								member.fullName(), binding.relationship(), binding.isPrimaryContact()))
						.stream())
				.toList());
	}
}
