package ai.unified.process.demo.book.library.loan.domain;

import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static ai.unified.process.demo.book.library.db.Tables.BOOK;
import static ai.unified.process.demo.book.library.db.Tables.LOAN;
import static ai.unified.process.demo.book.library.db.Tables.MEMBER;

/**
 * Data-access methods for UC-002 Borrow Book.
 * <p>
 * {@link #borrowAtomically(long, long)} is the core method: it locks the book row,
 * re-checks availability, and inserts the loan row in one transaction step (BR-009). The
 * caller ({@link LoanService}) must invoke it inside a Spring {@code @Transactional}
 * method so the lock is released on commit.
 */
@Repository
public class LoanRepository {

	private final DSLContext dsl;

	public LoanRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	/**
	 * Returns the {@code member.id} linked to the given {@code app_user.id}, or
	 * {@code null} when the user has no member row (alternative flow A2, BR-010).
	 * @param appUserId the {@code app_user.id} of the signed-in user
	 * @return the patron's {@code member.id}, or {@code null}
	 */
	public @Nullable Long findMemberIdByAppUserId(long appUserId) {
		return dsl.select(MEMBER.ID).from(MEMBER).where(MEMBER.USER_ID.eq(appUserId)).fetchOne(MEMBER.ID);
	}

	/**
	 * Locks the book row, re-checks that at least one copy is available, and inserts a
	 * new loan row — all as one atomic step inside the caller's transaction (BR-009).
	 * <p>
	 * Returns {@code true} when the loan was created, {@code false} when the book was
	 * fully on loan at the moment of the lock (alternative flow A1). The caller
	 * translates {@code false} into a {@link NoAvailableCopyException}.
	 * <p>
	 * SQL outline:
	 *
	 * <pre>{@code
	 * SELECT copies FROM book WHERE id = :bookId FOR UPDATE;
	 * -- then in Java: if (copies - openLoans > 0) INSERT INTO loan ...
	 * }</pre>
	 * @param memberId the patron's {@code member.id}
	 * @param bookId the book to borrow
	 * @return {@code true} if the loan was inserted; {@code false} if no copy was free
	 */
	public boolean borrowAtomically(long memberId, long bookId) {
		// Lock the book row to serialise concurrent borrows (BR-009).
		Integer copies = dsl.select(BOOK.COPIES).from(BOOK).where(BOOK.ID.eq(bookId)).forUpdate().fetchOne(BOOK.COPIES);

		if (copies == null) {
			return false;
		}

		int openLoans = dsl.fetchCount(LOAN, LOAN.BOOK_ID.eq(bookId).and(LOAN.RETURNED_AT.isNull()));

		if (openLoans >= copies) {
			return false;
		}

		dsl.insertInto(LOAN, LOAN.MEMBER_ID, LOAN.BOOK_ID).values(memberId, bookId).execute();

		return true;
	}

	/**
	 * Lists the member's open loans, newest first, for UC-003 step 2.
	 * @param memberId the patron whose loans to list
	 * @return the open loans with the book each one is for; empty when the member has
	 * none (alternative flow A1)
	 */
	public List<OpenLoan> findOpenLoansByMemberId(long memberId) {
		return dsl.select(LOAN.ID, BOOK.TITLE, BOOK.AUTHOR, LOAN.BORROWED_AT)
			.from(LOAN)
			.join(BOOK)
			.on(BOOK.ID.eq(LOAN.BOOK_ID))
			.where(LOAN.MEMBER_ID.eq(memberId).and(LOAN.RETURNED_AT.isNull()))
			.orderBy(LOAN.BORROWED_AT.desc())
			.fetch(Records.mapping(OpenLoan::new));
	}

	/**
	 * Records the return of one loan, in one indivisible step (UC-003 BR-005).
	 * <p>
	 * The conditions that the loan is still open and belongs to this member are part of
	 * the same statement that sets the return, so two concurrent returns of one loan
	 * close it once: the first matches the row, the second finds nothing left to match
	 * (alternative flow A4). The loan is updated, never removed (BR-001).
	 * @param memberId the patron closing the loan; a loan belonging to anyone else is
	 * left untouched (BR-002)
	 * @param loanId the loan to close
	 * @return {@code true} when the loan was closed; {@code false} when it was already
	 * closed, belongs to another patron, or does not exist
	 */
	public boolean closeLoanAtomically(long memberId, long loanId) {
		int updated = dsl.update(LOAN)
			.set(LOAN.RETURNED_AT, DSL.currentLocalDateTime())
			.where(LOAN.ID.eq(loanId).and(LOAN.MEMBER_ID.eq(memberId)).and(LOAN.RETURNED_AT.isNull()))
			.execute();
		return updated == 1;
	}

}
