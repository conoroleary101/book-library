package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.catalog.ui.CatalogView;
import ai.unified.process.demo.book.library.core.security.AppUserDetails;
import ai.unified.process.demo.book.library.core.security.Role;
import ai.unified.process.demo.book.library.core.ui.AbstractBrowserlessTest;
import ai.unified.process.demo.book.library.loan.domain.LoanAlreadyClosedException;
import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.OpenLoan;
import ai.unified.process.demo.book.library.usecase.UseCase;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithUserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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

/**
 * Browserless unit tests for UC-003 Return Book.
 *
 * <p>
 * Most tests sign in as {@code alice}, who has a patron profile. The empty list is
 * reached two ways and both are covered, because two different code paths produce it: a
 * member who holds a profile and no loans (the repository finding nothing, A1), and an
 * account with no profile at all (the service short-circuiting, BR-008).
 *
 * <p>
 * What each test proves:
 * <table border="1">
 * <caption>UC-003 coverage</caption>
 * <tr>
 * <td>Steps 1-2</td>
 * <td>{@code my_loans_lists_the_open_loan_with_its_book_and_borrow_date}</td>
 * </tr>
 * <tr>
 * <td>Steps 5-6, BR-001, BR-007</td>
 * <td>{@code returning_closes_the_loan_and_keeps_it_as_history}</td>
 * </tr>
 * <tr>
 * <td>Step 7</td>
 * <td>{@code the_returned_book_leaves_the_members_open_loans}</td>
 * </tr>
 * <tr>
 * <td>BR-003</td>
 * <td>{@code the_return_follows_the_borrow},
 * {@code a_return_that_precedes_its_borrow_is_rejected}</td>
 * </tr>
 * <tr>
 * <td>BR-004</td>
 * <td>{@code returning_frees_a_copy_in_the_catalog},
 * {@code a_second_account_also_sees_the_freed_copy}</td>
 * </tr>
 * <tr>
 * <td>A1</td>
 * <td>{@code a_member_with_a_profile_and_no_loans_sees_the_empty_list}</td>
 * </tr>
 * <tr>
 * <td>A2</td>
 * <td>{@code returning_an_already_closed_loan_reports_it_and_closes_nothing_twice}</td>
 * </tr>
 * <tr>
 * <td>A3</td>
 * <td>{@code cancelling_the_confirmation_closes_nothing_and_says_nothing}</td>
 * </tr>
 * <tr>
 * <td>A4, BR-005</td>
 * <td>{@code concurrent_returns_close_the_loan_once_and_free_one_copy}</td>
 * </tr>
 * <tr>
 * <td>BR-002</td>
 * <td>{@code another_patrons_loan_cannot_be_closed_and_is_not_listed}</td>
 * </tr>
 * <tr>
 * <td>BR-008</td>
 * <td>{@code an_account_with_no_patron_profile_sees_the_empty_list}</td>
 * </tr>
 * </table>
 *
 * <p>
 * Test data follows the convention set by {@code UC002BorrowBookTest}: rows are inserted
 * with a {@code UUID} prefix and removed in {@link #cleanup()}. A Flyway test migration
 * is not used, because migrations run before {@code DemoDataSeed} and would suppress the
 * demo catalog the other suites assert against.
 */
@WithUserDetails("alice")
class UC003ReturnBookTest extends AbstractBrowserlessTest {

	private static final int TITLE_COL = 0;

	private static final int AUTHOR_COL = 1;

	private static final int BORROWED_COL = 2;

	private static final int RETURN_COL = 3;

	private static final int CATALOG_AVAILABLE_COL = 3;

	@Autowired
	private LoanService loanService;

	@Autowired
	private DSLContext dsl;

	private final Set<Long> testBookIds = new HashSet<>();

	private final Set<Long> testMemberIds = new HashSet<>();

	private final Set<Long> testAppUserIds = new HashSet<>();

	// -------------------------------------------------------------------------
	// Teardown
	// -------------------------------------------------------------------------

