package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.core.security.AppUserDetails;
import ai.unified.process.demo.book.library.core.security.Role;
import ai.unified.process.demo.book.library.core.ui.AbstractBrowserlessTest;
import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.OpenLoan;
import ai.unified.process.demo.book.library.usecase.UseCase;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithUserDetails;

import java.time.LocalDateTime;
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
 * Browserless unit tests for UC-004 View My Loans.
 *
 * <p>
 * The subject here is the list itself — what it shows, in what order, and what it looks
 * like when it is empty. Anything about the return action on it belongs to UC-003 and is
 * tested in {@code UC003ReturnBookTest}, even though both use the same screen.
 *
 * <p>
 * The empty list is reached two ways and both are covered, because two different code
 * paths produce it: a member who holds a profile and no loans (the repository finding
 * nothing, A1) and an account with no profile at all (the service short-circuiting,
 * BR-004).
 *
 * <p>
 * What each test proves:
 * <table border="1">
 * <caption>UC-004 coverage</caption>
 * <tr>
 * <td>Pre-1</td>
 * <td>{@code an_account_with_no_patron_profile_sees_the_empty_list} (and every test,
 * which runs under {@code @WithUserDetails})</td>
 * </tr>
 * <tr>
 * <td>Step 1</td>
 * <td>{@code the_list_shows_each_open_loan_with_its_book_and_borrow_date}</td>
 * </tr>
 * <tr>
 * <td>Step 2</td>
 * <td>{@code another_patrons_loan_is_not_listed},
 * {@code an_account_with_no_patron_profile_sees_the_empty_list}</td>
 * </tr>
 * <tr>
 * <td>Step 3, Post-S-1</td>
 * <td>{@code the_list_shows_each_open_loan_with_its_book_and_borrow_date},
 * {@code the_most_recently_taken_book_comes_first}</td>
 * </tr>
 * <tr>
 * <td>A1, Post-F-3</td>
 * <td>{@code a_member_with_a_profile_and_no_loans_sees_the_empty_list},
 * {@code an_account_with_no_patron_profile_sees_the_empty_list}</td>
 * </tr>
 * <tr>
 * <td>A2</td>
 * <td>{@code a_returned_book_leaves_the_list}</td>
 * </tr>
 * <tr>
 * <td>A3</td>
 * <td>{@code reopening_the_list_drops_a_loan_closed_elsewhere},
 * {@code navigating_to_the_list_while_already_on_it_shows_current_loans}</td>
 * </tr>
 * <tr>
 * <td>BR-001</td>
 * <td>{@code another_patrons_loan_is_not_listed}</td>
 * </tr>
 * <tr>
 * <td>BR-002</td>
 * <td>{@code a_returned_loan_is_not_listed}</td>
 * </tr>
 * <tr>
 * <td>BR-003</td>
 * <td>{@code the_most_recently_taken_book_comes_first}</td>
 * </tr>
 * <tr>
 * <td>BR-004</td>
 * <td>{@code an_account_with_no_patron_profile_sees_the_empty_list}</td>
 * </tr>
 * <tr>
 * <td>BR-005</td>
 * <td>{@code the_list_does_not_update_itself_while_it_is_open},
 * {@code navigating_to_the_list_while_already_on_it_shows_current_loans}</td>
 * </tr>
 * <tr>
 * <td>Post-S-2</td>
 * <td>{@code drawing_the_list_changes_nothing}</td>
 * </tr>
 * <tr>
 * <td>Post-F-2</td>
 * <td>{@code an_account_with_no_patron_profile_sees_the_empty_list}</td>
 * </tr>
 * <tr>
 * <td>Post-F-1</td>
 * <td>covered end to end by {@code UC004ViewMyLoansIT}, not here</td>
 * </tr>
 * <tr>
 * <td>Step 4</td>
 * <td>nothing to assert - the member reading the list is not a system behaviour</td>
 * </tr>
 * </table>
 *
 * <p>
 * Test data follows the convention set by the other loan suites: rows are inserted with a
 * {@code UUID} prefix and removed in {@link #cleanup()}.
 */
@WithUserDetails("alice")
class UC004ViewMyLoansTest extends AbstractBrowserlessTest {

	private static final int TITLE_COL = 0;

	private static final int AUTHOR_COL = 1;

	private static final int BORROWED_COL = 2;

	private static final int RETURN_COL = 3;

	@Autowired
	private LoanService loanService;

	@Autowired
	private DSLContext dsl;

	private final Set<Long> testBookIds = new HashSet<>();

	private final Set<Long> testMemberIds = new HashSet<>();

	private final Set<Long> testAppUserIds = new HashSet<>();

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

	private long insertTestBook(String uuidPrefix, String suffix, int copies) {
		Long id = dsl.insertInto(BOOK, BOOK.TITLE, BOOK.AUTHOR, BOOK.COPIES)
			.values(uuidPrefix + suffix, "Test Author", copies)
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

	private void insertReturnedLoan(long memberId, long bookId) {
		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID, LOAN.BORROWED_AT, LOAN.RETURNED_AT)
			.values(memberId, bookId, LocalDateTime.now().minusDays(6), LocalDateTime.now().minusDays(3))
			.execute();
	}

	private long insertOpenLoanReturningId(long memberId, long bookId) {
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

	private int copiesOf(long bookId) {
		Integer copies = dsl.select(BOOK.COPIES).from(BOOK).where(BOOK.ID.eq(bookId)).fetchOne(BOOK.COPIES);
		assertThat(copies).isNotNull();
		return copies;
	}

	/**
	 * Opens the list again, the way a member does. This used to detour via another view,
	 * because the list was read only in the constructor and navigating to the route
	 * already shown left the old rows on screen; {@code MyLoansView} now reads on every
	 * navigation, so the detour is gone and this is the gesture it claims to be.
	 */
	private void reopenTheList() {
		navigate(MyLoansView.class);
	}

	private void insertLoanTakenAt(long memberId, long bookId, LocalDateTime borrowedAt) {
		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID, LOAN.BORROWED_AT)
			.values(memberId, bookId, borrowedAt)
			.execute();
	}

	/** A throwaway account and its patron profile. */
	private record Patron(long appUserId, long memberId) {
	}

	private Patron insertOtherMember(String uuidPrefix) {
		// The stored value is an unusable placeholder: the account exists only to own a
		// patron profile and is never signed in to.
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

	@SuppressWarnings("unchecked")
	private Grid<OpenLoan> loansGrid() {
		return find(Grid.class).single();
	}

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
	// The list
	// -------------------------------------------------------------------------

	@Test
	@UseCase(id = "UC-004")
	void the_list_shows_each_open_loan_with_its_book_and_borrow_date() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 1);
		insertLoanTakenAt(aliceMemberId(), bookId, LocalDateTime.now());

		navigate(MyLoansView.class);

		var grid = loansGrid();
		int row = rowOf(prefix + " Test Book");
		assertThat(test(grid).getCellText(row, AUTHOR_COL)).isEqualTo("Test Author");
		assertThat(test(grid).getCellText(row, BORROWED_COL)).isEqualTo(LocalDateTime.now().toLocalDate().toString());
	}

	@Test
	@UseCase(id = "UC-004", businessRules = { "BR-003" })
	void the_most_recently_taken_book_comes_first() {
		String prefix = UUID.randomUUID().toString();
		long older = insertTestBook(prefix, " Older", 1);
		long newer = insertTestBook(prefix, " Newer", 1);
		long memberId = aliceMemberId();
		// Inserted oldest first, so insertion order and the expected order differ.
		insertLoanTakenAt(memberId, older, LocalDateTime.now().minusDays(9));
		insertLoanTakenAt(memberId, newer, LocalDateTime.now().minusDays(2));

		navigate(MyLoansView.class);

		// BR-003: newest first, so the more recently taken book sits above the older one.
		assertThat(rowOf(prefix + " Newer")).isLessThan(rowOf(prefix + " Older"));
	}

	/**
	 * BR-002: a loan that has been returned is history, not something the member still
	 * has out. The open loan alongside it is the positive control - without it an empty
	 * list would satisfy the assertion just as well.
	 */
	@Test
	@UseCase(id = "UC-004", businessRules = { "BR-002" })
	void a_returned_loan_is_not_listed() {
		String prefix = UUID.randomUUID().toString();
		long stillOut = insertTestBook(prefix, " Still Out", 1);
		long broughtBack = insertTestBook(prefix, " Brought Back", 1);
		long memberId = aliceMemberId();
		insertLoanTakenAt(memberId, stillOut, LocalDateTime.now());
		insertReturnedLoan(memberId, broughtBack);

		navigate(MyLoansView.class);

		assertThat(rowOf(prefix + " Still Out")).isNotNegative();
		assertThatThrownBy(() -> rowOf(prefix + " Brought Back")).isInstanceOf(AssertionError.class);
	}

	/**
	 * UC-004 BR-001, the listing half of what UC-003 asserts as a refusal: another
	 * patron's loan never appears on this member's list.
	 */
	@Test
	@UseCase(id = "UC-004", businessRules = { "BR-001" })
	void another_patrons_loan_is_not_listed() {
		String prefix = UUID.randomUUID().toString();
		long mine = insertTestBook(prefix, " Mine", 1);
		long theirs = insertTestBook(prefix, " Theirs", 1);
		Patron other = insertOtherMember(prefix);
		insertLoanTakenAt(aliceMemberId(), mine, LocalDateTime.now());
		insertLoanTakenAt(other.memberId(), theirs, LocalDateTime.now());

		navigate(MyLoansView.class);

		// The control: alice's own loan is listed, so the absence below means something.
		assertThat(rowOf(prefix + " Mine")).isNotNegative();
		assertThatThrownBy(() -> rowOf(prefix + " Theirs")).isInstanceOf(AssertionError.class);
	}

	/** A2, the listing half: once the return is made the row is gone from the list. */
	@Test
	@UseCase(id = "UC-004", scenario = "A2: Member Returns a Book From the List")
	void a_returned_book_leaves_the_list() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 1);
		insertLoanTakenAt(aliceMemberId(), bookId, LocalDateTime.now());

		navigate(MyLoansView.class);
		var grid = loansGrid();
		int before = test(grid).size();
		int row = rowOf(prefix + " Test Book");

		// Reading the cell is what renders the component column; without it the Return
		// button does not exist in the tree and the lookup below finds nothing.
		test(grid).getCellText(row, RETURN_COL);
		test(find(Button.class, grid).withText("Return").single()).click();
		test(find(ConfirmDialog.class).single()).confirm();

		assertThat(test(grid).size()).isEqualTo(before - 1);
		assertThatThrownBy(() -> rowOf(prefix + " Test Book")).isInstanceOf(AssertionError.class);
	}

	/**
	 * BR-005: the list is a snapshot. A loan closed elsewhere stays on screen until the
	 * list is opened again - which is the property A3 depends on.
	 */
	@Test
	@UseCase(id = "UC-004", businessRules = { "BR-005" })
	void the_list_does_not_update_itself_while_it_is_open() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 1);
		long loanId = insertOpenLoanReturningId(aliceMemberId(), bookId);

		navigate(MyLoansView.class);
		assertThat(rowOf(prefix + " Test Book")).isNotNegative();

		// Closed from somewhere else entirely, with this screen untouched.
		loanService.returnLoan(loanId);
		assertThat(countOpenLoans(bookId)).isZero();

		// The screen still shows it: nothing pushes the change to an open list.
		assertThat(rowOf(prefix + " Test Book")).isNotNegative();
	}

	/**
	 * A3: reopening the list shows it as it stands, without the loan closed elsewhere.
	 */
	@Test
	@UseCase(id = "UC-004", scenario = "A3: The List Has Gone Out of Date")
	void reopening_the_list_drops_a_loan_closed_elsewhere() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 1);
		long loanId = insertOpenLoanReturningId(aliceMemberId(), bookId);

		navigate(MyLoansView.class);
		// The control: the row is there before anything closes it.
		assertThat(rowOf(prefix + " Test Book")).isNotNegative();

		loanService.returnLoan(loanId);
		reopenTheList();

		assertThatThrownBy(() -> rowOf(prefix + " Test Book")).isInstanceOf(AssertionError.class);
	}

	/**
	 * A3 by the gesture a member actually uses: choosing My Loans while already on it.
	 * The list used to be read only when the view was built, so this navigation left
	 * whatever was on screen; it now reads again and picks up a loan taken since.
	 */
	@Test
	@UseCase(id = "UC-004", scenario = "A3: The List Has Gone Out of Date")
	void navigating_to_the_list_while_already_on_it_shows_current_loans() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 1);

		navigate(MyLoansView.class);
		// The control: the loan does not exist yet, so the list cannot be showing it.
		assertThatThrownBy(() -> rowOf(prefix + " Test Book")).isInstanceOf(AssertionError.class);

		insertLoanTakenAt(aliceMemberId(), bookId, LocalDateTime.now());

		// Straight back to the same route, with no detour through another view.
		navigate(MyLoansView.class);

		assertThat(rowOf(prefix + " Test Book")).isNotNegative();
	}

	/**
	 * Post-S-2: drawing the list is a read. Availability is derived from copies and open
	 * loans, so both are checked either side of the navigation. The failure path is
	 * asserted by {@code an_account_with_no_patron_profile_sees_the_empty_list}.
	 */
	@Test
	@UseCase(id = "UC-004")
	void drawing_the_list_changes_nothing() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 2);
		insertLoanTakenAt(aliceMemberId(), bookId, LocalDateTime.now());

		int openLoansBefore = countOpenLoans(bookId);
		int copiesBefore = copiesOf(bookId);

		navigate(MyLoansView.class);
		assertThat(rowOf(prefix + " Test Book")).isNotNegative();

		assertThat(countOpenLoans(bookId)).isEqualTo(openLoansBefore);
		assertThat(copiesOf(bookId)).isEqualTo(copiesBefore);
	}

	// -------------------------------------------------------------------------
	// Alternative flow A1 — nothing out
	// -------------------------------------------------------------------------

	/**
	 * A1 proper: the member holds a patron profile, so the empty list comes from the
	 * repository finding no open loan — not from the service's no-profile shortcut, which
	 * is BR-004 and a different code path.
	 */
	@Test
	@UseCase(id = "UC-004", scenario = "A1: Member Has Nothing Out")
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
	 * BR-004: an account with no patron profile owns no loan, and reaches the same empty
	 * list by the service short-circuit rather than by a query returning nothing.
	 */
	@Test
	@WithUserDetails("librarian")
	@UseCase(id = "UC-004", scenario = "A1: Member Has Nothing Out", businessRules = { "BR-004" })
	void an_account_with_no_patron_profile_sees_the_empty_list() {
		// Someone else's loan exists, so there is state the failed read could disturb.
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, " Test Book", 1);
		insertLoanTakenAt(aliceMemberId(), bookId, LocalDateTime.now());
		int openLoansBefore = countOpenLoans(bookId);
		int copiesBefore = copiesOf(bookId);

		navigate(MyLoansView.class);

		var grid = loansGrid();
		assertThat(test(grid).size()).isZero();
		assertThat(grid.getEmptyStateText()).isEqualTo("You have no books on loan.");

		// Post-F-2: reaching the empty list is a read like any other, so nothing moved.
		assertThat(countOpenLoans(bookId)).isEqualTo(openLoansBefore);
		assertThat(copiesOf(bookId)).isEqualTo(copiesBefore);
	}

}
