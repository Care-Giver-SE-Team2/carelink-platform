package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.security.Role;

/**
 * Resolves the current family profile and checks binding access using Singapore time.
 *
 * @author Wang Zhili
 */
@Service
@Transactional(readOnly = true)
class FamilyAccessQueryService implements FamilyAccessQuery {

	private static final ZoneId CARELINK_ZONE = ZoneId.of("Asia/Singapore");

	private final UserDirectory users;
	private final FamilyMemberRepository families;
	private final ElderFamilyBindingRepository bindings;
	private final Clock clock;

	FamilyAccessQueryService(UserDirectory users, FamilyMemberRepository families,
			ElderFamilyBindingRepository bindings, Clock clock) {
		this.users = users;
		this.families = families;
		this.bindings = bindings;
		this.clock = clock;
	}

	@Override
	public Set<Long> readableElderIds(String authenticatedUsername) {
		Long familyMemberId = requireFamilyMemberId(authenticatedUsername);
		LocalDateTime now = LocalDateTime.now(clock.withZone(CARELINK_ZONE));
		return bindings.findByFamilyMemberId(familyMemberId).stream()
				.filter(binding -> binding.allowsReadAt(now))
				.map(ElderFamilyBinding::elderId)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public void requireReadableElder(String authenticatedUsername, Long elderId) {
		Long familyMemberId = requireFamilyMemberId(authenticatedUsername);
		if (elderId == null || elderId <= 0) {
			throw new AccessDeniedException("A readable elder binding is required");
		}
		LocalDateTime now = LocalDateTime.now(clock.withZone(CARELINK_ZONE));
		bindings.findByElderIdAndFamilyMemberId(elderId, familyMemberId)
				.filter(binding -> binding.allowsReadAt(now))
				.orElseThrow(() -> new AccessDeniedException("A readable elder binding is required"));
	}

	private Long requireFamilyMemberId(String authenticatedUsername) {
		if (authenticatedUsername == null || authenticatedUsername.isBlank()) {
			throw new AccessDeniedException("An authenticated family account is required");
		}
		var account = users.findByUsername(authenticatedUsername)
				.filter(user -> user.enabled() && user.hasRole(Role.FAMILY))
				.orElseThrow(() -> new AccessDeniedException("An enabled family account is required"));
		return families.findByUserId(account.id())
				.orElseThrow(() -> new AccessDeniedException("A family profile is required"))
				.id();
	}
}
