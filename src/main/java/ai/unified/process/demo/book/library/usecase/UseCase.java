package ai.unified.process.demo.book.library.usecase;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a test method back to the use case specification it verifies, so the AI Unified
 * Process IntelliJ Navigator plugin can wire up gutter icons and Find Usages between the
 * Markdown spec in {@code docs/use_cases/} and the Java tests.
 *
 * <p>
 * The values must match headings in the corresponding {@code UC-XXX-*.md} file:
 * {@code id} the {@code Use Case ID}, {@code scenario} either the main success scenario
 * or an alternative flow heading, and {@code businessRules} the {@code BR-XXX} headings.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseCase {

	String id();

	String scenario() default "Main Success Scenario";

	String[] businessRules() default {};

}
