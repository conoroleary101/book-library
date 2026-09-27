# Requirements Document

## Introduction

This document captures the requirements for **UC-002 Borrow Book**. The feature enables a signed-in member (or a librarian with a linked member profile) to borrow an available copy of a book, satisfying functional requirement FR-005. The system records the loan, enforces the availability constraint atomically, and immediately reflects the updated copy count in the catalog.

## Glossary

- **Loan**: A record linking a `member.id` to a `book.id`, with a `borrowed_at` timestamp and a `returned_at` timestamp that is null while the copy is still out.
- **Open Loan**: A loan row where `returned_at` is null — i.e., the copy has not yet been returned.
- **Available Copies**: A derived value equal to `book.copies` minus the count of open loans for that book. Never stored as a column.
- **Member**: A patron profile row in the `member` table, linked to an `app_user` via `user_id`. Loans reference `member.id`, not `app_user.id`.
- **Borrowed At**: The `borrowed_at` column on a loan row; set to the current timestamp when the loan is created.
- **Returned At**: The `returned_at` column on a loan row; null while the book is on loan, set to the return timestamp when the book is returned.

## Use Case: Borrow Book

## Overview

**Use Case ID:** UC-002
**Use Case Name:** Borrow Book
**Primary Actor:** Member
**Secondary Actor:** Librarian (may borrow books in the same way as a member)
**Goal:** Borrow an available copy of a book so that the system records the loan against the member's profile
**Functional Requirement:** FR-005
**Status:** Open

**Role Access:** The borrow action is available to both MEMBER and LIBRARIAN roles. A librarian without a linked `member` row is handled by alternative flow A2.

## Preconditions

- Actor is signed in with a member or a librarian account
- The book the actor wants to borrow is visible in the catalog (UC-001)

## Main Success Scenario

1. Member selects a book from the catalog that has at least one copy available.
2. Member initiates the borrow action for that book.
3. System confirms that at least one copy of the book is still available at the moment of the request (BR-009, C-018).
4. System records a new loan linking the member's `member.id` to the book, with `borrowed_at` set to the current timestamp and `returned_at` left null (BR-010, C-009, C-019).
5. System displays confirmation that the loan has been recorded.
6. The book's derived available-copy count decreases by one in the catalog (BR-011, C-018).

## Alternative Flows

### A1: No Copy Is Available at Borrow Time

**Trigger:** The availability check at step 3 finds that all copies are currently on loan (C-018, C-014).

1. System informs the member that no copy is available and the loan cannot be recorded.
2. System does not create a loan record.
3. No reservation or waiting-list entry is created (C-014).
4. Use case ends.

### A2: Actor Has No Member Profile

**Trigger:** The signed-in user has an `app_user` row but no corresponding `member` row, so there is no `member.id` to attach the loan to (C-009).

1. System detects that the signed-in user is not linked to a member record.
2. System informs the actor that borrowing is not available for this account.
3. System does not create a loan record.
4. Use case ends.

> **Note.** A librarian account that has not been linked to a member row hits this flow.
> A librarian who wants to borrow books must have both an `app_user` row with role LIBRARIAN
> and a linked `member` row, as stated in the product rules.

### A3: Concurrent Borrow Exhausts Last Copy

**Trigger:** Two members attempt to borrow the last available copy at the same time; one succeeds while the other's availability check (step 3) now finds zero copies free.

1. The first request succeeds through the main success scenario.
2. The second request fails the availability check and follows A1 from step 1.

> **Note.** The implementation must prevent the open-loan count from exceeding `book.copies`
> (BR-009). Whether this is achieved by an optimistic check, a database constraint, or a
> serialised transaction is a design decision, not a requirement.

## Postconditions

### Success Postconditions

- A new loan row exists with the member's `member.id`, the book's `book_id`, the current timestamp as `borrowed_at`, and `returned_at` as null (C-009, C-019).
- The book's derived available-copy count is one fewer than before the action (C-018).
- No due date is recorded on the loan (C-019).
- No fee or fine is created (C-013).
- The loan record is never hard-deleted and is retained for at least 24 months after it is eventually returned (NFR-014).

### Failure Postconditions

