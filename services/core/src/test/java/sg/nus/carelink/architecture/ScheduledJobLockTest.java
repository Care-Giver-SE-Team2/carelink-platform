package sg.nus.carelink.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * core runs on more than one replica. A scheduled job without a lock runs once on every replica:
 * escalations go out twice, reminders are sent twice. This rule fails the build as soon as a new
 * scheduled job in core arrives without a {@code @SchedulerLock}.
 *
 * <p>visit, report and notification are about to leave core; each brings its own answer for its
 * jobs when it does, so they are left out here.
 */
@AnalyzeClasses(
		packages = "sg.nus.carelink",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ScheduledJobLockTest {

	@ArchTest
	static final ArchRule scheduledJobsInCoreTakeALock =
			methods().that().areAnnotatedWith(Scheduled.class)
					.and().areDeclaredInClassesThat()
					.resideOutsideOfPackages("..visit..", "..report..", "..notification..")
					.should().beAnnotatedWith(SchedulerLock.class)
					.because("core runs on several replicas, and a job without a lock runs on each of them")
					.allowEmptyShould(true);

}
