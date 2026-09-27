package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.NoAvailableCopyException;
import ai.unified.process.demo.book.library.loan.domain.NoMemberProfileException;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Confirmation dialog for UC-002 Borrow Book.
 * <p>
 * Opened from {@code CatalogView} when the member clicks the Borrow button on a row. On
 * confirm it calls {@link LoanService#borrow(long)} and shows a Vaadin notification toast
 * — success or failure (BR-011, alternative flows A1 and A2). After a successful borrow
 * it calls the supplied {@code onSuccess} callback so {@code CatalogView} can refresh its
 * grid without the dialog needing to know about it.
 */
public class BorrowDialog extends ConfirmDialog {

	public BorrowDialog(long bookId, String title, LoanService loanService, Runnable onSuccess) {
		setHeader("Borrow \"" + title + "\"?");
		setText("Borrow this book and record the loan against your account?");
		setConfirmText("Borrow");
		setCancelable(true);

		addConfirmListener(event -> {
			try {
				loanService.borrow(bookId);
				Notification.show("\"" + title + "\" borrowed successfully.")
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				onSuccess.run();
			}
			catch (NoAvailableCopyException ex) {
				Notification.show("No copy of \"" + title + "\" is available right now.")
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}
			catch (NoMemberProfileException ex) {
				Notification.show("Borrowing is not available for your account.")
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}
		});
	}

}
