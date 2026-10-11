package sg.nus.carelink.testsupport;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Empties the core stand-in before each test. Tests share an application context, and with it the
 * stand-in, so without this one test's incidents would answer the next one's questions. Registered
 * in META-INF/spring.factories, so it runs for every Spring test in visit, before the test's own
 * set-up.
 */
public class CoreStandInReset extends AbstractTestExecutionListener {

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void beforeTestMethod(TestContext testContext) {
		if (testContext.hasApplicationContext()) {
			testContext.getApplicationContext().getBeanProvider(CoreStandIn.class).ifAvailable(CoreStandIn::clear);
		}
	}

}
