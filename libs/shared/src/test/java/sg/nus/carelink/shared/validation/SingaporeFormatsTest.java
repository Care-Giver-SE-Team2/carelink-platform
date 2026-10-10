package sg.nus.carelink.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SingaporeFormatsTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"91234567        | +6591234567",
			"' 9123 4567 '   | +6591234567",
			"+65 9123-4567   | +6591234567",
			"6591234567      | +6591234567",
			"(+65) 6123 4567 | +6561234567",
			"call me         | call me",
			"1234567         | 1234567"})
	void normalizesWhatPeopleTypeToPlus65AndEightDigits(String typed, String saved) {
		assertThat(SingaporeFormats.normalizePhone(typed)).isEqualTo(saved);
	}

	@Test
	void blankPhoneBecomesNull() {
		assertThat(SingaporeFormats.normalizePhone("  ")).isNull();
		assertThat(SingaporeFormats.normalizePhone(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "+6561234567", "+6581234567", "+6591234567", "+6531234567" })
	void acceptsLandlineMobileAndInternetNumbers(String phone) {
		assertThat(phone).matches(SingaporeFormats.PHONE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "+6511234567", "+6571234567", "+659123456", "91234567", "+6091234567" })
	void rejectsNumbersSingaporeDoesNotIssue(String phone) {
		assertThat(phone).doesNotMatch(SingaporeFormats.PHONE);
	}

	@Test
	void mobileOnlyAcceptsEightAndNine() {
		assertThat("+6591234567").matches(SingaporeFormats.MOBILE);
		assertThat("+6581234567").matches(SingaporeFormats.MOBILE);
		assertThat("+6561234567").doesNotMatch(SingaporeFormats.MOBILE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "018956", "560123", "310088", "730001", "750001", "828761" })
	void acceptsPostalCodesInRealDistricts(String code) {
		assertThat(code).matches(SingaporeFormats.POSTAL_CODE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "000000", "001234", "741234", "831234", "991234", "56012", "5601234", "56O123" })
	void rejectsPostalCodesOutsideTheDistricts(String code) {
		assertThat(code).doesNotMatch(SingaporeFormats.POSTAL_CODE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Tan Ah Kow", "Ravi s/o Krishnan", "Siti Nurhaliza binte Ahmad", "Tan Ah Kow @ Chen Ah Kow",
			"O'Brien-Lim", "Dr. Lee", "陈亚九", "முருகன்", "Zoë" })
	void acceptsNamesAsSingaporeansWriteThem(String name) {
		assertThat(name).matches(SingaporeFormats.PERSON_NAME);
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345", "Tan 2", "---", "@/", "tan_ah_kow", "Tan 😀" })
	void rejectsNamesWithoutLettersOrWithDigitsAndSymbols(String name) {
		assertThat(name).doesNotMatch(SingaporeFormats.PERSON_NAME);
	}

	@Test
	void collapsesSpacesInsideNames() {
		assertThat(SingaporeFormats.normalizeName("  Tan   Ah  Kow ")).isEqualTo("Tan Ah Kow");
		assertThat(SingaporeFormats.normalizeName(" ")).isNull();
	}

	@Test
	void writesDialectsInTheirListedSpellingOnce() {
		assertThat(SingaporeFormats.normalizeDialects(" hokkien , MANDARIN,hokkien,")).isEqualTo("Hokkien,Mandarin");
		assertThat(SingaporeFormats.normalizeDialects(" , ")).isNull();
		assertThat(SingaporeFormats.normalizeDialects("Hokkien,Mandarin")).matches(SingaporeFormats.DIALECT_LIST);
	}

	@Test
	void keepsAnUnlistedDialectSoTheListPatternRejectsIt() {
		String saved = SingaporeFormats.normalizeDialects("Hokkien, Klingon");
		assertThat(saved).isEqualTo("Hokkien,Klingon");
		assertThat(saved).doesNotMatch(SingaporeFormats.DIALECT_LIST);
	}

	@Test
	void theListPatternAcceptsEveryListedDialect() {
		SingaporeFormats.DIALECTS.forEach(dialect -> assertThat(dialect).matches(SingaporeFormats.DIALECT_LIST));
		assertThat(String.join(",", SingaporeFormats.DIALECTS)).matches(SingaporeFormats.DIALECT_LIST);
	}

	record Born(@OnOrAfter("1900-01-01") LocalDate dateOfBirth) {
	}

	@Test
	void onOrAfterRejectsEarlierDatesAndLetsNullThrough() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		assertThat(validator.validate(new Born(LocalDate.of(1899, 12, 31)))).hasSize(1);
		assertThat(validator.validate(new Born(LocalDate.of(1900, 1, 1)))).isEmpty();
		assertThat(validator.validate(new Born(null))).isEmpty();
	}
}
