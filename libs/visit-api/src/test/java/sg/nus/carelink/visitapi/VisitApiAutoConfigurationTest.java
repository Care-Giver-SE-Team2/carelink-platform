package sg.nus.carelink.visitapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** A caller that says where visit is gets a client; whoever serves the API, and says nothing, gets none. */
class VisitApiAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(VisitApiAutoConfiguration.class));

	@Test
	void aCallerThatSaysWhereVisitIsGetsAClient() {
		runner.withPropertyValues("carelink.visit-api.base-url=http://visit").run(context -> {
			assertThat(context).hasSingleBean(VisitApi.class);
			assertThat(context.getBean(VisitApiProperties.class).readTimeout()).hasSeconds(5);
		});
	}

	@Test
	void anApplicationThatSaysNothingGetsNone() {
		runner.run(context -> assertThat(context).doesNotHaveBean(VisitApi.class));
	}

}
