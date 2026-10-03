/**
 * Domain layer for UC-002 Borrow Book and UC-003 Return Book:
 * {@link ai.unified.process.demo.book.library.loan.domain.LoanRepository},
 * {@link ai.unified.process.demo.book.library.loan.domain.LoanService}, and the
 * {@link ai.unified.process.demo.book.library.loan.domain.OpenLoan} projection the return
 * list is built from.
 * <p>
 * {@link ai.unified.process.demo.book.library.loan.domain.Loan} mirrors a loan row and is
 * not used by either use case: borrowing writes without reading one back, and the return
 * list needs the book's title and author, which a loan does not carry. It is kept for a
 * future read path rather than widened with fields that do not belong to a loan — see the
 * open point in {@code docs/use_cases/UC-003-return-book.md}.
 */
@NullMarked
package ai.unified.process.demo.book.library.loan.domain;

import org.jspecify.annotations.NullMarked;
