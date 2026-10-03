# Use Case: View My Loans

## Overview

**Use Case ID:** UC-004  
**Use Case Name:** View My Loans  
**Primary Actor:** Member  
**Goal:** See which books are currently out on loan, so the member knows what is still to be returned  
**Status:** Implemented

> **The list exists already, built as part of UC-003 Return Book.** UC-003 needs this list
> to offer the return action, so it was implemented first and its steps 1 and 2 describe
> the same screen. This specification is what those steps defer to. The shape it settles
> on — title, author, when the copy was taken out, newest first — is the one already built,
> so UC-003 needs no change. The tests that exercise the list are currently attributed to
> UC-003; none names UC-004.

## Preconditions

- Actor is signed in with a member or a librarian account

## Main Success Scenario

1. Member opens their loans.
2. System identifies the patron profile the loans belong to.
3. System shows every book the member currently has out, each with its title, its author, and when the copy was taken, the most recently taken first.
4. Member reads the list and knows which books are still to be returned.

## Alternative Flows

### A1: Member Has Nothing Out

**Trigger:** The member holds no open loan (step 3)  
**Flow:**

1. System shows an empty list and states that the member has no books on loan.
2. Member sees there is nothing to return.
3. Use case ends.

### A2: Member Returns a Book From the List

**Trigger:** Member decides to return one of the listed books (step 4)  
**Flow:**

1. Member starts the return for that book.
2. System carries out the return as UC-003 Return Book describes, and the book leaves the list.
3. Use case ends.

### A3: The List Has Gone Out of Date

**Trigger:** A loan was closed elsewhere after the list was drawn, so the list still shows it (step 4)  
**Flow:**

1. Member opens the list again.
2. System shows the loans as they stand at that moment, without the closed one.
3. Use case ends.

## Postconditions

### Success Postconditions

- Member has seen every book they currently have out, each with when the copy was taken
- Loans, availability, and the catalog are unchanged, the list being a read

### Failure Postconditions

- No loan information is shown to the member
- Loans, availability, and the catalog are unchanged
- A member holding nothing is told so, rather than shown an error

## Business Rules

### BR-001: A Member Sees Only Their Own Loans

The list contains the loans belonging to the signed-in member's patron profile and no one else's. Another patron's loan is never shown, whatever role the viewer holds (C-009, UC-003 BR-002).

### BR-002: Only Open Loans Are Listed

A loan that has been returned leaves the list, because the list answers "what do I still have out". The returned loan is not deleted — it remains part of the loan history (UC-003 BR-001, UC-003 BR-007) — it is simply not what this screen is for.

### BR-003: The Most Recently Taken Book Comes First

Loans are ordered by when the copy was taken out, newest first, so the book a member borrowed most recently is the one they see first.

### BR-004: An Account Without a Patron Profile Holds No Loans

An account with no patron profile owns no loan, so its list is empty and it reaches A1 by the ordinary route rather than by a failure of its own (UC-003 BR-008).

### BR-005: The List Is Read When It Is Opened

The list shows the loans as they stood when it was drawn, and refreshes after an action taken on it. It does not update itself while it sits on screen, which is why a loan closed elsewhere can linger until the list is opened again (A3).

## Open Points

1. **A member cannot see their own past loans.** The list is deliberately limited to open
   loans (BR-002), and FR-009 gives loan history to the librarian only. Whether a member
   should see their own returned loans is not a question any requirement answers; it would
   need a new functional requirement rather than a change to this use case.
2. **The list carries no availability or due information.** A loan has no due date (C-019,
   UC-003 BR-004), so there is nothing to warn about, and availability belongs to the
   catalog. If a count is ever wanted on this screen, it would be a change to BR-003 of
   UC-003 as much as to this use case.
3. **No test names UC-004.** The list is exercised by the UC-003 suite, which annotates
   those tests against UC-003's steps 1 and 2. The behaviour is covered; the trace is not.
   Attributing them — or adding a test class for this use case — is what the status needs
   before it can move past Implemented.
