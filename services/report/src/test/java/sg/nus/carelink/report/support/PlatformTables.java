package sg.nus.carelink.report.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import sg.nus.carelink.testsupport.PlatformTablesForTests;

/**
 * The platform's tables after core's migrations, in every report test that starts the application:
 * the outbox report's messages are written to, and the scheduler locks. Component scanning finds this
 * class; slice tests leave it out, as they leave out every configuration class of the application's own.
 */
@Configuration(proxyBeanMethods = false)
@Import(PlatformTablesForTests.class)
class PlatformTables {
}
