package ai.unified.process.demo.book.library.loan.domain;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A single borrow event as returned by {@link LoanRepository}.
 *
 * @param id the loan's primary key
 * @param memberId the {@code member.id} of the borrowing patron (never app_user.id)
 * @param bookId the borrowed book
 * @param borrowedAt timestamp when the copy was taken out
 * @param returnedAt null while the copy is still on loan; set when returned (C-019)
 */
public record Loan(Long id, Long memberId, Long bookId, LocalDateTime borrowedAt, @Nullable LocalDateTime returnedAt) {

}
