package ai.unified.process.demo.book.library.catalog.domain;

import org.jspecify.annotations.Nullable;

/**
 * A catalog row as UC-001 Search Catalog shows it: the book's details together with how
 * many of its copies can be borrowed right now.
 *
 * @param isbn the ISBN, or {@code null} for a book recorded without one (UC-001 BR-005)
 * @param copies the total number of copies the library holds
 * @param availableCopies copies minus the book's open loans, derived by the query and
 * never stored (UC-001 BR-002)
 */
public record Book(Long id, String title, String author, @Nullable String isbn, Integer copies,
		Integer availableCopies) {
}
