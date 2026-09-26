package ai.unified.process.demo.book.library.catalog.ui;

import ai.unified.process.demo.book.library.catalog.domain.Book;
import ai.unified.process.demo.book.library.core.ui.AbstractBrowserlessTest;
import ai.unified.process.demo.book.library.usecase.UseCase;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case test for UC-001 "Search Catalog".
 *
 * <p>
 * The fixtures come from {@code DemoDataSeed}, which runs after {@code SecuritySeed} in
 * every Spring test context and is built for exactly these assertions: nine books
 * inserted out of alphabetical order, two without an ISBN, repeated authors, and one
 * title whose only copy is on loan.
 *
 * <p>
 * Every test reads the catalog and changes nothing, so there is no data to clean up.
 */
@WithMockUser(username = "alice", roles = "MEMBER")
class UC001SearchCatalogTest extends AbstractBrowserlessTest {

	private static final int TITLE = 0;

	private static final int AUTHOR = 1;

	private static final int ISBN = 2;

	private static final int AVAILABLE = 3;

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-006", "BR-007" })
	void catalog_lists_every_book_sorted_by_title() {
		navigate(CatalogView.class);

		var grid = catalogGrid();
		assertThat(test(grid).size()).isEqualTo(9);

		// BR-006: the collation compares without case and disregards spaces, so
		// "An Beal Bocht" ("anbealbocht") precedes "A Wizard of Earthsea"
		// ("awizardofearthsea"). BR-007: "The Hobbit" sorts under T, article included.
		assertThat(titlesInGridOrder()).containsExactly("An Beal Bocht", "A Wizard of Earthsea", "Beloved",
				"Cloud Atlas", "Dubliners", "The Hobbit", "The Left Hand of Darkness", "The Silmarillion",
				"Zorba the Greek");
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-003" })
	void every_row_shows_title_author_isbn_and_availability() {
		navigate(CatalogView.class);

		var grid = catalogGrid();
		int hobbit = rowOf("The Hobbit");

		assertThat(test(grid).getCellText(hobbit, TITLE)).isEqualTo("The Hobbit");
		assertThat(test(grid).getCellText(hobbit, AUTHOR)).isEqualTo("J.R.R. Tolkien");
		assertThat(test(grid).getCellText(hobbit, ISBN)).isEqualTo("9780261102217");
		// BR-003: availability reads as available out of total, not as a yes/no flag.
		assertThat(test(grid).getCellText(hobbit, AVAILABLE)).isEqualTo("1 of 3");
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-001" })
	void searching_by_partial_author_ignores_case() {
		navigate(CatalogView.class);

		searchFor("tolk");

		// BR-001: case-insensitive partial match, so "tolk" finds "Tolkien" twice.
		assertThat(titlesInGridOrder()).containsExactly("The Hobbit", "The Silmarillion");
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-001" })
	void searching_by_partial_title_ignores_case() {
		navigate(CatalogView.class);

		searchFor("HOBB");

		assertThat(titlesInGridOrder()).containsExactly("The Hobbit");
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-001" })
	void isbn_is_displayed_but_not_searched() {
		navigate(CatalogView.class);

		searchFor("9780261102217");

		// The ISBN is shown in the results but takes no part in the search (C-020).
		assertThat(test(catalogGrid()).size()).isZero();
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-002", "BR-003" })
	void availability_counts_only_open_loans() {
		navigate(CatalogView.class);

		var grid = catalogGrid();
		// The Hobbit holds three copies with two open loans.
		assertThat(test(grid).getCellText(rowOf("The Hobbit"), AVAILABLE)).isEqualTo("1 of 3");
		// Beloved holds two copies and its only loan was returned, so nothing is
		// withheld.
		assertThat(test(grid).getCellText(rowOf("Beloved"), AVAILABLE)).isEqualTo("2 of 2");
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-005" })
	void book_without_isbn_shows_an_empty_cell_and_is_still_listed() {
		navigate(CatalogView.class);

		var grid = catalogGrid();
		int dubliners = rowOf("Dubliners");

		// BR-005: an empty cell, never a placeholder, and the row is still present.
		assertThat(test(grid).getCellText(dubliners, ISBN)).isEmpty();
		assertThat(test(grid).getCellText(dubliners, TITLE)).isEqualTo("Dubliners");
	}

	@Test
	@UseCase(id = "UC-001", businessRules = { "BR-005" })
	void book_without_isbn_can_still_be_found_by_title() {
		navigate(CatalogView.class);

		searchFor("dubliners");

		assertThat(titlesInGridOrder()).containsExactly("Dubliners");
	}

	@Test
	@UseCase(id = "UC-001", scenario = "A1: No Book Matches the Search Term")
	void unmatched_search_term_empties_the_grid_and_explains_why() {
		navigate(CatalogView.class);

		searchFor("qqqzzz");

		var grid = catalogGrid();
		assertThat(test(grid).size()).isZero();
		assertThat(grid.getEmptyStateText()).isEqualTo("No book matches \"qqqzzz\".");
	}

	@Test
	@UseCase(id = "UC-001", scenario = "A2: Every Copy Is on Loan", businessRules = { "BR-003" })
	void book_with_every_copy_on_loan_shows_none_available() {
		navigate(CatalogView.class);

		var grid = catalogGrid();
		int onLoan = rowOf("The Left Hand of Darkness");

		// Its single copy is out, so the member can see it cannot be borrowed now.
		assertThat(test(grid).getCellText(onLoan, AVAILABLE)).isEqualTo("0 of 1");
	}

	@Test
	@UseCase(id = "UC-001", scenario = "A4: Member Clears the Search Term")
	void clearing_the_search_term_restores_the_whole_catalog() {
		navigate(CatalogView.class);

		searchFor("tolk");
		assertThat(test(catalogGrid()).size()).isEqualTo(2);

		searchFor("");

		assertThat(test(catalogGrid()).size()).isEqualTo(9);
		assertThat(titlesInGridOrder()).startsWith("An Beal Bocht", "A Wizard of Earthsea");
	}

	@Test
	@WithMockUser(username = "librarian", roles = "LIBRARIAN")
	@UseCase(id = "UC-001", businessRules = { "BR-004" })
	void librarian_can_search_the_catalog_too() {
		navigate(CatalogView.class);

		var grid = catalogGrid();
		assertThat(test(grid).size()).isEqualTo(9);

		searchFor("tolk");

		assertThat(test(catalogGrid()).size()).isEqualTo(2);
	}

	private void searchFor(String term) {
		test(find(TextField.class).single()).setValue(term);
	}

	@SuppressWarnings("unchecked")
	private Grid<Book> catalogGrid() {
		return find(Grid.class).single();
	}

	private String[] titlesInGridOrder() {
		var grid = catalogGrid();
		int size = test(grid).size();
		var titles = new String[size];
		for (int row = 0; row < size; row++) {
			titles[row] = test(grid).getCellText(row, TITLE);
		}
		return titles;
	}

	private int rowOf(String title) {
		var titles = titlesInGridOrder();
		for (int row = 0; row < titles.length; row++) {
			if (titles[row].equals(title)) {
				return row;
			}
		}
		throw new AssertionError("No row for title: " + title);
	}

}
