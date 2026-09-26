package ai.unified.process.demo.book.library.catalog.ui;

import ai.unified.process.demo.book.library.core.ui.PlaywrightIT;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import in.virit.mopo.GridPw;
import in.virit.mopo.Mopo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser integration test for UC-001 "Search Catalog".
 *
 * <p>
 * Black box: it drives the running application through the browser and asserts only what
 * a member can see. The catalog it reads is the one {@code DemoDataSeed} puts in place
 * for every Spring test context.
 *
 * <p>
 * Nothing here creates data, so there is nothing to clean up.
 */
@DisplayName("UC-001: Search Catalog")
class UC001SearchCatalogIT extends PlaywrightIT {

	private static final int TITLE = 0;

	private static final int AUTHOR = 1;

	private static final int ISBN = 2;

	private static final int AVAILABLE = 3;

	/**
	 * Seeded demo accounts use the username as the password — see {@code SecuritySeed}
	 * for the accounts and {@code DemoDataSeed} for the catalog they own.
	 */
	private static final String MEMBER = "alice";

	private static final String LIBRARIAN = "librarian";

	@Nested
	@DisplayName("Listing the catalog")
	class Listing {

		@Test
		@DisplayName("lists every book, titles ordered by the database collation (BR-006, BR-007)")
		void lists_every_book_sorted_by_title() {
			var grid = openCatalogAs(MEMBER);

			assertThat(grid.getRenderedRowCount()).isEqualTo(9);
			// BR-006: the collation disregards case, spaces and punctuation, so
			// "An Beal Bocht" precedes "A Wizard of Earthsea".
			PlaywrightAssertions.assertThat(grid.getRow(0).getCell(TITLE)).hasText("An Beal Bocht");
			PlaywrightAssertions.assertThat(grid.getRow(1).getCell(TITLE)).hasText("A Wizard of Earthsea");
			// BR-007: the article is part of the title, so The Hobbit sorts under T.
			PlaywrightAssertions.assertThat(grid.getRow(5).getCell(TITLE)).hasText("The Hobbit");
		}

		@Test
		@DisplayName("shows title, author, ISBN and availability for a book (BR-003)")
		void shows_all_four_columns() {
			var grid = searchAs(MEMBER, "hobbit");

			var row = grid.getRow(0);
			PlaywrightAssertions.assertThat(row.getCell(TITLE)).hasText("The Hobbit");
			PlaywrightAssertions.assertThat(row.getCell(AUTHOR)).hasText("J.R.R. Tolkien");
			PlaywrightAssertions.assertThat(row.getCell(ISBN)).hasText("9780261102217");
			// BR-003: available out of total, so "one of three left" is distinguishable.
			PlaywrightAssertions.assertThat(row.getCell(AVAILABLE)).hasText("1 of 3");
		}

		@Test
		@DisplayName("shows an empty ISBN cell for a book recorded without one (BR-005)")
		void book_without_isbn_shows_an_empty_cell() {
			var grid = searchAs(MEMBER, "dubliners");

			var row = grid.getRow(0);
			PlaywrightAssertions.assertThat(row.getCell(TITLE)).hasText("Dubliners");
			// Empty, never a placeholder — and the row is listed all the same.
			PlaywrightAssertions.assertThat(row.getCell(ISBN)).hasText("");
		}

	}

	@Nested
	@DisplayName("Searching")
	class Searching {

		@Test
		@DisplayName("matches part of an author, ignoring case (BR-001)")
		void partial_author_match_ignores_case() {
			var grid = searchAs(MEMBER, "TOLK");

			assertThat(grid.getRenderedRowCount()).isEqualTo(2);
			PlaywrightAssertions.assertThat(grid.getRow(0).getCell(TITLE)).hasText("The Hobbit");
			PlaywrightAssertions.assertThat(grid.getRow(1).getCell(TITLE)).hasText("The Silmarillion");
		}

		@Test
		@DisplayName("matches part of a title, ignoring case (BR-001)")
		void partial_title_match_ignores_case() {
			var grid = searchAs(MEMBER, "EARTHSEA");

			assertThat(grid.getRenderedRowCount()).isEqualTo(1);
			PlaywrightAssertions.assertThat(grid.getRow(0).getCell(TITLE)).hasText("A Wizard of Earthsea");
		}

		@Test
		@DisplayName("does not search the ISBN, though it is displayed (BR-001)")
		void isbn_is_displayed_but_not_searched() {
			var grid = searchAs(MEMBER, "9780261102217");

			assertThat(grid.getRenderedRowCount()).isZero();
		}

		@Test
		@DisplayName("A1: reports that nothing matches the search term")
		void unmatched_term_reports_no_match() {
			var grid = searchAs(MEMBER, "qqqzzz");

			assertThat(grid.getRenderedRowCount()).isZero();
			PlaywrightAssertions.assertThat(page.getByText("No book matches \"qqqzzz\".")).isVisible();
		}

		@Test
		@DisplayName("A4: clearing the term brings the whole catalog back")
		void clearing_the_term_restores_the_catalog() {
			var grid = searchAs(MEMBER, "tolk");
			assertThat(grid.getRenderedRowCount()).isEqualTo(2);

			search("");

			assertThat(new GridPw(page).getRenderedRowCount()).isEqualTo(9);
		}

	}

	@Nested
	@DisplayName("Access")
	class Access {

		@Test
		@DisplayName("a librarian reaches the same catalog as a member (BR-004)")
		void librarian_can_search_the_catalog() {
			var grid = openCatalogAs(LIBRARIAN);

			assertThat(grid.getRenderedRowCount()).isEqualTo(9);
		}

	}

	@Nested
	@DisplayName("A2: every copy on loan")
	class EveryCopyOnLoan {

		@Test
		@DisplayName("shows none available out of the total held")
		void shows_none_available() {
			var grid = searchAs(MEMBER, "left hand");

			PlaywrightAssertions.assertThat(grid.getRow(0).getCell(AVAILABLE)).hasText("0 of 1");
		}

	}

	// ----------------------------------------------------------------- helpers

	private GridPw openCatalogAs(String username) {
		page.navigate(url("catalog"));
		logIn(username);
		var grid = new GridPw(page);
		// The grid is the view's first painted content, so waiting on it means the
		// navigation after login has completed.
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();
		return grid;
	}

	private GridPw searchAs(String username, String term) {
		openCatalogAs(username);
		search(term);
		return new GridPw(page);
	}

	private void search(String term) {
		var field = page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Search by title or author"));
		field.fill(term);
		// The field updates lazily, so let the round trip finish before asserting.
		Mopo.waitForConnectionToSettle(page);
	}

	private void logIn(String username) {
		// By role, not by label: the password field's label also matches its
		// "Show password" reveal button, which makes a by-label lookup ambiguous.
		page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")).fill(username);
		// Seeded demo accounts use the username as their password (SecuritySeed).
		page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill(username);
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
		Mopo.waitForConnectionToSettle(page);
	}

	private String url(String route) {
		return "http://localhost:" + localServerPort + "/" + route;
	}

}
