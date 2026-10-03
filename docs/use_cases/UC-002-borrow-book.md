# Use Case: Borrow Book

## Overview

**Use Case ID:** UC-002  
**Use Case Name:** Borrow Book  
**Primary Actor:** Member  
**Goal:** Borrow an available copy of a book so that the system records the loan against the member's own patron profile  
**Status:** Tested

> **Derived from `.kiro/specs/borrow-book/` and verified against the implementation.**
> Business rule identifiers are scoped to this use case and restart at BR-001, as the AI
> Unified Process requires; the original Kiro numbering is noted on each rule so the
> mapping back to that document is not lost. A librarian borrows exactly as a member does,
> through the patron profile linked to their account.
>
> Every step, alternative flow, postcondition and business rule has an asserting test,
> with the exceptions recorded under Open Points below.

## Preconditions

- Actor is signed in with a member or a librarian account
- The book the actor wants to borrow is visible in the catalog (UC-001)

## Main Success Scenario

1. Member finds a book in the catalog that has at least one copy available.
2. Member starts the borrow action from that book's row and confirms the request.
3. System identifies the patron profile the loan will belong to.
4. System confirms that at least one copy of the book is still available at that moment.
5. System records the loan against that patron profile, noting when the copy was taken out and leaving the loan open.
6. System confirms to the member that the loan has been recorded.
7. System shows the book's available-copy count reduced by one.

## Alternative Flows

### A1: No Copy Is Available at Borrow Time

**Trigger:** Every copy of the book is already on loan when availability is confirmed (step 4)  
**Flow:**

1. System tells the member that no copy of the book is available at the moment.
2. System records no loan, and creates no reservation or waiting list entry.
3. Use case ends.

### A2: Actor Has No Patron Profile

**Trigger:** The signed-in account has no patron profile for the loan to belong to (step 3)  
**Flow:**

1. System tells the actor that borrowing is not available for their account.
2. System records no loan.
3. Use case ends.

> A librarian account that has never been linked to a patron profile reaches this flow. A
> librarian who wants to borrow holds a patron profile alongside their librarian account,
> exactly like any other patron.

### A3: Concurrent Borrow Takes the Last Copy

**Trigger:** Another member's borrow of the same book is recorded between this member starting the action and availability being confirmed (step 4)  
**Flow:**

1. System records the loan for whichever request confirmed availability first.
2. System finds no copy left for the later request and tells that member so.
3. System records no loan for the later request, leaving the total number of open loans no greater than the copies held.
4. Use case ends.

### A4: Member Abandons the Confirmation

**Trigger:** Member cancels the confirmation instead of confirming it (step 2)  
**Flow:**

1. System closes the confirmation and takes no further action.
2. System records no loan and shows the member no message, the request having been abandoned rather than refused.
3. Use case ends.

## Postconditions

### Success Postconditions

- A loan is recorded against the borrowing member's patron profile, open and with no return recorded
- The book's available-copy count is one fewer than before the action
- No due date, fee, or fine is recorded in connection with the loan
- The member's view shows the reduced availability without a manual reload

### Failure Postconditions

- No loan is recorded
- The book's available-copy count is unchanged
- The member's existing loans are unchanged
- The actor is told why the book could not be borrowed, unless they abandoned the request themselves (A4)

## Business Rules

### BR-001: The Available-Copy Check Is Indivisible

*Kiro BR-009.* Confirming availability and recording the loan act as one indivisible step, so concurrent requests can never together produce more open loans than the library holds copies. A loan counts as open until a return is recorded. Available copies are never stored and are always derived when read (C-018).

### BR-002: A Loan Belongs to a Patron Profile

*Kiro BR-010.* A loan is owned by the borrower's patron profile, never by their sign-in account directly (C-009). The step from signed-in user to patron profile is taken before the loan is recorded, and an account without a profile cannot borrow (A2).

### BR-003: Availability Reflects the New Loan Immediately

*Kiro BR-011.* Once a loan is recorded, the borrowing member's own view shows the new available-copy count without a manual reload (C-018). Other members see the change on their next search or refresh of the catalog.

### BR-004: A Loan Carries No Due Date

*Kiro BR-012.* A loan records when the copy was taken out and, later, when it came back. No due date, deadline, or reminder is recorded or displayed (C-019).

### BR-005: No Payments, Fines, or Reservations

*Kiro BR-013.* Borrowing never creates a fee, fine, or payment of any kind (C-013). When no copy is available the member is told and the use case ends; no reservation or waiting list entry is created (C-014).

### BR-006: Loans Are Retained

*Kiro BR-014.* No application action removes a loan. Once a loan is closed it is retained for at least 24 months (NFR-014).

### BR-007: A Member May Hold Several Copies of the Same Book

*Kiro BR-015.* Nothing stops a member borrowing a further copy of a book they already have on loan, as long as a copy is available. Each borrow is recorded as its own loan, and BR-001 applies to each request independently.

### BR-008: No Per-Member Loan Limit in This Version

*Kiro BR-016.* There is no maximum number of open loans a member may hold at one time; a member may borrow as many books as there are copies available. Introducing a limit later requires a new rule and a new acceptance criterion.

### BR-009: The Borrow Action Is Offered Only When a Copy Is Free

*No Kiro equivalent — recorded from the implementation.* A book with no copy available offers no borrow action on its catalog row, so A1 is normally prevented rather than reported. A1 remains reachable when the last copy is taken between the catalog being drawn and the request arriving, which is A3.

## Open Points

1. **Post-F-3 is asserted narrowly.** The rejected-borrow test checks that the open-loan
   count for the book in question is unchanged, but the postcondition is about the
   member's loans as a whole. No code path writes another book's loans, so this is a
   narrow assertion rather than a missing guarantee.
2. **The fee and fine half of Post-S-3 is schema-guaranteed, not asserted.** No loan
   column and no entity exists that could hold a fee or a fine, so there is nothing for a
   test to observe. The no-due-date half is asserted.
3. **A request for a book that no longer exists is reported as "no copy available."** The
   message misstates the cause, and no flow of this use case describes a vanished book.
   It cannot arise today, because nothing deletes books — it becomes a live defect as soon
   as UC-006 Manage Catalog adds deletion, and should be resolved as part of that work.
4. **The `Loan` record in `loan/domain` is unused.** Nothing returns or reads it, although
   the package documentation presents it as part of the domain layer. Either a read path
   was dropped or the record is dead code; the package documentation is inaccurate either
   way.
