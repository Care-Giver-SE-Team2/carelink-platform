package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.security.Role;

/** Enumerates bindings, then rechecks each recipient in its delivery transaction. @author Wang Zhili */
@Service
@Transactional(readOnly = true)
class FamilyAlertRecipientsService implements FamilyAlertRecipients {
	private final ElderFamilyBindingRepository bindings;
	private final FamilyMemberRepository families;
	private final UserDirectory users;
	private final Clock clock;

	FamilyAlertRecipientsService(ElderFamilyBindingRepository bindings, FamilyMemberRepository families,
			UserDirectory users, Clock clock) {
		this.bindings = bindings; this.families = families; this.users = users; this.clock = clock;
	}

	@Override public List<Long> familyMemberIds(Long elderId) {
		return bindings.findByElderId(elderId).stream().map(ElderFamilyBinding::familyMemberId).sorted().toList();
	}

	@Override public Candidate resolve(Long elderId, Long familyId) {
		var family = families.findById(familyId).orElse(null);
		Long accountId = family == null ? null : family.userId();
		var binding = bindings.findByElderIdAndFamilyMemberId(elderId, familyId).orElse(null);
		String reason;
		if (binding == null) { reason = "BINDING_MISSING"; }
		else if (binding.status() != ElderFamilyBinding.Status.ACTIVE) { reason = "BINDING_" + binding.status().name(); }
		else if (!binding.allowsReadAt(LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore"))))) { reason = "BINDING_EXPIRED"; }
		else {
			var user = accountId == null ? null : users.findById(accountId).orElse(null);
			if (user == null) { reason = "ACCOUNT_MISSING"; }
			else if (!user.enabled()) { reason = "ACCOUNT_DISABLED"; }
			else if (!user.hasRole(Role.FAMILY)) { reason = "FAMILY_ROLE_MISSING"; }
			else { reason = null; }
		}
		return new Candidate(familyId, accountId, reason);
	}
}
