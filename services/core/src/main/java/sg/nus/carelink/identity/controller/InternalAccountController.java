package sg.nus.carelink.identity.controller;

import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.identity.application.AccountsByRole;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * identity's part of core's internal API ({@link CoreApi}): an account as it stands now, for a
 * service that checks on every request whether the account behind a session may still act, and
 * who holds a role, for a service that writes to every manager.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalAccountController {

	private final UserDirectory users;

	private final AccountsByRole roles;

	InternalAccountController(UserDirectory users, AccountsByRole roles) {
		this.users = users;
		this.roles = roles;
	}

	@GetMapping("/accounts/{username}")
	public CoreApi.Account account(@PathVariable String username) {
		return users.findByUsername(username)
				.map(user -> new CoreApi.Account(user.id(), user.username(), user.displayName(), user.enabled(),
						user.roles().stream().map(Role::name).collect(Collectors.toSet())))
				.orElseThrow(() -> new ResourceNotFound("Account", username));
	}

	@GetMapping("/accounts/with-role/{role}")
	public CoreApi.AccountIds enabledAccountsWithRole(@PathVariable Role role) {
		return new CoreApi.AccountIds(roles.enabledIdsWithRole(role));
	}

}
