package sg.nus.carelink.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Turns the conventions in ARCHITECTURE.md into build-time constraints. Code that breaks
 * the layering turns the pull request red in the fast stage; it does not rely on a
 * reviewer noticing.
 *
 * <p>This implements the non-functional requirement stated in the proposal: the build
 * fails if the domain layer references the presentation layer or a persistence
 * implementation.
 */
@AnalyzeClasses(
		packages = "sg.nus.carelink",
		importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

	@ArchTest
	static final ArchRule domainMustNotDependOnControllerOrInfrastructure =
			noClasses().that().resideInAPackage("..domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..controller..", "..infrastructure..")
					.because("dependencies point inwards; the domain layer must be testable "
							+ "without the web layer or a database")
					.allowEmptyShould(true);

	@ArchTest
	static final ArchRule controllerMustNotDependOnInfrastructure =
			noClasses().that().resideInAPackage("..controller..")
					.should().dependOnClassesThat()
					.resideInAPackage("..infrastructure..")
					.because("controllers enter through the application layer and never reach "
							+ "a repository implementation directly")
					.allowEmptyShould(true);

	@ArchTest
	static final ArchRule domainMustNotDependOnPersistenceFrameworks =
			noClasses().that().resideInAPackage("..domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
					.because("JPA annotations belong to infrastructure; the domain model and the "
							+ "JPA entity are kept apart")
					.allowEmptyShould(true);

	@ArchTest
	static final ArchRule modulesMustNotFormCycles =
			SlicesRuleDefinition.slices()
					.matching("sg.nus.carelink.(*)..")
					.should().beFreeOfCycles()
					.allowEmptyShould(true);
}
