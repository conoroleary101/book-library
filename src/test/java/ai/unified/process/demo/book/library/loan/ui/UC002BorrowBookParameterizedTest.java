package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.core.ui.AbstractBrowserlessTest;
import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.NoAvailableCopyException;
import ai.unified.process.demo.book.library.usecase.UseCase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static ai.unified.process.demo.book.library.db.Tables.APP_USER;
import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;
import static ai.unified.process.demo.book.library.db.Tables.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parameterised property tests for UC-002 Borrow Book.
 *
 * <p>
 * Validates the two core availability properties across multiple input combinations:
 * <ol>
 * <li>Borrow succeeds exactly when a copy is free (Property 1 from design.md)</li>
 * <li>Borrow is rejected when no copy is available (Property 2 from design.md)</li>
 * </ol>
 * Property 3 (availability never negative under concurrency) is covered by
 * {@code UC002BorrowBookTest#concurrent_borrow_creates_exactly_one_loan}.
 *
 * <p>
 * Test data isolation: every test inserts books with a {@code UUID} prefix in the title
 * and deletes those rows (loans first, then books) in {@link #cleanup()}.
 */
@WithUserDetails("alice")
class UC002BorrowBookParameterizedTest extends AbstractBrowserlessTest {

	@Autowired
	private LoanService loanService;

	@Autowired
	private DSLContext dsl;

	/**
	 * Tracks all book IDs created by test methods so {@link #cleanup()} can remove them.
	 */
	private final Set<Long> testBookIds = new HashSet<>();

	// -------------------------------------------------------------------------
	// Teardown
	// -------------------------------------------------------------------------

	@AfterEach
	void cleanup() {
		if (testBookIds.isEmpty()) {
			return;
		}
		// Delete loans first (FK on loan.book_id), then books.
		dsl.deleteFrom(LOAN).where(LOAN.BOOK_ID.in(testBookIds)).execute();
		dsl.deleteFrom(BOOK).where(BOOK.ID.in(testBookIds)).execute();
		testBookIds.clear();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Inserts a test book with the given copy count and a UUID-prefixed title. Registers
	 * the new ID in {@link #testBookIds} for cleanup.
	 */
	private long insertTestBook(String uuidPrefix, int copies) {
		Long id = dsl.insertInto(BOOK, BOOK.TITLE, BOOK.AUTHOR, BOOK.COPIES)
			.values(uuidPrefix + " Test Book", "Test Author", copies)
			.returningResult(BOOK.ID)
			.fetchOne(BOOK.ID);
		assertThat(id).isNotNull();
		testBookIds.add(id);
		return id;
	}

	/** Returns alice's {@code member.id} by joining through {@code app_user}. */
	private long aliceMemberId() {
		Long id = dsl.select(MEMBER.ID)
			.from(MEMBER)
			.join(APP_USER)
			.on(MEMBER.USER_ID.eq(APP_USER.ID))
			.where(APP_USER.USERNAME.eq("alice"))
			.fetchOne(MEMBER.ID);
		assertThat(id).as("alice must have a member row").isNotNull();
		return id;
	}

	/** Inserts an open loan for the given member and book. */
	private void insertOpenLoan(long memberId, long bookId) {
		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID).values(memberId, bookId).execute();
	}

	/** Counts open loans for a book. */
	private int countOpenLoans(long bookId) {
		return dsl.fetchCount(LOAN, LOAN.BOOK_ID.eq(bookId).and(LOAN.RETURNED_AT.isNull()));
	}

	// -------------------------------------------------------------------------
	// Property 1 — Borrow succeeds exactly when a copy is free
	// -------------------------------------------------------------------------

	/**
	 * Validates design Property 1: for any book with {@code copies = N} and exactly
	 * {@code M} open loans where {@code M < N}, a borrow request from a member with a
	 * valid member row SHALL succeed and increase the open-loan count to {@code M + 1}.
	 *
	 * <p>
	 * Validates: Requirements 1.1, 1.2
	 */
	@ParameterizedTest
	@CsvSource({ "1,0", "2,1", "5,3", "10,9" })
	@UseCase(id = "UC-002", scenario = "Main Success Scenario", businessRules = { "BR-001", "BR-007", "BR-008" })
	void borrow_succeeds_when_copy_is_available(int copies, int existingLoans) {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, copies);
		long memberId = aliceMemberId();

		// Pre-populate with the specified number of open loans.
		for (int i = 0; i < existingLoans; i++) {
			insertOpenLoan(memberId, bookId);
		}

		// Borrow must succeed without throwing.
		loanService.borrow(bookId);

		// Open-loan count must have increased by exactly one.
		assertThat(countOpenLoans(bookId)).as("open-loan count after borrow").isEqualTo(existingLoans + 1);
	}

	// -------------------------------------------------------------------------
	// Property 2 — Borrow is rejected when no copy is available
	// -------------------------------------------------------------------------

	/**
	 * Validates design Property 2: for any book where the open-loan count equals
	 * {@code book.copies}, a borrow request SHALL be rejected with
	 * {@link NoAvailableCopyException} and the open-loan count SHALL remain unchanged.
	 *
	 * <p>
	 * Validates: Requirements 1.5
	 */
	@ParameterizedTest
	@CsvSource({ "1", "2", "5", "10" })
	@UseCase(id = "UC-002", scenario = "A1: No Copy Is Available at Borrow Time", businessRules = { "BR-001" })
	void borrow_rejected_when_fully_on_loan(int copies) {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, copies);
		long memberId = aliceMemberId();

		// Fill all copies with open loans.
		for (int i = 0; i < copies; i++) {
			insertOpenLoan(memberId, bookId);
		}

		// Borrow must be rejected.
		assertThatThrownBy(() -> loanService.borrow(bookId)).isInstanceOf(NoAvailableCopyException.class);

		// Open-loan count must be unchanged.
		assertThat(countOpenLoans(bookId)).as("open-loan count must be unchanged after rejection").isEqualTo(copies);
	}

}
