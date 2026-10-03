package ai.unified.process.demo.book.library;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

	// Packages

	public static final String PACKAGE_ROOT = "ai.unified.process.demo.book.library";

	public static final String UI_PACKAGE = "..ui..";

	// Security configuration is the one place outside ..ui.. that may import com.vaadin..
	// (VaadinSecurityConfigurer, AuthenticationContext, LoginForm).
	public static final String SECURITY_PACKAGE = "..security..";

	// Layers

	private static final String UI_LAYER = "UI";

	// Modules

	private static final String CORE_MODULE = "..core..";

	private static final String GREETING_MODULE = "..greeting..";

	private final JavaClasses classes = new ClassFileImporter().importPackages(PACKAGE_ROOT);

	/**
	 * Production classes only. Tests legitimately delete the rows they create, so the
	 * retention rule below must not see them.
	 */
	private final JavaClasses mainClasses = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages(PACKAGE_ROOT);

	@Test
	void layered_architecture_check() {
		layeredArchitecture().consideringAllDependencies()

			.layer(UI_LAYER)
			.definedBy(UI_PACKAGE)

			.whereLayer(UI_LAYER)
			.mayNotBeAccessedByAnyLayer()

			.check(classes);
	}

	@Test
	void module_check_core_may_not_be_accessed_by_any_other_module() {
		noClasses().that()
			.resideInAPackage(CORE_MODULE)
			.should()
			.accessClassesThat()
			.resideInAnyPackage(GREETING_MODULE)
			.check(classes);
	}

	/**
	 * UC-002 BR-006 and UC-003 BR-001: a loan is never removed by any application action,
	 * and a closed loan is retained for at least 24 months (NFR-014). Both use cases
	 * state the rule - UC-002 for the loan that is created, UC-003 for the loan that is
	 * closed rather than deleted - and this test is the only thing enforcing either, so
	 * both are named here. Removing or renumbering one must not silently orphan the
	 * other. Until this rule existed the guarantee rested on there happening to be no
	 * delete in the code; now adding one breaks the build.
	 * <p>
	 * ArchUnit sees the method call, not the table passed to it, so this forbids every
	 * jOOQ delete and truncate in production code rather than only those against
	 * {@code LOAN}. That is deliberately wider than either rule needs: nothing in
	 * production deletes anything today, and a use case that genuinely must delete
	 * another table's rows should narrow this rule — by package, say — as part of that
	 * work, rather than remove it.
	 */
	@Test
	void production_code_never_deletes_rows_so_loans_are_retained() {
		DescribedPredicate<JavaMethodCall> jooqDelete = DescribedPredicate.describe("a jOOQ delete or truncate",
				call -> call.getTargetOwner().isAssignableTo(DSLContext.class)
						&& (call.getName().startsWith("delete") || call.getName().startsWith("truncate")));

		noClasses().should()
			.callMethodWhere(jooqDelete)
			.because("UC-002 BR-006 and UC-003 BR-001 require loans to be retained; "
					+ "no production code may delete rows")
			.check(this.mainClasses);
	}

	@Test
	void verify_that_only_the_ui_layer_is_using_vaadin() {
		noClasses().that()
			.resideOutsideOfPackages(UI_PACKAGE, SECURITY_PACKAGE)
			.should()
			.accessClassesThat()
			.resideInAnyPackage("com.vaadin..")
			.check(classes);
	}

}
