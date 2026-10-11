package sg.nus.carelink.report.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import sg.nus.carelink.testsupport.OwnDatabaseForTests;

/**
 * report's own database, in every report test that starts the application: built by its own migrations
 * and then the platform's, beside core's tables for the core stand-in (OwnDatabaseForTests), as in a
 * real run. Component scanning finds this class; slice tests leave it out, as they leave out every
 * configuration class of the application's own.
 */
@Configuration(proxyBeanMethods = false)
@Import(OwnDatabaseForTests.class)
class PlatformTables {
}
