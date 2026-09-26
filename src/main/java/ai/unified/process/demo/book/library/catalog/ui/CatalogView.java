package ai.unified.process.demo.book.library.catalog.ui;

import ai.unified.process.demo.book.library.catalog.domain.Book;
import ai.unified.process.demo.book.library.catalog.domain.BookRepository;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Objects;

/**
 * UC-001 Search Catalog.
 *
 * <p>
 * Lists the catalog sorted by title ascending and narrows it to the books whose title or
 * author matches the search term. Every row carries its availability as "available of
 * total", so the member can tell "none of three left" from "the library holds only one"
 * (BR-003).
 *
 * <p>
 * Open to any signed-in user, member or librarian (BR-004).
 */
@RolesAllowed({ "MEMBER", "LIBRARIAN" })
@Route("catalog")
@PageTitle("Search Catalog")
@Menu(title = "Catalog", order = 1, icon = "vaadin:book")
public class CatalogView extends VerticalLayout {

	private final transient BookRepository bookRepository;

	private final TextField searchField = new TextField();

	private final Grid<Book> grid = new Grid<>();

	public CatalogView(BookRepository bookRepository) {
		this.bookRepository = bookRepository;
		setSizeFull();
		configureSearchField();
		configureGrid();
		add(searchField, grid);
		refresh();
	}

	private void configureSearchField() {
		searchField.setPlaceholder("Search by title or author");
		searchField.setAriaLabel("Search by title or author");
		searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
		// The clear button is flow A4: emptying the term brings the whole catalog back.
		searchField.setClearButtonVisible(true);
		searchField.setValueChangeMode(ValueChangeMode.LAZY);
		searchField.setWidth("22em");
		searchField.addValueChangeListener(event -> refresh());
	}

	private void configureGrid() {
		grid.addColumn(Book::title).setHeader("Title").setAutoWidth(true).setFlexGrow(1);
		grid.addColumn(Book::author).setHeader("Author").setAutoWidth(true);
		// A book recorded without an ISBN shows an empty cell, never a placeholder, and
		// is
		// never kept out of a result it otherwise matches (BR-005).
		grid.addColumn(book -> Objects.requireNonNullElse(book.isbn(), "")).setHeader("ISBN").setAutoWidth(true);
		grid.addComponentColumn(CatalogView::availability).setHeader("Available").setAutoWidth(true);
		grid.setSizeFull();
	}

	private void refresh() {
		String term = Objects.requireNonNullElse(searchField.getValue(), "").trim();
		List<Book> books = bookRepository.search(term);
		// The two empty results mean different things: nothing in the catalog at all (A3)
		// versus nothing matching what was typed (A1).
		grid.setEmptyStateText(
				term.isEmpty() ? "No books have been added to the catalog yet." : "No book matches \"" + term + "\".");
		grid.setItems(books);
	}

	/**
	 * Renders availability as "available of total" (BR-003), highlighting the case where
	 * every copy is on loan so the member can see the book cannot be borrowed now (A2).
	 */
	private static Span availability(Book book) {
		var span = new Span(book.availableCopies() + " of " + book.copies());
		span.addClassNames("app-availability");
		if (book.availableCopies() <= 0) {
			span.addClassNames("app-availability-none");
		}
		return span;
	}

}