	@AfterEach
	void cleanup() {
		if (!testBookIds.isEmpty()) {
			dsl.deleteFrom(LOAN).where(LOAN.BOOK_ID.in(testBookIds)).execute();
			dsl.deleteFrom(BOOK).where(BOOK.ID.in(testBookIds)).execute();
			testBookIds.clear();
		}
		if (!testMemberIds.isEmpty()) {
			dsl.deleteFrom(LOAN).where(LOAN.MEMBER_ID.in(testMemberIds)).execute();
			dsl.deleteFrom(MEMBER).where(MEMBER.ID.in(testMemberIds)).execute();
			testMemberIds.clear();
		}
		if (!testAppUserIds.isEmpty()) {
			dsl.deleteFrom(APP_USER).where(APP_USER.ID.in(testAppUserIds)).execute();
			testAppUserIds.clear();
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private long insertTestBook(String uuidPrefix, int copies) {
		Long id = dsl.insertInto(BOOK, BOOK.TITLE, BOOK.AUTHOR, BOOK.COPIES)
			.values(uuidPrefix + " Test Book", "Test Author", copies)
			.returningResult(BOOK.ID)
			.fetchOne(BOOK.ID);
		assertThat(id).isNotNull();
		testBookIds.add(id);
		return id;
	}

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

	/** A throwaway account and its patron profile, from {@link #insertOtherMember}. */
	private record Patron(long appUserId, long memberId) {
	}

	/** Creates a second patron, for tests that need an account other than alice's. */
	private Patron insertOtherMember(String uuidPrefix) {
		// The stored value is an unusable placeholder: this account exists only to own a
		// loan and is never signed in to.
		Long userId = dsl.insertInto(APP_USER, APP_USER.USERNAME, APP_USER.PASSWORD_HASH, APP_USER.ROLE)
			.values(uuidPrefix, "unusable-placeholder", "MEMBER")
			.returningResult(APP_USER.ID)
			.fetchOne(APP_USER.ID);
		assertThat(userId).isNotNull();
		testAppUserIds.add(userId);

		Long memberId = dsl.insertInto(MEMBER, MEMBER.USER_ID, MEMBER.NAME, MEMBER.EMAIL)
			.values(userId, "Other Patron", uuidPrefix + "@example.org")
			.returningResult(MEMBER.ID)
			.fetchOne(MEMBER.ID);
		assertThat(memberId).isNotNull();
		testMemberIds.add(memberId);
		return new Patron(userId, memberId);
	}

	/**
	 * Signs in as a patron created during the test. {@code @WithUserDetails} cannot serve
	 * here: it resolves the account before the test body runs, and the account does not
	 * exist yet at that point.
	 */
	private void signInAs(Patron patron, String username) {
		var details = new AppUserDetails(patron.appUserId(), username, "unused", Role.MEMBER);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
	}

	private long insertOpenLoan(long memberId, long bookId) {
		Long id = dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID)
			.values(memberId, bookId)
			.returningResult(LOAN.ID)
			.fetchOne(LOAN.ID);
		assertThat(id).isNotNull();
		return id;
	}

	private int countOpenLoans(long bookId) {
		return dsl.fetchCount(LOAN, LOAN.BOOK_ID.eq(bookId).and(LOAN.RETURNED_AT.isNull()));
	}

	/**
	 * Reads the availability cell for a book from the catalog. Navigating to the route
	 * already shown does not rebuild the view, so the term is cleared and retyped to
	 * force a fresh query.
	 */
	private String catalogAvailability(String term) {
		navigate(CatalogView.class);
		var search = find(TextField.class).single();
		test(search).setValue("");
		test(search).setValue(term);
		return test(find(Grid.class).single()).getCellText(0, CATALOG_AVAILABLE_COL);
	}

	@SuppressWarnings("unchecked")
	private Grid<OpenLoan> loansGrid() {
		return find(Grid.class).single();
	}

	/** Row index of a book in the loans grid, by title. */
	private int rowOf(String title) {
		var grid = loansGrid();
		for (int row = 0; row < test(grid).size(); row++) {
			if (test(grid).getCellText(row, TITLE_COL).equals(title)) {
				return row;
			}
		}
		throw new AssertionError("No loan row for title: " + title);
	}

	// -------------------------------------------------------------------------
	// Main success scenario
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-003")
	void my_loans_lists_the_open_loan_with_its_book_and_borrow_date() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		insertOpenLoan(aliceMemberId(), bookId);

		navigate(MyLoansView.class);

		var grid = loansGrid();
		int row = rowOf(prefix + " Test Book");
		assertThat(test(grid).getCellText(row, AUTHOR_COL)).isEqualTo("Test Author");
		assertThat(test(grid).getCellText(row, BORROWED_COL)).isEqualTo(LocalDateTime.now().toLocalDate().toString());
	}

