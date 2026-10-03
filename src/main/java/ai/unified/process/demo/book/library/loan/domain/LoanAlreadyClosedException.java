package ai.unified.process.demo.book.library.loan.domain;

/**
 * Thrown by {@link LoanService#returnLoan(long)} when the loan could not be closed
 * because it is no longer open to this member (UC-003 alternative flow A2 and A4).
 *
 * <p>
 * It also covers a loan that belongs to another patron (UC-003 BR-002). The two are not
 * distinguished deliberately: a member is never shown another patron's loan, so a request
 * for one is not an ordinary mistake, and telling the two apart in the message would
 * confirm that someone else's loan exists.
 */
public class LoanAlreadyClosedException extends RuntimeException {

	public LoanAlreadyClosedException(long loanId) {
		super("Loan is not open for this member: loan.id=" + loanId);
	}

}
