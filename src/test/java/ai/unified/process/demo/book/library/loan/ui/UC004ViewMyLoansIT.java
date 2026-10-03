package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.core.ui.PlaywrightIT;
import ai.unified.process.demo.book.library.usecase.UseCase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import in.virit.mopo.GridPw;
import in.virit.mopo.Mopo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static ai.unified.process.demo.book.library.db.Tables.APP_USER;
import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;
import static ai.unified.process.demo.book.library.db.Tables.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playwright end-to-end test for UC-004 View My Loans.
 *
 * <p>
 * The subject is the list itself. The return action offered on the same screen belongs to
 * UC-003 and is driven by {@code UC003ReturnBookIT}.
 *
 * <p>
 * The populated-list test creates its own book and loans rather than leaning on alice's
 * seeded ones, so the order it asserts is the order it set up; rows are removed in
 * {@link #cleanup()}.
 */
@DisplayName("UC-004: View My Loans")
class UC004ViewMyLoansIT extends PlaywrightIT {

	private static final String LIBRARIAN = "librarian";

	private static final String ALICE = "alice";

	/** Column indices in the My Loans grid (0-based). */
	private static final int TITLE_COL = 0;

	private static final int AUTHOR_COL = 1;

	private static final int BORROWED_COL = 2;

	@Autowired
	private DSLContext dsl;

	private final Set<Long> testBookIds = new HashSet<>();

	@AfterEach
	void cleanup() {
		if (testBookIds.isEmpty()) {
			return;
		}
		dsl.deleteFrom(LOAN).where(LOAN.BOOK_ID.in(testBookIds)).execute();
		dsl.deleteFrom(BOOK).where(BOOK.ID.in(testBookIds)).execute();
		testBookIds.clear();
	}

	private long insertTestBook(String title) {
		Long id = dsl.insertInto(BOOK, BOOK.TITLE, BOOK.AUTHOR, BOOK.COPIES)
			.values(title, "Test Author", 1)
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
			.where(APP_USER.USERNAME.eq(ALICE))
			.fetchOne(MEMBER.ID);
		assertThat(id).as("alice must have a member row").isNotNull();
		return id;
	}

	private void insertLoanTakenAt(long memberId, long bookId, LocalDateTime borrowedAt) {
		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID, LOAN.BORROWED_AT)
			.values(memberId, bookId, borrowedAt)
			.execute();
	}

	@Test
	@DisplayName("BR-004: an account with no patron profile sees the empty list")
	@UseCase(id = "UC-004", scenario = "A1: Member Has Nothing Out", businessRules = { "BR-004" })
	void an_account_with_no_patron_profile_sees_the_empty_list() {
		page.navigate(url("my-loans"));

		// Post-F-1: the route answers with the login form, and no loan information is on
		// screen at that point - not the list, not even an empty one.
		PlaywrightAssertions
			.assertThat(page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")))
			.isVisible();
		assertThat(page.locator("vaadin-grid").count()).as("no loan list before signing in").isZero();

		logIn(LIBRARIAN);
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();

		assertThat(new GridPw(page).getRenderedRowCount()).isZero();
		PlaywrightAssertions.assertThat(page.getByText("You have no books on loan.")).isVisible();
	}

	@Test
	@DisplayName("step 3, BR-003: the list shows each loan, most recently taken first")
	@UseCase(id = "UC-004", businessRules = { "BR-003" })
	void the_list_shows_its_columns_newest_first() {
		String prefix = UUID.randomUUID().toString();
		String newerTitle = prefix + " Newer";
		String olderTitle = prefix + " Older";
		long newer = insertTestBook(newerTitle);
		long older = insertTestBook(olderTitle);
		long memberId = aliceMemberId();
		// Inserted oldest first, so insertion order and the expected order differ. Both
		// are hours old, newer than any loan DemoDataSeed gives alice (4 days at the
		// most recent), so these two own rows 0 and 1.
		insertLoanTakenAt(memberId, older, LocalDateTime.now().minusHours(3));
		insertLoanTakenAt(memberId, newer, LocalDateTime.now().minusHours(1));

		page.navigate(url("my-loans"));
		logIn(ALICE);
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();

		// Assert the titles before trusting the positions: alice also holds seeded loans,
		// so a wrong order shows up here rather than in the column assertions below.
		GridPw grid = new GridPw(page);
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(TITLE_COL)).hasText(newerTitle);
		PlaywrightAssertions.assertThat(grid.getRow(1).getCell(TITLE_COL)).hasText(olderTitle);

		// Step 3: author and borrow date render alongside the title.
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(AUTHOR_COL)).hasText("Test Author");
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(BORROWED_COL))
			.hasText(LocalDateTime.now().toLocalDate().toString());
	}

	private void logIn(String username) {
		page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username")).fill(username);
		// Seeded demo accounts use the username as their password.
		page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill(username);
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
		Mopo.waitForConnectionToSettle(page);
	}

	private String url(String route) {
		return "http://localhost:" + localServerPort + "/" + route;
	}

}
