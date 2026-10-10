package sg.nus.carelink.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * report runs on two replicas. A scheduled job without a lock runs once on each of them: weekly
 * reports would be filed twice, a request settled twice. This rule fails the build as soon as a
 * scheduled job arrives without a {@code @SchedulerLock}.
 */
@AnalyzeClasses(
		packages = "sg.nus.carelink",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ScheduledJobLockTest {

	@ArchTest
	static final ArchRule scheduledJobsTakeALock =
			methods().that().areAnnotatedWith(Scheduled.class)
					.should().beAnnotatedWith(SchedulerLock.class)
					.because("report runs on several replicas, and a job without a lock runs on each of them")
					.allowEmptyShould(true);

}
