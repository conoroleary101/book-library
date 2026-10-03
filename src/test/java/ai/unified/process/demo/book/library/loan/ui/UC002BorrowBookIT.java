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
 * Playwright end-to-end tests for UC-002 Borrow Book.
 *
 * <p>
 * Drives the running application through a headless Chromium browser. Signs in as
 * {@code alice} (password {@code alice}), who is seeded by
 * {@link ai.unified.process.demo.book.library.core.configuration.DemoDataSeed} with a
 * linked {@code member} row.
 *
 * <p>
 * Test data isolation: each test inserts its own book with a UUID-prefixed title and
 * removes those rows (loans first, then books) in {@link #cleanup()}.
 */
class UC002BorrowBookIT extends PlaywrightIT {

	private static final String ALICE = "alice";

	/** Column indices in the catalog grid (0-based). */
	private static final int AVAILABLE_COL = 3;

	private static final int BORROW_COL = 4;

	@Autowired
	private DSLContext dsl;

	/** Tracks book IDs created by test methods so {@link #cleanup()} can remove them. */
	private final Set<Long> testBookIds = new HashSet<>();

	// -------------------------------------------------------------------------
	// Teardown
	// -------------------------------------------------------------------------

	@AfterEach
	void cleanup() {
		if (testBookIds.isEmpty()) {
			return;
		}
		// Delete all loans (open and closed) for the test books first (FK constraint),
		// then remove the book rows.
		dsl.deleteFrom(LOAN).where(LOAN.BOOK_ID.in(testBookIds)).execute();
		dsl.deleteFrom(BOOK).where(BOOK.ID.in(testBookIds)).execute();
		testBookIds.clear();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Inserts a test book with a UUID-prefixed title and registers its ID for cleanup.
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
			.where(APP_USER.USERNAME.eq(ALICE))
			.fetchOne(MEMBER.ID);
		assertThat(id).as("alice must have a member row").isNotNull();
		return id;
	}

	/** Inserts an open loan for the given member and book. */
	private void insertOpenLoan(long memberId, long bookId) {
		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID).values(memberId, bookId).execute();
	}

	/** Navigates to the catalog route and logs in as the given user. */
	private GridPw openCatalogAs(String username) {
		page.navigate(url("catalog"));
		logIn(username);
		PlaywrightAssertions.assertThat(page.locator("vaadin-grid")).isVisible();
		return new GridPw(page);
	}

	/** Searches the catalog for the given term and returns the grid. */
	private GridPw searchAs(String username, String term) {
		openCatalogAs(username);
		search(term);
		return new GridPw(page);
	}

	private void search(String term) {
		var field = page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Search by title or author"));
		field.fill(term);
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
	// Tests
	// -------------------------------------------------------------------------

	/**
	 * Main success scenario: alice browses the catalog, clicks Borrow on an available
	 * book, confirms the dialog, and the grid reflects the updated availability without a
	 * page reload (BR-011).
	 */
	@Test
	@UseCase(id = "UC-002", scenario = "Main success scenario", businessRules = { "FR-005", "BR-011" })
	void member_can_borrow_available_book() {
		String prefix = UUID.randomUUID().toString();
		insertTestBook(prefix, 2);

		var grid = searchAs(ALICE, prefix);

		// Initial availability: 2 of 2.
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(AVAILABLE_COL)).hasText("2 of 2");

		// Click the Borrow button in the matching row.
		var borrowButton = grid.getRow(0).getCell(BORROW_COL).locator("vaadin-button");
		PlaywrightAssertions.assertThat(borrowButton).isVisible();
		borrowButton.click();
		Mopo.waitForConnectionToSettle(page);

		// The ConfirmDialog must appear. Vaadin renders the header text inside shadow
		// DOM; getByRole(HEADING) pierces shadow DOM automatically.
		var dialog = page.locator("vaadin-confirm-dialog-overlay");
		PlaywrightAssertions.assertThat(dialog).isVisible();
		String expectedHeader = "Borrow \"" + prefix + " Test Book\"?";
		PlaywrightAssertions
			.assertThat(page.getByRole(AriaRole.HEADING)
				.filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(expectedHeader)))
			.isVisible();

		// Click the confirm ("Borrow") button. The confirm button is a light-DOM child
		// of vaadin-confirm-dialog (not the overlay portal); scope the selector there
		// to avoid ambiguity with any Borrow button still visible in the grid behind.
		page.locator("vaadin-confirm-dialog vaadin-button[slot='confirm-button']").click();
		Mopo.waitForConnectionToSettle(page);

		// A success notification must be visible.
		var notification = page.locator("vaadin-notification-container vaadin-notification-card");
		PlaywrightAssertions.assertThat(notification.first()).containsText("borrowed successfully");

		// BR-011: availability must update in the grid without a page reload.
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(AVAILABLE_COL)).hasText("1 of 2");
	}

	/**
	 * Alternative flow A1: when all copies of a book are on loan, no Borrow button is
	 * rendered in the catalog row (BR-009).
	 */
	@Test
	@UseCase(id = "UC-002", scenario = "A1: No Copy Available", businessRules = { "BR-009" })
	void no_borrow_button_for_unavailable_book() {
		String prefix = UUID.randomUUID().toString();
		long bookId = insertTestBook(prefix, 1);
		insertOpenLoan(aliceMemberId(), bookId);

		var grid = searchAs(ALICE, prefix);

		// The availability cell must show 0 of 1.
		PlaywrightAssertions.assertThat(grid.getRow(0).getCell(AVAILABLE_COL)).hasText("0 of 1");

		// No vaadin-button with text "Borrow" must exist in that row's Borrow cell.
		var borrowCell = grid.getRow(0).getCell(BORROW_COL);
		var borrowButton = borrowCell.locator("vaadin-button");
		assertThat(borrowButton.count()).isZero();
	}

}