	@Test
	@UseCase(id = "UC-003", businessRules = { "BR-001", "BR-007" })
	void returning_closes_the_loan_and_keeps_it_as_history() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		long loanId = insertOpenLoan(aliceMemberId(), bookId);

		navigate(MyLoansView.class);
		var grid = loansGrid();
		int row = rowOf(prefix + " Test Book");
		test(grid).getCellText(row, RETURN_COL);
		test(find(Button.class, grid).withText("Return").single()).click();
		test(find(ConfirmDialog.class).single()).confirm();

		assertThat(test(find(Notification.class).single()).getText()).contains("returned successfully");

		// BR-001 and BR-007: the loan is closed, not removed. The row is still there and
		// now carries a return.
		var loan = dsl.selectFrom(LOAN).where(LOAN.ID.eq(loanId)).fetchOne();
		assertThat(loan).as("the loan row must survive the return").isNotNull();
		assertThat(loan.getReturnedAt()).isNotNull();
		assertThat(loan.getMemberId()).isEqualTo(aliceMemberId());
	}

	@Test
	@UseCase(id = "UC-003", businessRules = { "BR-003" })
	void the_return_follows_the_borrow() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		long loanId = insertOpenLoan(aliceMemberId(), bookId);

		loanService.returnLoan(loanId);

		var loan = dsl.selectFrom(LOAN).where(LOAN.ID.eq(loanId)).fetchOne();
		assertThat(loan).isNotNull();
		assertThat(loan.getReturnedAt()).isAfter(loan.getBorrowedAt());
	}

	/**
	 * Canary for {@code chk_loan_returned_after_borrowed} in {@code V004}: dropping the
	 * constraint lets the out-of-order return succeed and turns this test red.
	 */
	@Test
	@UseCase(id = "UC-003", businessRules = { "BR-003" })
	void a_return_that_precedes_its_borrow_is_rejected() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		// A loan taken out tomorrow: returning it now would record a return before its
		// own borrow, which the database must refuse.
		Long loanId = dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID, LOAN.BORROWED_AT)
			.values(aliceMemberId(), bookId, LocalDateTime.now().plusDays(1))
			.returningResult(LOAN.ID)
			.fetchOne(LOAN.ID);
		assertThat(loanId).isNotNull();

		assertThatThrownBy(() -> loanService.returnLoan(loanId))
			.as("the out-of-order return must be refused by the database constraint")
			.hasStackTraceContaining("chk_loan_returned_after_borrowed");

		// The refused return left the loan open.
		assertThat(countOpenLoans(bookId)).isEqualTo(1);
	}

	@Test
	@UseCase(id = "UC-003")
	void the_returned_book_leaves_the_members_open_loans() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		insertOpenLoan(aliceMemberId(), bookId);

		navigate(MyLoansView.class);
		var grid = loansGrid();
		int before = test(grid).size();
		int row = rowOf(prefix + " Test Book");

		test(grid).getCellText(row, RETURN_COL);
		test(find(Button.class, grid).withText("Return").single()).click();
		test(find(ConfirmDialog.class).single()).confirm();

		// Step 7: one fewer row, and the returned book is no longer among them.
		assertThat(test(grid).size()).isEqualTo(before - 1);
		assertThatThrownBy(() -> rowOf(prefix + " Test Book")).isInstanceOf(AssertionError.class);
	}

	@Test
	@UseCase(id = "UC-003", businessRules = { "BR-004" })
	void returning_frees_a_copy_in_the_catalog() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		long loanId = insertOpenLoan(aliceMemberId(), bookId);

		navigate(CatalogView.class);
		test(find(TextField.class).single()).setValue(prefix);
		assertThat(test(find(Grid.class).single()).getCellText(0, CATALOG_AVAILABLE_COL)).isEqualTo("0 of 1");

		loanService.returnLoan(loanId);

		// BR-004: availability is derived from the open loans, so the freed copy shows as
		// soon as the catalog is read again. Navigating to the route already shown does
		// not rebuild the view, so the search is re-run instead — which is what a member
		// does, and is not a page reload.
		test(find(TextField.class).single()).setValue("");
		test(find(TextField.class).single()).setValue(prefix);
		assertThat(test(find(Grid.class).single()).getCellText(0, CATALOG_AVAILABLE_COL)).isEqualTo("1 of 1");
	}

	/**
	 * BR-004 says the freed copy is visible to whoever reads the catalog, not only to the
	 * member who returned it. The availability expression takes no member or session
	 * parameter, so a difference would be structurally impossible - this asserts it
	 * rather than leaving it implied.
	 */
	@Test
	@UseCase(id = "UC-003", businessRules = { "BR-004" })
	void a_second_account_also_sees_the_freed_copy() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		long loanId = insertOpenLoan(aliceMemberId(), bookId);

		Authentication alice = SecurityContextHolder.getContext().getAuthentication();
		Patron other = insertOtherMember(prefix);

		// Before the return, the second account sees the copy as out. Without this the
		// assertion after the return could pass on a book that was never on loan.
		signInAs(other, prefix);
		assertThat(catalogAvailability(prefix)).isEqualTo("0 of 1");

		// Alice returns her own loan.
		SecurityContextHolder.getContext().setAuthentication(alice);
		loanService.returnLoan(loanId);

		// The same read by the other account now shows the freed copy.
		signInAs(other, prefix);
		assertThat(catalogAvailability(prefix)).isEqualTo("1 of 1");
	}

	// -------------------------------------------------------------------------
	// Alternative flow A1 — no open loans
	// -------------------------------------------------------------------------

	/**
	 * A1 proper: the member holds a patron profile, so the empty list comes from the
	 * repository finding no open loan - not from the service's no-profile shortcut, which
	 * is BR-008 and a different code path.
	 */
	@Test
	@UseCase(id = "UC-003", scenario = "A1: Member Has No Open Loans")
	void a_member_with_a_profile_and_no_loans_sees_the_empty_list() {
		String prefix = UUID.randomUUID().toString();
		Patron patron = insertOtherMember(prefix);
		signInAs(patron, prefix);

		navigate(MyLoansView.class);

		var grid = loansGrid();
		assertThat(test(grid).size()).isZero();
		assertThat(grid.getEmptyStateText()).isEqualTo("You have no books on loan.");
	}

	/**
	 * BR-008: an account with no patron profile owns no loan, and reaches the same empty
	 * list by the service short-circuit rather than by a query returning nothing.
	 */
	@Test
	@WithUserDetails("librarian")
	@UseCase(id = "UC-003", scenario = "A1: Member Has No Open Loans", businessRules = { "BR-008" })
	void an_account_with_no_patron_profile_sees_the_empty_list() {
		navigate(MyLoansView.class);

		var grid = loansGrid();
		assertThat(test(grid).size()).isZero();
		assertThat(grid.getEmptyStateText()).isEqualTo("You have no books on loan.");
	}

	// -------------------------------------------------------------------------
	// Alternative flow A2 — the loan has already been closed
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-003", scenario = "A2: The Loan Has Already Been Closed")
	void returning_an_already_closed_loan_reports_it_and_closes_nothing_twice() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		long loanId = insertOpenLoan(aliceMemberId(), bookId);

		navigate(MyLoansView.class);
		var grid = loansGrid();
		int row = rowOf(prefix + " Test Book");
		test(grid).getCellText(row, RETURN_COL);
		test(find(Button.class, grid).withText("Return").single()).click();

		// The loan is closed behind the open dialog, as a second screen would do.
		loanService.returnLoan(loanId);
		LocalDateTime firstReturn = dsl.select(LOAN.RETURNED_AT)
			.from(LOAN)
			.where(LOAN.ID.eq(loanId))
			.fetchOne(LOAN.RETURNED_AT);
		assertThat(firstReturn).isNotNull();

		test(find(ConfirmDialog.class).single()).confirm();

		assertThat(test(find(Notification.class).single()).getText()).contains("already been returned");
		// The recorded return is still the first one; nothing was closed a second time.
		assertThat(dsl.select(LOAN.RETURNED_AT).from(LOAN).where(LOAN.ID.eq(loanId)).fetchOne(LOAN.RETURNED_AT))
			.isEqualTo(firstReturn);
		// A2 step 3 continues at step 1: the list is redrawn, so the stale row is gone.
		assertThatThrownBy(() -> rowOf(prefix + " Test Book")).isInstanceOf(AssertionError.class);
	}

	// -------------------------------------------------------------------------
	// Alternative flow A3 — member abandons the confirmation
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-003", scenario = "A3: Member Abandons the Confirmation")
	void cancelling_the_confirmation_closes_nothing_and_says_nothing() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		insertOpenLoan(aliceMemberId(), bookId);

		navigate(MyLoansView.class);
		var grid = loansGrid();
		int row = rowOf(prefix + " Test Book");
		// Render the cell once and keep the button: asking for the cell again renders a
		// second Return button into the tree and the lookup then finds two.
		test(grid).getCellText(row, RETURN_COL);
		Button returnButton = find(Button.class, grid).withText("Return").single();
		test(returnButton).click();

		// The dialog really is on screen, so what follows is about a cancelled
		// confirmation rather than about a button that quietly did nothing.
		ConfirmDialog dialog = find(ConfirmDialog.class).single();
		assertThat(dialog.isOpened()).as("the confirmation is open before it is cancelled").isTrue();
		assertThat(find(Notification.class).exists()).as("nothing is shown before the member decides").isFalse();

		test(dialog).cancel();

		assertThat(dialog.isOpened()).as("the confirmation closed on cancel").isFalse();
		assertThat(countOpenLoans(bookId)).as("the loan is still open").isEqualTo(1);
		assertThat(find(Notification.class).exists()).as("no confirmation and no error after cancelling").isFalse();

		// Positive control: the same Notification query must be able to find a message in
		// this very test, or "no message" proves nothing.
		test(returnButton).click();
		test(find(ConfirmDialog.class).single()).confirm();
		assertThat(find(Notification.class).exists()).as("the same query does find a message when one is shown")
			.isTrue();
		assertThat(countOpenLoans(bookId)).isZero();
	}

	// -------------------------------------------------------------------------
	// Alternative flow A4 — concurrent return of the same loan
	// -------------------------------------------------------------------------

	/**
	 * Canary for BR-005: "still open" and "belongs to this member" live in the same
	 * statement that records the return. Splitting them into a read followed by an
	 * unconditional update lets two threads both see the loan open and close it twice.
	 */
	@Test
	@UseCase(id = "UC-003", scenario = "A4: Concurrent Return of the Same Loan", businessRules = { "BR-005" })
	void concurrent_returns_close_the_loan_once_and_free_one_copy() throws Exception {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 3);
		long loanId = insertOpenLoan(aliceMemberId(), bookId);
		insertOpenLoan(aliceMemberId(), bookId);

		int threads = 5;
		SecurityContext ctx = SecurityContextHolder.getContext();
		CountDownLatch startGate = new CountDownLatch(1);
		AtomicInteger closed = new AtomicInteger();
		AtomicInteger alreadyClosed = new AtomicInteger();
		List<Exception> unexpected = new CopyOnWriteArrayList<>();

		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				SecurityContextHolder.setContext(ctx);
				try {
					startGate.await();
					loanService.returnLoan(loanId);
					closed.incrementAndGet();
				}
				catch (LoanAlreadyClosedException expected) {
					alreadyClosed.incrementAndGet();
				}
				catch (Exception ex) {
					unexpected.add(ex);
				}
			}));
		}
		startGate.countDown();
		for (Future<?> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		executor.shutdown();
		assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		assertThat(unexpected).isEmpty();
		assertThat(closed).hasValue(1);
		assertThat(alreadyClosed).hasValue(threads - 1);
		// Two loans were open; exactly one closed, so one copy was freed, never two.
		assertThat(countOpenLoans(bookId)).isEqualTo(1);
	}

	// -------------------------------------------------------------------------
	// BR-002 — a member closes only their own loans
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-003", businessRules = { "BR-002" })
	void another_patrons_loan_cannot_be_closed_and_is_not_listed() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		Patron other = insertOtherMember(prefix);
		long otherLoanId = insertOpenLoan(other.memberId(), bookId);

		// Not offered: the other patron's loan is not on alice's list.
		navigate(MyLoansView.class);
		assertThatThrownBy(() -> rowOf(prefix + " Test Book")).isInstanceOf(AssertionError.class);

		// Not reachable either: asking for it directly is refused.
		assertThatThrownBy(() -> loanService.returnLoan(otherLoanId)).isInstanceOf(LoanAlreadyClosedException.class);

		// And the other patron's loan is untouched.
		assertThat(countOpenLoans(bookId)).isEqualTo(1);
	}

}
