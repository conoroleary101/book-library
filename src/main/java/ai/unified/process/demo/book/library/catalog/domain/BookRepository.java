package ai.unified.process.demo.book.library.catalog.domain;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;

/**
 * Reads the catalog for UC-001 Search Catalog.
 *
 * <p>
 * Pure data access, so there is no service in front of it: the view talks to this
 * repository directly, as {@code docs/architecture.md} prescribes for a read-only query.
 */
@Repository
public class BookRepository {

	private final DSLContext dsl;

	public BookRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	/**
	 * Returns the catalog sorted by title ascending, narrowed to the books whose title or
	 * author contains {@code term}. A blank term returns the whole catalog, which is both
	 * the initial listing (step 2) and the result of clearing the search (flow A4).
	 */
	public List<Book> search(String term) {
		Field<Integer> openLoans = DSL.selectCount()
			.from(LOAN)
			.where(LOAN.BOOK_ID.eq(BOOK.ID).and(LOAN.RETURNED_AT.isNull()))
			.asField();

		return dsl.select(BOOK.ID, BOOK.TITLE, BOOK.AUTHOR, BOOK.ISBN, BOOK.COPIES, BOOK.COPIES.minus(openLoans))
			.from(BOOK)
			.where(matching(term))
			.orderBy(BOOK.TITLE.asc())
			.fetch(Records.mapping(Book::new));
	}

	/**
	 * Matches the term against title and author only, ignoring case and matching any part
	 * of the value, so "tolk" finds "Tolkien" (UC-001 BR-001). The ISBN is displayed but
	 * deliberately not searched (C-020).
	 */
	private Condition matching(String term) {
		if (term.isBlank()) {
			return DSL.noCondition();
		}
		return BOOK.TITLE.containsIgnoreCase(term).or(BOOK.AUTHOR.containsIgnoreCase(term));
	}

}
