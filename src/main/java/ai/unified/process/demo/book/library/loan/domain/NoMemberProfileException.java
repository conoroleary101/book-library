package ai.unified.process.demo.book.library.loan.domain;

/**
 * Thrown by {@link LoanService#borrow(long)} when the signed-in user has no linked
 * {@code member} row (alternative flow A2, BR-010, C-009).
 */
public class NoMemberProfileException extends RuntimeException {

	public NoMemberProfileException(long appUserId) {
		super("No member profile linked to app_user.id=" + appUserId);
	}

}