- No loan row is created.
- The book's available-copy count is unchanged.
- The member's existing loans are unchanged.

## Business Rules

### BR-009: Available-Copy Check Is Atomic

The availability check and the loan creation must act as one indivisible step, so that concurrent requests can never together create more open loans than `book.copies`. An open loan is one where `returned_at` is null. Available copies are never stored as a column and must always be derived at query time (C-018).

### BR-010: Loan Ownership Uses Member Identity

A loan row must store `member.id` as its owner reference. It must never store `app_user.id` directly (C-009). The mapping from the signed-in user to their `member.id` is made in the domain layer before the loan is written.

### BR-011: Availability Reflects the New Loan Immediately

Once a loan is recorded, the borrowing actor's own view must reflect the new available-copy count immediately, without requiring a manual page reload (C-018). Other users see the updated count on their next search or manual refresh of the catalog.

### BR-012: No Due Date on the Loan

A loan records `borrowed_at` (the moment the copy was taken out) and `returned_at` (null until the copy is returned). No due date, deadline, or reminder is recorded or displayed (C-019).

### BR-013: No Payments, Fines, or Reservations

The borrow action must not create a fee, fine, or payment record of any kind (C-013). If no copy is available, the member is informed and the use case ends; no reservation or waiting-list entry is created (C-014).

### BR-014: Loan Retention

Loan records must never be removed by any application action. After a loan is closed (returned_at set), the row must be retained for at least 24 months (NFR-014).

### BR-015: A Member May Hold Multiple Open Loans for the Same Book

There is no restriction on a member borrowing a second (or further) copy of a book they already have on loan, as long as a copy is available. Each borrow creates a separate loan row. The availability check (BR-009) applies independently to each request.

### BR-016: No Per-Member Loan Limit for This Version

There is no maximum number of open loans a member may hold at the same time. A member may borrow as many books as copies are available. If a limit is introduced in a future version, a new business rule and acceptance criterion must be added at that time.

## Requirements

### Requirement 1: Borrow an Available Book

**User Story:** FR-005 — As a member, I want to borrow a book that has at least one available copy so that I can take it home and the system records the loan.

#### Acceptance Criteria

1. WHEN a signed-in member selects a book and initiates the borrow action, THE System SHALL verify that the number of open loans for that book is strictly less than `book.copies` before creating a loan (BR-009, C-018).
2. WHEN the availability check passes, THE System SHALL insert a loan row with `member_id` set to the signed-in user's `member.id`, `book_id` set to the selected book, `borrowed_at` set to the current timestamp, and `returned_at` set to null (BR-010, BR-012, C-009, C-019).
3. WHEN the loan is created, THE System SHALL display a confirmation to the actor (FR-005).
4. WHEN the loan is created, THE System SHALL reflect the updated available-copy count in the borrowing actor's own view without requiring a manual page reload (BR-011, C-018). Other users see the updated count on their next search or refresh.
5. IF the number of open loans for the book equals `book.copies` at the moment of the request, THEN THE System SHALL reject the borrow request and inform the member that no copy is currently available (BR-009, C-014).
6. WHEN two requests to borrow the last available copy of a book arrive concurrently, THE System SHALL ensure that exactly one loan is created and the total number of open loans for that book never exceeds `book.copies` (BR-009).
7. IF the signed-in user has no linked `member` row, THEN THE System SHALL reject the borrow request and inform the actor that borrowing is not available for their account (BR-010, C-009).
8. THE System SHALL never record a due date, fee, fine, or reservation in connection with a borrow action (BR-012, BR-013, C-013, C-014, C-019).
9. THE System SHALL never hard-delete a loan record; every loan row must be retained for at least 24 months after `returned_at` is set (BR-014, NFR-014).

## Open Questions

1. **Entry point.** It is not yet decided whether the borrow action is triggered from the catalog view (UC-001) directly — for example, a "Borrow" button on each catalog row — or from a separate book-detail screen. This is a UX/design decision; the requirement is intentionally entry-point-neutral.

2. **Confirmation UX.** The form of the confirmation shown at step 5 of the main success scenario (notification, dialog, redirect, or updated row state) is not specified here and is left to the design phase.
