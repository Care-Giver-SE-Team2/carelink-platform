package sg.nus.carelink.testsupport;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Empties the visit stand-in before each test. Tests share an application context, and with it the
 * stand-in, so without this one test's visits would turn up in the next one's roster. Registered in
 * META-INF/spring.factories, so it runs for every Spring test in core; it runs before the test's own
 * set-up, which lays out the visits it needs.
 */
public class VisitStandInReset extends AbstractTestExecutionListener {

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void beforeTestMethod(TestContext testContext) {
		if (testContext.hasApplicationContext()) {
			testContext.getApplicationContext().getBeanProvider(VisitStandIn.class).ifAvailable(VisitStandIn::clear);
		}
	}

}
