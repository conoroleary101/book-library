package ai.unified.process.demo.book.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Pinned to an explicit major version rather than {@code latest}. UC-001 BR-006 says
	 * titles are ordered by the database's default collation, and two tests assert the
	 * consequence — that "An Beal Bocht" precedes "A Wizard of Earthsea", because the
	 * collation disregards spaces. That assertion rests on the collation this image ships
	 * with, so a floating tag could break the tests without a single change to the
	 * project. Change this version deliberately, and re-run UC001SearchCatalogTest and
	 * UC001SearchCatalogIT when you do.
	 * <p>
	 * Keep this in step with the {@code db.image} property in {@code pom.xml}, which is
	 * the same image for the build-time container that Flyway and jOOQ code generation
	 * use. Generating code against one version and testing against another is a mismatch
	 * the build will not report.
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:18");
	}

}
