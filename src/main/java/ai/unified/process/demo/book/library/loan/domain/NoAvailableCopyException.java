package ai.unified.process.demo.book.library.loan.domain;

/**
 * Thrown by {@link LoanService#borrow(long)} when all copies of a book are on loan at the
 * moment of the request (alternative flow A1, BR-009, C-014).
 */
public class NoAvailableCopyException extends RuntimeException {

	public NoAvailableCopyException(long bookId) {
		super("No available copy for book.id=" + bookId);
	}

}
