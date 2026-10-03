package ai.unified.process.demo.book.library.loan.ui;

import ai.unified.process.demo.book.library.loan.domain.LoanService;
import ai.unified.process.demo.book.library.loan.domain.OpenLoan;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

/**
 * UC-003 Return Book.
 *
 * <p>
 * Lists the books the signed-in member currently has on loan and lets them return one.
 * When the list is empty the member is told they have nothing on loan (alternative flow
 * A1); otherwise each row offers a Return action, which opens {@link ReturnDialog}.
 *
 * <p>
 * Open to any signed-in user. A librarian holding a patron profile sees and returns their
 * own loans exactly as a member does, and gains nothing over another patron's loans — the
 * resolved point in the specification, and {@code LoanService} scopes every query and
 * every close by the signed-in member (BR-002).
 *
 * <p>
 * Scope note: steps 1 and 2 of UC-003 need a list of the member's open loans, so this
 * view provides one. That listing is also the subject of UC-004 View My Loans, which is
 * not yet specified. This view is the minimum UC-003 requires; UC-004 should take it over
 * and extend it rather than a second list being built beside it.
 *
 * <p>
 * On a successful return the grid is refreshed, so the returned book leaves the list
 * without a manual reload. The book's freed copy shows in the catalog, whose availability
 * is derived on every query (BR-004) and so is current the next time the catalog is read.
 */
@RolesAllowed({ "MEMBER", "LIBRARIAN" })
@Route("my-loans")
@PageTitle("My Loans")
@Menu(title = "My Loans", order = 2, icon = "vaadin:bookmark")
public class MyLoansView extends VerticalLayout {

	private final transient LoanService loanService;

	private final Grid<OpenLoan> grid = new Grid<>();

	public MyLoansView(LoanService loanService) {
		this.loanService = loanService;
		setSizeFull();
		configureGrid();
		add(grid);
		refresh();
	}

	private void configureGrid() {
		grid.addColumn(OpenLoan::title).setHeader("Title").setAutoWidth(true).setFlexGrow(1);
		grid.addColumn(OpenLoan::author).setHeader("Author").setAutoWidth(true);
		// The date alone: a loan has no due date and no time of day worth showing
		// (BR-006).
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
