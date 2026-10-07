package sg.nus.carelink.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.domain.service.UsernameRule;

class UsernameRuleTest {

	@ParameterizedTest
	@CsvSource({
			"Tan Bee Choo, tan.bee.choo",
			"Mohd Yusof bin Ali, mohd.yusof.bin.ali",
			"'  Lim   Soo-Hwa ', lim.soo.hwa",
			"Zoë O'Neil, zoe.o.neil",
	})
	void formsLowerCaseWordsJoinedByDots(String name, String expected) {
		assertThat(UsernameRule.base(name)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void aNameWithNoLettersFallsBackToUser(String name) {
		assertThat(UsernameRule.base(name)).isEqualTo("user");
		assertThat(UsernameRule.base("李明")).isEqualTo("user");
	}

	@Test
	void aLongNameIsCutWithoutATrailingDot() {
		String base = UsernameRule.base("Abcdefghij ".repeat(8));
		assertThat(base).hasSizeLessThanOrEqualTo(50).doesNotEndWith(".");
	}

	@Test
	void laterAttemptsAppendANumber() {
		assertThat(UsernameRule.candidate("tan.bee.choo", 1)).isEqualTo("tan.bee.choo");
		assertThat(UsernameRule.candidate("tan.bee.choo", 2)).isEqualTo("tan.bee.choo2");
	}
}
