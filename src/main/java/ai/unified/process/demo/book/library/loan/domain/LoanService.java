package ai.unified.process.demo.book.library.loan.domain;

import ai.unified.process.demo.book.library.core.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates the borrow action for UC-002 and the return action for UC-003.
 * <p>
 * This service exists because borrowing spans two entities ({@code member} and
 * {@code loan}) and enforces an availability invariant that must be checked atomically
 * (BR-009). The view must go through this service exclusively and must not call
 * {@link LoanRepository} directly.
 */
@Service
public class LoanService {

	private final LoanRepository loanRepository;

	private final CurrentUser currentUser;

	public LoanService(LoanRepository loanRepository, CurrentUser currentUser) {
		this.loanRepository = loanRepository;
		this.currentUser = currentUser;
	}

	/**
	 * Borrows a copy of the given book for the currently signed-in user (UC-002 main
	 * success scenario).
	 * <p>
	 * The method:
	 * <ol>
	 * <li>Resolves the signed-in {@code app_user.id} via {@link CurrentUser}.</li>
	 * <li>Looks up the corresponding {@code member.id} (BR-010, C-009).</li>
	 * <li>Delegates to {@link LoanRepository#borrowAtomically(long, long)}, which locks
	 * the book row and inserts the loan in one step (BR-009).</li>
	 * </ol>
	 * @param bookId the {@code book.id} to borrow
	 * @throws NoMemberProfileException if the signed-in user has no linked member row
	 * (alternative flow A2, BR-010, C-009)
	 * @throws NoAvailableCopyException if no copy is free at the moment of the request
	 * (alternative flow A1, A3, BR-009)
	 */
	@Transactional
	public void borrow(long bookId) {
		long appUserId = currentUser.requireAppUserId();

		Long memberId = loanRepository.findMemberIdByAppUserId(appUserId);
		if (memberId == null) {
			throw new NoMemberProfileException(appUserId);
		}

		boolean created = loanRepository.borrowAtomically(memberId, bookId);
		if (!created) {
			throw new NoAvailableCopyException(bookId);
		}
	}

	/**
	 * Lists the signed-in member's open loans (UC-003 steps 1 and 2).
	 * <p>
	 * An account with no patron profile simply holds no loans, so it gets an empty list
	 * and lands on alternative flow A1 rather than an error — there is nothing to explain
	 * to someone who has borrowed nothing.
	 * @return the member's open loans, newest first; empty when there are none
	 */
	@Transactional(readOnly = true)
	public List<OpenLoan> findMyOpenLoans() {
		Long memberId = loanRepository.findMemberIdByAppUserId(currentUser.requireAppUserId());
		return memberId == null ? List.of() : loanRepository.findOpenLoansByMemberId(memberId);
	}

	/**
	 * Records the return of one of the signed-in member's loans (UC-003 main success
	 * scenario).
	 * <p>
	 * The loan is closed rather than removed (BR-001), and only a loan belonging to this
	 * member can be closed (BR-002) — the member id is part of the closing statement, not
	 * a check made before it.
	 * @param loanId the loan to close
	 * @throws NoMemberProfileException if the signed-in user has no linked patron profile
	 * @throws LoanAlreadyClosedException if the loan is no longer open to this member
	 * (alternative flows A2 and A4, BR-002)
	 */
	@Transactional
	public void returnLoan(long loanId) {
		long appUserId = currentUser.requireAppUserId();

		Long memberId = loanRepository.findMemberIdByAppUserId(appUserId);
		if (memberId == null) {
			throw new NoMemberProfileException(appUserId);
		}

		boolean closed = loanRepository.closeLoanAtomically(memberId, loanId);
		if (!closed) {
			throw new LoanAlreadyClosedException(loanId);
		}
	}

}
