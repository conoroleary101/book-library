package ai.unified.process.demo.book.library;

import ai.unified.process.demo.book.library.usecase.UseCase;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the {@code @UseCase} annotations honest against the specifications in
 * {@code docs/use_cases}.
 *
 * <p>
 * The annotations are the only machine-readable link between a specification and the
 * tests that verify it, and nothing else checks them: a renamed alternative flow, a
 * renumbered business rule, or a constraint written where a rule belongs all leave the
 * annotation compiling and the test passing while the trace silently points at nothing.
 * Business rule identifiers are scoped per use case, so the same {@code BR-001} means
 * different things in different specifications — which makes a stale identifier worse
 * than a missing one.
 *
 * <p>
 * This test fails when an annotation names a use case with no specification file, a
 * scenario that is not a heading in that file, or a business rule that file does not
 * declare. It also rejects anything in {@code businessRules} that is not a business rule
 * at all — a constraint, a functional requirement, or any other identifier.
 *
 * <p>
 * <strong>Known limit.</strong> A bare rule id that means another use case's rule cannot
 * be detected when the same id also exists locally: {@code "BR-008"} written in a UC-002
 * test but meaning UC-001's rule resolves cleanly against UC-002's own BR-008, and
 * nothing distinguishes it from a correct local reference. That ambiguity is exactly why
 * a cross-use-case reference must be qualified as {@code "UC-001 BR-008"} — the
 * convention is the protection there, not this check. An unqualified id that does
 * <em>not</em> exist locally is caught.
 */
class UseCaseTraceabilityTest {

	private static final Path SPEC_DIR = Path.of("docs", "use_cases");

	/**
	 * The main success scenario is a {@code ##} heading, and is the annotation default.
	 */
	private static final String MAIN_SCENARIO = "Main Success Scenario";

	private static final Pattern SPEC_FILE = Pattern.compile("^(UC-\\d{3})-.+\\.md$");

	private static final Pattern USE_CASE_ID = Pattern.compile("UC-\\d{3}");

	private static final Pattern RULE_ID = Pattern.compile("BR-\\d{3}");

	/**
	 * A rule of another use case, qualified with its use case id — e.g. "UC-001 BR-008".
	 */
	private static final Pattern QUALIFIED_RULE_ID = Pattern.compile("(UC-\\d{3}) (BR-\\d{3})");

	private static final Pattern SCENARIO_HEADING = Pattern.compile("^### (A\\d+: .+?)\\s*$", Pattern.MULTILINE);

	private static final Pattern RULE_HEADING = Pattern.compile("^### (BR-\\d{3}):", Pattern.MULTILINE);

	private final JavaClasses classes = new ClassFileImporter().importPackages(ArchitectureTest.PACKAGE_ROOT);

	@Test
	void every_use_case_annotation_resolves_to_its_specification() {
		Map<String, Spec> specs = loadSpecs();
		assertThat(specs).as("specifications parsed from %s", SPEC_DIR).isNotEmpty();

		List<JavaMethod> annotated = annotatedMethods();
		// A traceability check that finds no annotations proves nothing and would stay
		// green for ever, so the scan itself is asserted before its results are.
		assertThat(annotated).as("methods annotated with @UseCase").isNotEmpty();

		List<String> problems = new ArrayList<>();
		for (JavaMethod method : annotated) {
			check(method, specs, problems);
		}

		assertThat(problems).as("unresolved @UseCase references:%n%s", String.join("\n", problems)).isEmpty();
	}

	private void check(JavaMethod method, Map<String, Spec> specs, List<String> problems) {
		UseCase useCase = method.getAnnotationOfType(UseCase.class);
		String where = method.getOwner().getSimpleName() + "." + method.getName() + "()";

		if (!USE_CASE_ID.matcher(useCase.id()).matches()) {
			problems.add("  %s: id \"%s\" is not of the form UC-XXX".formatted(where, useCase.id()));
			return;
		}

		Spec spec = specs.get(useCase.id());
		if (spec == null) {
			problems.add("  %s: no specification file for %s in %s".formatted(where, useCase.id(), SPEC_DIR));
			return;
		}

		if (!spec.scenarios().contains(useCase.scenario())) {
			problems.add("  %s: scenario \"%s\" is not a heading in %s - expected one of %s".formatted(where,
					useCase.scenario(), spec.file().getFileName(), spec.scenarios()));
		}

		for (String rule : useCase.businessRules()) {
			checkRule(rule, where, spec, specs, problems);
		}
	}

	private void checkRule(String rule, String where, Spec spec, Map<String, Spec> specs, List<String> problems) {
		Matcher qualified = QUALIFIED_RULE_ID.matcher(rule);
		if (qualified.matches()) {
			String otherId = qualified.group(1);
			String otherRule = qualified.group(2);
			Spec other = specs.get(otherId);
			if (other == null) {
				problems
					.add("  %s: \"%s\" refers to %s, which has no specification file".formatted(where, rule, otherId));
			}
			else if (!other.rules().contains(otherRule)) {
				problems.add("  %s: \"%s\" is not a rule in %s - it declares %s".formatted(where, rule,
						other.file().getFileName(), other.rules()));
			}
			return;
		}

		if (RULE_ID.matcher(rule).matches()) {
			if (!spec.rules().contains(rule)) {
				problems.add("  %s: \"%s\" is not a rule in %s - it declares %s".formatted(where, rule,
						spec.file().getFileName(), spec.rules()));
			}
			return;
		}

		// Anything else: a constraint (C-XXX), a functional or non-functional requirement
		// (FR-XXX, NFR-XXX), or a bare rule id belonging to another use case.
		problems.add(("  %s: businessRules contains \"%s\", which is not a business rule. "
				+ "Constraints and requirements are cited inside the rule text in the specification - "
				+ "reference the BR that cites them. A rule of another use case must be qualified, "
				+ "e.g. \"UC-001 BR-008\".")
			.formatted(where, rule));
	}

	private List<JavaMethod> annotatedMethods() {
		List<JavaMethod> annotated = new ArrayList<>();
		for (var javaClass : this.classes) {
			for (JavaMethod method : javaClass.getMethods()) {
				if (method.isAnnotatedWith(UseCase.class)) {
					annotated.add(method);
				}
			}
		}
		return annotated;
	}

	private static Map<String, Spec> loadSpecs() {
		assertThat(SPEC_DIR).as("specification directory — tests must run from the project root").isDirectory();

		Map<String, Spec> specs = new LinkedHashMap<>();
		try (Stream<Path> files = Files.list(SPEC_DIR)) {
			for (Path file : files.sorted().toList()) {
				Matcher name = SPEC_FILE.matcher(file.getFileName().toString());
				if (name.matches()) {
					specs.put(name.group(1), parse(name.group(1), file));
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + SPEC_DIR, ex);
		}
		return specs;
	}

	private static Spec parse(String id, Path file) {
		String markdown;
		try {
			markdown = Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + file, ex);
		}

		Set<String> scenarios = new TreeSet<>();
		scenarios.add(MAIN_SCENARIO);
		Matcher scenario = SCENARIO_HEADING.matcher(markdown);
		while (scenario.find()) {
			scenarios.add(scenario.group(1));
		}

		Set<String> rules = new TreeSet<>();
		Matcher rule = RULE_HEADING.matcher(markdown);
		while (rule.find()) {
			rules.add(rule.group(1));
		}

		return new Spec(id, file, scenarios, rules);
	}

	private record Spec(String id, Path file, Set<String> scenarios, Set<String> rules) {
	}

}
