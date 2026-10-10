package sg.nus.carelink.profile.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;

class FamilyBindingCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static FamilyBindingCreateRequest request(String username) {
        return new FamilyBindingCreateRequest(username, ElderFamilyBinding.Relationship.DAUGHTER, false,
                ElderFamilyBinding.AccessScope.FULL);
    }

    @Test
    void findsTheUsernameHoweverItWasCapitalised() {
        var request = request(" Lim.WeiLing ");

        assertThat(request.familyUsername()).isEqualTo("lim.weiling");
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "li", "lim weiling", "-lim", "lim@family" })
    void rejectsWhatCannotBeAUsername(String username) {
        assertThat(validator.validate(request(username))).isNotEmpty();
    }
}
