package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.OpenLoan;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

/**
 * UC-004 View My Loans.
 *
 * <p>
 * Shows each book the signed-in member currently has out, with its title, its author and
 * when the copy was taken, most recently taken first (UC-004 steps 3 and 4, UC-004
 * BR-003). An empty list tells the member they have nothing on loan rather than reading
 * as an error (UC-004 A1).
 *
 * <p>
 * The return action on each row belongs to UC-003 Return Book, not to this use case. This
 * view offers it and refreshes itself afterwards, which is UC-004 A2; everything the
 * return itself does is UC-003's.
 *
 * <p>
 * Open to any signed-in user. A librarian holding a patron profile sees their own loans
 * exactly as a member does and gains nothing over another patron's, because
 * {@code LoanService} scopes the query by the signed-in member (UC-004 BR-001). An
 * account with no patron profile holds no loan and so sees the empty list (UC-004
 * BR-004).
 *
 * <p>
 * The list is read when the view is built and again after a return, and does not update
 * itself while it sits on screen (UC-004 BR-005) — a loan closed elsewhere lingers until
 * the list is opened again (UC-004 A3).
 */
@RolesAllowed({ "MEMBER", "LIBRARIAN" })
@Route("my-loans")
@PageTitle("My Loans")
@Menu(title = "My Loans", order = 2, icon = "vaadin:bookmark")
public class MyLoansView extends VerticalLayout implements BeforeEnterObserver {

	private final transient LoanService loanService;

	private final Grid<OpenLoan> grid = new Grid<>();

	public MyLoansView(LoanService loanService) {
		this.loanService = loanService;
		setSizeFull();
		configureGrid();
		add(grid);
	}

	/**
	 * Reads the loans on every navigation here, not only when the view is first built.
	 * Reading in the constructor alone left the list showing whatever it held when it was
	 * created, so choosing My Loans while already on it kept a loan closed elsewhere on
	 * screen (UC-004 A3, UC-004 BR-005).
	 */
	@Override
	public void beforeEnter(BeforeEnterEvent event) {
		refresh();
	}

	private void configureGrid() {
		grid.addColumn(OpenLoan::title).setHeader("Title").setAutoWidth(true).setFlexGrow(1);
		grid.addColumn(OpenLoan::author).setHeader("Author").setAutoWidth(true);
		// The date alone: a loan has no due date (UC-002 BR-004, C-019) and no time of
		// day worth showing.
		grid.addColumn(loan -> loan.borrowedAt().toLocalDate().toString()).setHeader("Borrowed").setAutoWidth(true);
		configureReturnColumn();
		// A1: the member has nothing out, which is an ordinary state rather than an
		// error.
		grid.setEmptyStateText("You have no books on loan.");
		grid.setSizeFull();
	}

	private void configureReturnColumn() {
		grid.addComponentColumn(loan -> {
			var button = new Button("Return");
			button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
			button.addClickListener(event -> {
				var dialog = new ReturnDialog(loan.loanId(), loan.title(), loanService, this::refresh);
				dialog.open();
			});
			return button;
		}).setHeader("").setAutoWidth(true).setFlexGrow(0);
	}

	private void refresh() {
		List<OpenLoan> loans = loanService.findMyOpenLoans();
		grid.setItems(loans);
	}

}
