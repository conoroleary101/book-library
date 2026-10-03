package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.loan.domain.LoanAlreadyClosedException;
import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.NoMemberProfileException;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Confirmation dialog for UC-003 Return Book.
 * <p>
 * Opened from {@code MyLoansView} when the member clicks Return on one of their loans. It
 * mirrors {@code BorrowDialog}: cancellable, so the member can abandon the request and be
 * told nothing (alternative flow A3), and on confirm it closes the loan and reports the
 * outcome.
 * <p>
 * The already-returned case also refreshes the list, because alternative flow A2
 * continues at step 1 — the row the member clicked is stale and should not stay on
 * screen.
 */
public class ReturnDialog extends ConfirmDialog {

	public ReturnDialog(long loanId, String title, LoanService loanService, Runnable onFinished) {
		setHeader("Return \"" + title + "\"?");
		setText("Return this book and close the loan?");
		setConfirmText("Return");
		setCancelable(true);

		addConfirmListener(event -> {
			try {
				loanService.returnLoan(loanId);
				Notification.show("\"" + title + "\" returned successfully.")
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				onFinished.run();
			}
			catch (LoanAlreadyClosedException ex) {
				Notification.show("\"" + title + "\" has already been returned.")
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
				onFinished.run();
			}
			catch (NoMemberProfileException ex) {
				// Unreachable by construction: the Return action is only ever offered
				// from
				// a loan the account already holds, and an account with no patron profile
				// holds none, so it is never shown this dialog (UC-003 BR-008). Kept so
				// the service's contract is handled rather than escaping as an
				// unexplained
				// error if a future entry point offers the action before ownership is
				// known - which is exactly how UC-002 reaches its A2.
				Notification.show("Returning is not available for your account.")
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}
		});
	}

}
