package sg.nus.carelink.coreapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** A service that says where core is gets a client; core, which says nothing, gets none. */
class CoreApiAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(CoreApiAutoConfiguration.class));

	@Test
	void aServiceThatSaysWhereCoreIsGetsAClient() {
		runner.withPropertyValues("carelink.core-api.base-url=http://core").run(context -> {
			assertThat(context).hasSingleBean(CoreApi.class);
			assertThat(context.getBean(CoreApiProperties.class).readTimeout()).hasSeconds(5);
		});
	}

	@Test
	void anApplicationThatSaysNothingGetsNone() {
		runner.run(context -> assertThat(context).doesNotHaveBean(CoreApi.class));
	}

}
