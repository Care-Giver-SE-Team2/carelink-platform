package sg.nus.carelink.profile.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import sg.nus.carelink.shared.validation.SingaporeFormats;

/**
 * A family member's sign-up. The username is what they type at the landing page's sign-in, so it
 * is kept to lower-case letters, digits and . _ - with no spaces; the password's 72-byte cap is
 * BCrypt's, past which it would silently ignore the rest.
 */
public record FamilyRegistrationRequest(
		@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,63}",
				message = "Use 3 to 64 lower-case letters, digits, dots, dashes or underscores") String username,
		@NotBlank @Size(min = 8, max = 72) String password,
		@NotBlank @CodePointLength(max = 100)
		@Pattern(regexp = SingaporeFormats.PERSON_NAME, message = SingaporeFormats.PERSON_NAME_MESSAGE) String fullName,
		@NotBlank @Pattern(regexp = SingaporeFormats.MOBILE, message = SingaporeFormats.MOBILE_MESSAGE) String phone) {

	public FamilyRegistrationRequest {
		username = username == null ? null : username.strip();
		fullName = SingaporeFormats.normalizeName(fullName);
		phone = SingaporeFormats.normalizePhone(phone);
	}
}
