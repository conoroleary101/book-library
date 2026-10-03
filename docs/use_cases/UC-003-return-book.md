# Use Case: Return Book

## Overview

**Use Case ID:** UC-003  
**Use Case Name:** Return Book  
**Primary Actor:** Member  
**Goal:** Return a borrowed book so that the loan is closed and the copy becomes available to other members  
**Status:** Tested

> **Tested.** Every step, alternative flow, postcondition and business rule is exercised by
> the browserless suite, with the main path, BR-004, A3 and BR-008 additionally driven
> through a browser. The only units no test can observe are BR-006 and Post-S-5, recorded
> under Open Points because no fee column and no fee type exists to assert against.
> Business rule identifiers restart at BR-001, as the AI Unified Process requires; a rule
> of another use case is referenced by its qualified id.

## Preconditions

- Actor is signed in with a member or a librarian account
- Actor has a patron profile, so loans can belong to them (UC-002 BR-002)
- Member is viewing their open loans (UC-004 View My Loans)

## Main Success Scenario

1. Member starts the return action for one of their open loans and confirms the request.
2. System confirms that the loan is still open and belongs to the member.
3. System closes the loan, recording when the copy came back and keeping the loan as history.
4. System confirms to the member that the return has been recorded.
5. System no longer lists the book among the member's open loans, the loan now counting as history, and the freed copy is reflected in the catalog from that moment on.

## Alternative Flows

### A1: Member Has Nothing to Return

**Trigger:** The member holds no open loan, so there is no return to start (step 1)  
**Flow:**

1. System offers no return action, there being no loan to offer one on.
2. Member sees there is nothing to return.
3. Use case ends.

### A2: The Loan Has Already Been Closed

**Trigger:** The loan was returned after the list was drawn and before the request arrived (step 2)  
**Flow:**

1. System tells the member that the book has already been returned.
2. System closes no loan a second time and leaves the recorded return untouched.
3. System redraws the member's open loans (UC-004 View My Loans), without the entry that is no longer open.
4. Use case ends.

### A3: Member Abandons the Confirmation

**Trigger:** Member cancels the confirmation instead of confirming it (step 1)  
**Flow:**

1. System closes the confirmation and takes no further action.
2. System closes no loan and shows the member no message, the request having been abandoned rather than refused.
3. Use case ends.

### A4: Concurrent Return of the Same Loan

**Trigger:** The same loan is returned twice at once, from two screens or two devices (step 2)  
**Flow:**

1. System closes the loan for whichever request found it open first.
2. System finds the loan already closed for the later request and tells that member so.
3. System leaves the first recorded return untouched and frees exactly one copy, never two.
4. Use case ends.

## Postconditions

### Success Postconditions

- The loan is closed, with the moment the copy came back recorded against it
- The loan is still stored, as history rather than as an open loan
- The book's available-copy count is one greater than before the action
- The book no longer appears among the member's open loans
- No fee or fine is recorded

### Failure Postconditions

- No loan is closed, and no return already recorded is altered
- The book's available-copy count is unchanged
- The member's open loans are unchanged
- The actor is told why the return could not be recorded, unless they abandoned the request themselves (A3)

## Business Rules

### BR-001: A Return Closes a Loan, It Never Removes One

Returning a book marks the loan as closed by recording when the copy came back. The loan itself is kept, and no application action removes it (NFR-014, UC-002 BR-006). A library's record of who borrowed what survives the book coming back.

### BR-002: A Member Returns Only Their Own Open Loans

A member may close only a loan belonging to their own patron profile. A loan belonging to another patron is neither shown to them nor closable by them (C-009, UC-002 BR-002).

### BR-003: The Return Follows the Borrow

The moment a copy comes back is always later than the moment it was taken out. A return cannot be recorded as having happened before its own loan began.

### BR-004: Returning Frees a Copy Immediately

The copy becomes available the instant the loan closes. Availability is derived from the open loans on every query rather than stored (C-018), so any read of the catalog after the return shows the freed copy, whoever makes it and whenever they make it. No screen is required to refresh itself, and no member is privileged over another in seeing the change.

### BR-005: Closing a Loan Is Indivisible

Confirming that a loan is open and closing it act as one indivisible step, so two concurrent returns of the same loan close it once and free exactly one copy. The second request finds the loan already closed and follows A4.

### BR-006: A Return Carries No Fee or Fine

Returning a book never creates a fee, fine, or payment of any kind (C-013). Because a loan carries no due date (C-019, UC-002 BR-004), no return is ever late and nothing is owed however long the copy was out.

### BR-007: A Closed Loan Remains Visible as History

Once closed, a loan stops being an open loan but remains part of the loan history, with both the moment it was taken out and the moment it came back, so a librarian can trace who borrowed a book and when (FR-009).


### BR-008: No Return Action Is Offered Without a Patron Profile

An account with no patron profile is never shown a return action, because it holds no loan for one to be offered on (UC-004 BR-004).

This is deliberately not an alternative flow, which is where UC-002 puts the same state (UC-002 A2). The difference is where the action sits: borrowing is offered from the catalog, which every signed-in account can reach, so the request is made before ownership is known and has to be refused. Returning is offered only from a loan the account already holds, so an account with no profile is never shown the action and has nothing to be refused.

Should a future entry point ever offer the return before ownership is known, the request is refused and the actor told that returning is not available for their account — the same answer UC-002 gives in its A2. No member reaches that message today, and no flow of this use case produces it.
## Open Points

1. **The `Loan` record added for UC-002 is currently unused by any production code**, and
   this use case is its likely first consumer: closing a loan means reading one, and the
   record already carries exactly the fields these steps need. The implementation should
   either use it or say why a different type is warranted — defining a second type with
   the same meaning, and leaving the first one dead, is the outcome to avoid.
2. **BR-006 and Post-S-5 are schema-guaranteed, not asserted.** No loan column and no
   entity exists that could hold a fee or a fine, so there is nothing for a test to
   observe. UC-002 records the same situation for the same rule in its own Open Points.
3. **The confirmation at step 1 mirrors UC-002.** Borrowing asks the member to confirm, so
   returning does too, and A3 exists for the same reason UC-002's A4 does. If the borrow
   confirmation is ever dropped, this should follow it.
4. **There is no TC-003 test case document, deliberately.** Writing test cases is not part
   of the nine-step chain this project follows, and neither UC-001 nor UC-002 has one
   either, so UC-003 is not the exception. A journey document would also be the wrong
   shape for a single use case: borrowing and returning only make sense end to end, so the
   natural test case spans UC-001, UC-002 and UC-003 together rather than this one alone.
   Worth writing when that journey is wanted, not to fill a per-use-case slot.

## Resolved Points

1. **A librarian does not record returns on another patron's behalf — out of scope.**
   `docs/vision.md` grants a librarian exactly one borrowing power: "Let a librarian borrow
   books like any other member." That makes a librarian with a patron profile an ordinary
   patron for loans, closing their own and gaining nothing over anyone else's, which is
   what BR-002 says. In a physical library the librarian is the person who receives the
   book, so this will likely be revisited; granting it would need a new functional
   requirement, a change to BR-002, and an alternative flow for choosing whose loan is
   being closed.
2. **The list this use case acts on belongs to UC-004 View My Loans.** This specification
   used to restate it as steps 1 and 2, written before UC-004 existed. UC-004 now
   specifies the list — its columns, its ordering, and what an empty one shows — and
   this use case begins once the member is looking at it, which is why that is a
   precondition rather than a step. The shape UC-004 settled on is the one these steps
   already assumed, so nothing changed here except the duplication going away.
