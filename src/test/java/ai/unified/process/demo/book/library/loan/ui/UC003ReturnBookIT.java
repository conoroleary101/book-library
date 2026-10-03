package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.core.ui.PlaywrightIT;
import ai.unified.process.demo.book.library.usecase.UseCase;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import in.virit.mopo.GridPw;
import in.virit.mopo.Mopo;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static ai.unified.process.demo.book.library.db.Tables.APP_USER;
import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;
import static ai.unified.process.demo.book.library.db.Tables.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playwright end-to-end tests for UC-003 Return Book.
 *
 * <p>
 * Drives the running application through a headless Chromium browser, asserting only what
 * a member can see. The browserless suite covers every rule in detail; this one checks
 * that the return actually works through the rendered UI — the dialog, the message, the
 * row leaving the list, and the freed copy appearing in the catalog.
 *
 * <p>
 * Each test inserts its own book with a UUID-prefixed title and removes those rows, loans
 * first, in {@link #cleanup()}. Because {@code MyLoansView} lists newest first and alice
 * also holds three seeded loans, each test asserts that row 0 is its own book before
 * acting on it — so a change in ordering fails loudly instead of returning someone else's
 * loan.
 */
@DisplayName("UC-003: Return Book")
class UC003ReturnBookIT extends PlaywrightIT {

	private static final String ALICE = "alice";

	/** Column indices in the My Loans grid (0-based). */
	private static final int TITLE_COL = 0;

	private static final int RETURN_COL = 3;

	/** Column index of the availability cell in the catalog grid. */
	private static final int CATALOG_AVAILABLE_COL = 3;

	@Autowired
	private DSLContext dsl;

	private final Set<Long> testBookIds = new HashSet<>();

	// -------------------------------------------------------------------------
	// Teardown
	// -------------------------------------------------------------------------

	@AfterEach
	void cleanup() {
		if (testBookIds.isEmpty()) {
			return;
		}
		dsl.deleteFrom(LOAN).where(LOAN.BOOK_ID.in(testBookIds)).execute();
		dsl.deleteFrom(BOOK).where(BOOK.ID.in(testBookIds)).execute();
		testBookIds.clear();
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
			.where(APP_USER.USERNAME.eq(ALICE))
			.fetchOne(MEMBER.ID);
		assertThat(id).as("alice must have a member row").isNotNull();
		return id;
	}

	private void insertOpenLoan(long memberId, long bookId) {
		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID).values(memberId, bookId).execute();
	}

	/**
	 * Signs in at the given route. Call once per test: an already-authenticated session
	 * is never shown the login form again, and waiting for it would hang.
	 */
	private GridPw signInAt(String route, String username) {
		page.navigate(url(route));
		logIn(username);
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();
		return new GridPw(page);
	}

	/** Navigates to My Loans in an already-signed-in session. */
	private GridPw openMyLoans() {
		page.navigate(url("my-loans"));
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();
		return new GridPw(page);
	}

	/**
	 * Asserts the newest loan is the one this test created, and returns the grid. The
	 * list is ordered newest first, so a freshly inserted loan is row 0.
	 */
	private GridPw owningFirstRow(GridPw grid, String title) {
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(TITLE_COL)).hasText(title);
		return grid;
	}

	private void clickReturnOnFirstRow(GridPw grid) {
		var returnButton = grid.getRow(0).getCell(RETURN_COL).locator("vaadin-button");
		PlaywrightAssertions.assertThat(returnButton).isVisible();
		returnButton.click();
		Mopo.waitForConnectionToSettle(page);
	}

	private void confirmDialog() {
		page.locator("vaadin-confirm-dialog vaadin-button[slot='confirm-button']").click();
		Mopo.waitForConnectionToSettle(page);
	}

	private void cancelDialog() {
		page.locator("vaadin-confirm-dialog vaadin-button[slot='cancel-button']").click();
		Mopo.waitForConnectionToSettle(page);
	}

	private void searchCatalogFor(String term) {
		page.navigate(url("catalog"));
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();
		page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Search by title or author")).fill(term);
		Mopo.waitForConnectionToSettle(page);
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

	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Returning a book")
	class Returning {

		@Test
		@DisplayName("records the return, confirms it, and the loan leaves the list")
		@UseCase(id = "UC-003")
		void member_can_return_a_borrowed_book() {
			String prefix = UUID.randomUUID().toString();
			String title = prefix + " Test Book";
			long bookId = insertTestBook(prefix, 1);
			insertOpenLoan(aliceMemberId(), bookId);

			GridPw grid = owningFirstRow(signInAt("my-loans", ALICE), title);
			int before = grid.getRenderedRowCount();

			clickReturnOnFirstRow(grid);

			// Step 3: the confirmation names the book being returned.
			PlaywrightAssertions.assertThat(page.locator("vaadin-confirm-dialog-overlay")).isVisible();
			PlaywrightAssertions
				.assertThat(page.getByRole(AriaRole.HEADING)
					.filter(new com.microsoft.playwright.Locator.FilterOptions()
						.setHasText("Return \"" + title + "\"?")))
				.isVisible();

			confirmDialog();

			// Step 6: the member is told the return was recorded.
			PlaywrightAssertions
				.assertThat(page.locator("vaadin-notification-container vaadin-notification-card").first())
				.containsText("returned successfully");

			// Step 7: the loan is no longer among the member's open loans.
			assertThat(new GridPw(page).getRenderedRowCount()).isEqualTo(before - 1);
			PlaywrightAssertions.assertThat(page.getByText(title)).not().isVisible();
		}

		@Test
		@DisplayName("BR-004: the freed copy shows in the catalog")
		@UseCase(id = "UC-003", businessRules = { "BR-004" })
		void returning_frees_a_copy_in_the_catalog() {
			String prefix = UUID.randomUUID().toString();
			String title = prefix + " Test Book";
			long bookId = insertTestBook(prefix, 1);
			insertOpenLoan(aliceMemberId(), bookId);

			// The only copy is out.
			signInAt("catalog", ALICE);
			searchCatalogFor(prefix);
			PlaywrightAssertions.assertThat(new GridPw(page).getRow(0).getCell(CATALOG_AVAILABLE_COL))
				.hasText("0 of 1");

			GridPw loans = owningFirstRow(openMyLoans(), title);
			clickReturnOnFirstRow(loans);
			confirmDialog();

			// Availability is derived on every query, so the next read shows the copy.
			searchCatalogFor(prefix);
			PlaywrightAssertions.assertThat(new GridPw(page).getRow(0).getCell(CATALOG_AVAILABLE_COL))
				.hasText("1 of 1");
		}

	}

	@Nested
	@DisplayName("A3: member abandons the confirmation")
	class Abandoning {

		@Test
		@DisplayName("closes nothing and shows no message")
		@UseCase(id = "UC-003", scenario = "A3: Member Abandons the Confirmation")
		void cancelling_the_confirmation_closes_nothing() {
			String prefix = UUID.randomUUID().toString();
			String title = prefix + " Test Book";
			long bookId = insertTestBook(prefix, 1);
			insertOpenLoan(aliceMemberId(), bookId);

			GridPw grid = owningFirstRow(signInAt("my-loans", ALICE), title);
			int before = grid.getRenderedRowCount();

			clickReturnOnFirstRow(grid);

			// The dialog really is on screen, so the absence of output below is about a
			// cancelled confirmation rather than about a button that did nothing.
			PlaywrightAssertions.assertThat(page.locator("vaadin-confirm-dialog-overlay")).isVisible();

			cancelDialog();

			PlaywrightAssertions.assertThat(page.locator("vaadin-confirm-dialog-overlay")).not().isVisible();
			// No message of either kind: the request was abandoned, not refused.
			assertThat(page.locator("vaadin-notification-container vaadin-notification-card").count()).isZero();
			// The loan is still listed.
			assertThat(new GridPw(page).getRenderedRowCount()).isEqualTo(before);
			PlaywrightAssertions.assertThat(page.getByText(title).first()).isVisible();
		}

	}

}
