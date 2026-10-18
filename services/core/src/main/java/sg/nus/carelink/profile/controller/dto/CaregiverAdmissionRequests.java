package sg.nus.carelink.profile.controller.dto;

import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import sg.nus.carelink.shared.validation.SingaporeFormats;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.Details;

public final class CaregiverAdmissionRequests {
    private CaregiverAdmissionRequests() { }
    public record Registration(
            @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9._-]{2,63}") String username,
            @NotBlank @Size(min=8,max=72) String password,
            @NotBlank @Size(max=100) @Pattern(regexp=SingaporeFormats.PERSON_NAME) String fullName,
            @NotBlank @Pattern(regexp=SingaporeFormats.MOBILE) String phone,
            @Size(max=100) @Pattern(regexp=SingaporeFormats.DIALECT_LIST) String declaredDialects,
            @Size(max=50) @Pattern(regexp="[A-Za-z0-9 -]+") String requestedSector) {
        public Registration {
            username=username==null ? null : username.strip();
            fullName=SingaporeFormats.normalizeName(fullName); phone=SingaporeFormats.normalizePhone(phone);
            declaredDialects=SingaporeFormats.normalizeDialects(declaredDialects);
            requestedSector=blank(requestedSector);
        }
        @AssertTrue(message="Password must be at most 72 UTF-8 bytes")
        public boolean isPasswordWithinBcryptLimit() { return password!=null && password.getBytes(StandardCharsets.UTF_8).length<=72; }
        @JsonAnySetter public void unexpected(String name,Object value) { throw new IllegalArgumentException("Unknown registration field: " + name); }
        public Details details() { return new Details(fullName,phone,declaredDialects,requestedSector); }
    }
    public record Edit(
            @NotBlank @Size(max=100) @Pattern(regexp=SingaporeFormats.PERSON_NAME) String fullName,
            @NotBlank @Pattern(regexp=SingaporeFormats.MOBILE) String phone,
            @Size(max=100) @Pattern(regexp=SingaporeFormats.DIALECT_LIST) String declaredDialects,
            @Size(max=50) @Pattern(regexp="[A-Za-z0-9 -]+") String requestedSector,
            @NotNull @PositiveOrZero Long expectedVersion, @NotBlank String clientRequestId) {
        public Edit {
            fullName=SingaporeFormats.normalizeName(fullName); phone=SingaporeFormats.normalizePhone(phone);
            declaredDialects=SingaporeFormats.normalizeDialects(declaredDialects); requestedSector=blank(requestedSector);
        }
        @JsonAnySetter public void unexpected(String name,Object value) { throw new IllegalArgumentException("Unknown application field: " + name); }
        public Details details() { return new Details(fullName,phone,declaredDialects,requestedSector); }
    }
    public record Command(@NotNull @PositiveOrZero Long expectedVersion,@NotBlank String clientRequestId) {
        @JsonAnySetter public void unexpected(String name,Object value) { throw new IllegalArgumentException("Unknown command field: " + name); }
    }
    public record Contact(@NotBlank @Pattern(regexp=SingaporeFormats.MOBILE) String phone,
            @NotNull @PositiveOrZero Long expectedVersion,@NotBlank String clientRequestId) {
        public Contact { phone=SingaporeFormats.normalizePhone(phone); }
        @JsonAnySetter public void unexpected(String name,Object value) { throw new IllegalArgumentException("Unknown contact field: " + name); }
    }
    private static String blank(String value) { return value==null || value.isBlank() ? null : value.strip(); }
}
