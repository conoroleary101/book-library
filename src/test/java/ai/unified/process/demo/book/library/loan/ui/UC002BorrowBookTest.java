package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.catalog.domain.Book;
import ai.unified.process.demo.book.library.catalog.ui.CatalogView;
import ai.unified.process.demo.book.library.core.ui.AbstractBrowserlessTest;
import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.NoAvailableCopyException;
import ai.unified.process.demo.book.library.loan.domain.NoMemberProfileException;
import ai.unified.process.demo.book.library.usecase.UseCase;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithUserDetails;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.unified.process.demo.book.library.db.Tables.APP_USER;
import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;
import static ai.unified.process.demo.book.library.db.Tables.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Browserless unit tests for UC-002 Borrow Book.
 *
 * <p>
 * Most tests sign in as {@code alice}, who has a linked {@code member} row (the happy
 * path). The A2 alternative flow (no member row) is exercised with
 * {@code @WithUserDetails("librarian")} at method level, because the librarian account is
 * seeded without a {@code member} row.
 *
 * <p>
 * Test data isolation: every test that writes to the database inserts books with a
 * {@code UUID} prefix in the title and deletes those rows (loans first, then books) in
 * {@link #cleanup()}.
 */
@WithUserDetails("alice")
class UC002BorrowBookTest extends AbstractBrowserlessTest {

	private static final int AVAILABLE_COL = 3;

	private static final int BORROW_COL = 4;

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
		// Delete all loans (open and closed) for the test books first to satisfy FK
		// constraints, then remove the book rows themselves.
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

	private void searchFor(String term) {
		test(find(TextField.class).single()).setValue(term);
	}

	@SuppressWarnings("unchecked")
	private Grid<Book> catalogGrid() {
		return find(Grid.class).single();
	}

	// -------------------------------------------------------------------------
	// Borrow button visibility
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-002", scenario = "Main Success Scenario", businessRules = { "BR-009" })
	void borrow_button_appears_for_available_book() {
		String prefix = UUID.randomUUID().toString();
		insertTestBook(prefix, 2);

		navigate(CatalogView.class);
		searchFor(prefix);

		var grid = catalogGrid();
		// Asking for the cell renders its component; getRow(0) alone does not (see
		// UC001SearchCatalogTest).
		assertThat(test(grid).getCellText(0, BORROW_COL)).isEqualTo("Borrow");
	}

	@Test
	@UseCase(id = "UC-002", scenario = "A1: No Copy Available", businessRules = { "BR-008", "BR-009" })
	void borrow_button_absent_for_unavailable_book() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		insertOpenLoan(aliceMemberId(), bookId);

		navigate(CatalogView.class);
		searchFor(prefix);

		var grid = catalogGrid();
		assertThat(test(grid).getCellText(0, BORROW_COL)).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Happy path — dialog flow
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-002", scenario = "Main Success Scenario", businessRules = { "FR-005" })
	void successful_borrow_shows_success_notification() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 2);

		navigate(CatalogView.class);
		searchFor(prefix);

		var grid = catalogGrid();
		test(grid).getCellText(0, BORROW_COL);

		// Click the Borrow button to open the confirmation dialog.
		test(find(Button.class, grid).withText("Borrow").single()).click();

		// Confirm the dialog.
		test(find(ConfirmDialog.class).single()).confirm();

		// A success notification must appear.
		String notificationText = test(find(Notification.class).single()).getText();
		assertThat(notificationText).contains("borrowed successfully");

		// Ensure the loan was actually created.
		assertThat(countOpenLoans(bookId)).isEqualTo(1);
	}

	@Test
	@UseCase(id = "UC-002", scenario = "Main Success Scenario", businessRules = { "BR-011" })
	void successful_borrow_updates_availability_in_grid() {
		String prefix = UUID.randomUUID().toString();
		insertTestBook(prefix, 2);

		navigate(CatalogView.class);
		searchFor(prefix);

		var grid = catalogGrid();
		// Verify initial availability.
		assertThat(test(grid).getCellText(0, AVAILABLE_COL)).isEqualTo("2 of 2");

		test(grid).getCellText(0, BORROW_COL);
		test(find(Button.class, grid).withText("Borrow").single()).click();
		test(find(ConfirmDialog.class).single()).confirm();

		// BR-011: the grid must refresh without a page reload.
		assertThat(test(grid).getCellText(0, AVAILABLE_COL)).isEqualTo("1 of 2");
	}

	// -------------------------------------------------------------------------
	// Alternative flow A1 — no copy available
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-002", scenario = "A1: No Copy Available", businessRules = { "BR-009" })
	void borrow_service_throws_when_no_copy_available() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		insertOpenLoan(aliceMemberId(), bookId);

		int loansBefore = countOpenLoans(bookId);

		assertThatThrownBy(() -> loanService.borrow(bookId)).isInstanceOf(NoAvailableCopyException.class);

		// Open-loan count must not have changed.
		assertThat(countOpenLoans(bookId)).isEqualTo(loansBefore);
	}

	@Test
	@UseCase(id = "UC-002", scenario = "A1: No Copy Available", businessRules = { "BR-009" })
	void borrow_dialog_shows_error_notification_when_no_copy_available() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		String title = prefix + " Test Book";
		insertOpenLoan(aliceMemberId(), bookId);

		navigate(CatalogView.class);

		// Construct and open the dialog directly — the book is unavailable so no Borrow
		// button is rendered, but we still need to test the dialog's error handling path.
		var dialog = new BorrowDialog(bookId, title, loanService, () -> {
		});
		test(dialog).open();
		test(dialog).confirm();

		String notificationText = test(find(Notification.class).single()).getText();
		assertThat(notificationText).contains("No copy of").contains("is available right now");
	}

	// -------------------------------------------------------------------------
	// Loan structure invariants
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-002", scenario = "Main Success Scenario", businessRules = { "BR-012", "C-019" })
	void loan_row_has_null_returned_at_and_no_due_date() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);

		// Borrow through the service so the transaction and lock are exercised.
		loanService.borrow(bookId);

		var loan = dsl.selectFrom(LOAN).where(LOAN.BOOK_ID.eq(bookId)).fetchOne();
		assertThat(loan).isNotNull();
		// BR-012: no due date — returned_at is null while the copy is still on loan.
		assertThat(loan.getReturnedAt()).isNull();
		// C-019: borrowed_at is populated automatically via the column DEFAULT.
		assertThat(loan.getBorrowedAt()).isNotNull();
	}

	// -------------------------------------------------------------------------
	// Alternative flow A2 — no member profile (librarian without member row)
	// -------------------------------------------------------------------------

	@Test
	@WithUserDetails("librarian")
	@UseCase(id = "UC-002", scenario = "A2: Actor Has No Member Profile", businessRules = { "BR-010", "C-009" })
	void borrow_service_throws_when_no_member_profile() {
		// Use any book ID — the service must fail before even reaching the repository's
		// availability check because the signed-in user has no member row.
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 2);

		assertThatThrownBy(() -> loanService.borrow(bookId)).isInstanceOf(NoMemberProfileException.class);
	}

	@Test
	@WithUserDetails("librarian")
	@UseCase(id = "UC-002", scenario = "A2: Actor Has No Member Profile", businessRules = { "BR-010", "C-009" })
	void borrow_dialog_shows_error_notification_when_no_member_profile() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 2);
		String title = prefix + " Test Book";

		navigate(CatalogView.class);

		var dialog = new BorrowDialog(bookId, title, loanService, () -> {
		});
		test(dialog).open();
		test(dialog).confirm();

		String notificationText = test(find(Notification.class).single()).getText();
		assertThat(notificationText).contains("Borrowing is not available for your account");
	}

	// -------------------------------------------------------------------------
	// Alternative flow A3 — concurrent borrows (atomicity canary)
	// -------------------------------------------------------------------------

	/**
	 * Verifies that exactly {@code min(copies, threads)} loans are created when multiple
	 * threads race to borrow a book simultaneously.
	 *
	 * <p>
	 * This test is a canary for two guarantees simultaneously:
	 * <ul>
	 * <li>Removing {@code .forUpdate()} from
	 * {@link ai.unified.process.demo.book.library.loan.domain.LoanRepository#borrowAtomically(long, long)}
	 * allows concurrent threads to read the same stale availability count before any
	 * insert, producing more loans than {@code book.copies}.</li>
	 * <li>Removing {@code @Transactional} from {@link LoanService#borrow(long)} releases
	 * the lock between the {@code SELECT FOR UPDATE} and the {@code INSERT}, producing
	 * the same race.</li>
	 * </ul>
	 * // Canary: removing .forUpdate() or @Transactional causes this test to fail.
	 *
	 * <p>
	 * Spring Security context is thread-local: the context is captured on the test
	 * thread, then re-applied in each worker before the start latch is released, so
	 * {@code CurrentUser.requireAppUserId()} succeeds inside
	 * {@code LoanService.borrow()}.
	 */
	@ParameterizedTest
	@CsvSource({ "1,5", "3,5" })
	@UseCase(id = "UC-002", scenario = "A3: Concurrent Borrow Exhausts Last Copy", businessRules = { "BR-009" })
	void concurrent_borrow_creates_exactly_one_loan(int copies, int threads) throws Exception {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, copies);

		// Capture the authenticated Spring Security context before spawning workers.
		// SecurityContextHolder is thread-local; workers must re-apply it so that
		// CurrentUser.requireAppUserId() succeeds on their threads.
		SecurityContext ctx = SecurityContextHolder.getContext();

		CountDownLatch startGate = new CountDownLatch(1);
		AtomicInteger noAvailableCount = new AtomicInteger(0);
		List<Exception> unexpectedErrors = new CopyOnWriteArrayList<>();

		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				// Propagate the test-thread security context to the worker.
				SecurityContextHolder.setContext(ctx);
				try {
					startGate.await();
					loanService.borrow(bookId);
				}
				catch (NoAvailableCopyException expected) {
					noAvailableCount.incrementAndGet();
				}
				catch (Exception unexpected) {
					unexpectedErrors.add(unexpected);
				}
			}));
		}

		// Release all threads simultaneously.
		startGate.countDown();

		executor.shutdown();
		boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
		if (!finished) {
			fail("Worker threads did not finish within 10 seconds");
		}

		// Rethrow anything a worker threw outside its own catch blocks (for example an
		// Error, or a failure in setContext), which would otherwise be lost.
		for (Future<?> future : futures) {
			future.get();
		}

		assertThat(unexpectedErrors).as("unexpected exceptions in worker threads").isEmpty();

		int expectedLoans = Math.min(copies, threads);
		assertThat(countOpenLoans(bookId)).as("open loans must equal min(copies, threads)").isEqualTo(expectedLoans);
		assertThat(noAvailableCount.get())
			.as("NoAvailableCopyException count must equal threads - min(copies, threads)")
			.isEqualTo(threads - expectedLoans);
	}

}
