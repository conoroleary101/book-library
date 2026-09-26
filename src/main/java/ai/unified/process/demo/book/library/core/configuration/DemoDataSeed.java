package ai.unified.process.demo.book.library.core.configuration;

import ai.unified.process.demo.book.library.core.security.SecuritySeed;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static ai.unified.process.demo.book.library.db.Tables.APP_USER;
import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;
import static ai.unified.process.demo.book.library.db.Tables.MEMBER;

/**
 * Seeds a demo catalog so the catalog use case (UC-001 Search Catalog) has something to
 * show on first start.
 * <p>
 * Runs after {@link SecuritySeed}, which is what makes the lookup by username safe: the
 * {@code alice} account already exists by the time this runner fires, so no password is
 * hashed or duplicated here. Flyway cannot do this job, because migrations run before the
 * application context and therefore before any account exists.
 * <p>
 * Idempotent on both halves: the patron profile is inserted only when {@code alice} has
 * none, and the catalog is skipped entirely once any book is present, so repeated boots
 * in dev never duplicate rows.
 * <p>
 * The fixtures are chosen to exercise specific rules of UC-001:
 * <ul>
 * <li>Books are inserted out of alphabetical order, so a list sorted by title ascending
 * visibly differs from insertion order (step 2).</li>
 * <li>Two books carry no ISBN, for BR-005 (empty ISBN cell, still listed).</li>
 * <li>Tolkien and Le Guin each appear twice, so a partial author search returns more than
 * one row and {@code "tolk"} matches {@code "Tolkien"} (BR-001).</li>
 * <li>The Left Hand of Darkness holds one copy and has one open loan, so it shows 0 of 1
 * — the fixture for alternative flow A2.</li>
 * <li>The Hobbit holds three copies and has two open loans, so it shows 1 of 3:
 * availability is a count, not a flag (BR-003).</li>
 * <li>Beloved has a loan that was already returned, so it still shows 2 of 2 — a closed
 * loan is history and never reduces availability (C-018).</li>
 * </ul>
 */
@Component
@Order(DemoDataSeed.ORDER)
public class DemoDataSeed implements ApplicationRunner {

	public static final int ORDER = SecuritySeed.ORDER + 10;

	private static final String DEMO_MEMBER_USERNAME = "alice";

	private final DSLContext dsl;

	public DemoDataSeed(DSLContext dsl) {
		this.dsl = dsl;
	}

	@Override
	public void run(ApplicationArguments args) {
		Long memberId = ensureDemoMember();
		if (memberId == null) {
			return;
		}
		if (dsl.fetchExists(dsl.selectFrom(BOOK))) {
			return;
		}
		dsl.transaction(configuration -> {
			DSLContext tx = configuration.dsl();
			insertCatalog(tx);
			insertLoans(tx, memberId);
		});
	}

	/**
	 * Returns the demo patron's member id, creating the profile if the account has none.
	 * Returns {@code null} when the account itself is missing, which leaves the demo data
	 * unseeded rather than inventing an identity.
	 */
	private @Nullable Long ensureDemoMember() {
		Long userId = dsl.select(APP_USER.ID)
			.from(APP_USER)
			.where(APP_USER.USERNAME.eq(DEMO_MEMBER_USERNAME))
			.fetchOne(APP_USER.ID);
		if (userId == null) {
			return null;
		}

		Long memberId = dsl.select(MEMBER.ID).from(MEMBER).where(MEMBER.USER_ID.eq(userId)).fetchOne(MEMBER.ID);
		if (memberId != null) {
			return memberId;
		}

		return dsl.insertInto(MEMBER, MEMBER.USER_ID, MEMBER.NAME, MEMBER.EMAIL)
			.values(userId, "Alice Nolan", "alice@example.org")
			.returningResult(MEMBER.ID)
			.fetchOne(MEMBER.ID);
	}

	private void insertCatalog(DSLContext tx) {
		tx.insertInto(BOOK, BOOK.TITLE, BOOK.AUTHOR, BOOK.ISBN, BOOK.COPIES)
			.values("The Hobbit", "J.R.R. Tolkien", "9780261102217", 3)
			.values("Zorba the Greek", "Nikos Kazantzakis", null, 1)
			.values("Dubliners", "James Joyce", null, 1)
			.values("A Wizard of Earthsea", "Ursula K. Le Guin", "9780141354910", 2)
			.values("The Silmarillion", "J.R.R. Tolkien", "9780261102736", 2)
			.values("Beloved", "Toni Morrison", "9780099760115", 2)
			.values("An Beal Bocht", "Myles na gCopaleen", "9780956104656", 1)
			.values("Cloud Atlas", "David Mitchell", "9780340822784", 1)
			.values("The Left Hand of Darkness", "Ursula K. Le Guin", "9780441478125", 1)
			.execute();
	}

	private void insertLoans(DSLContext tx, Long memberId) {
		LocalDateTime now = LocalDateTime.now();
		borrow(tx, memberId, "The Left Hand of Darkness", now.minusDays(12), null);
		borrow(tx, memberId, "The Hobbit", now.minusDays(9), null);
		borrow(tx, memberId, "The Hobbit", now.minusDays(4), null);
		borrow(tx, memberId, "Beloved", now.minusDays(30), now.minusDays(21));
	}

	private void borrow(DSLContext tx, Long memberId, String title, LocalDateTime borrowedAt,
			@Nullable LocalDateTime returnedAt) {
		Long bookId = tx.select(BOOK.ID).from(BOOK).where(BOOK.TITLE.eq(title)).fetchOne(BOOK.ID);
		tx.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID, LOAN.BORROWED_AT, LOAN.RETURNED_AT)
			.values(memberId, bookId, borrowedAt, returnedAt)
			.execute();
	}

}
