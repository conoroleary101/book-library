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
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:18");
	}

}
