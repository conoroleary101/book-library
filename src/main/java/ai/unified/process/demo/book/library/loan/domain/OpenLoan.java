package ai.unified.process.demo.book.library.loan.domain;

import java.time.LocalDateTime;

/**
 * One row of the member's open loans as UC-003 Return Book lists them (step 2): the book
 * that is out, and when the copy was taken.
 *
 * <p>
 * This is a projection, not the loan row. {@link Loan} mirrors the loan itself and
 * carries no book details, while step 2 has to show the title and the author — so the
 * list joins the book and maps into this record rather than widening {@link Loan} with
 * fields that do not belong to a loan.
 *
 * @param loanId identifies the loan to close; the book id is deliberately absent, because
 * a return closes one loan rather than "a copy of this book"
 */
public record OpenLoan(Long loanId, String title, String author, LocalDateTime borrowedAt) {
}
