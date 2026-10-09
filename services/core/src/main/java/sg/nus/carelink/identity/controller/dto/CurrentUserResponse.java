package sg.nus.carelink.identity.controller.dto;

import sg.nus.carelink.identity.domain.model.AppUser;

import java.util.List;

/** The front end decides which set of screens to show based on the roles listed here. */
public record CurrentUserResponse(Long id, String username, String displayName, List<String> roles) {

	public static CurrentUserResponse from(AppUser user) {
		return new CurrentUserResponse(
				user.id(),
				user.username(),
				user.displayName(),
				user.roles().stream().map(Enum::name).sorted().toList());
	}
}
