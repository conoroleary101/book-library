# Use Case: Search Catalog

## Overview

**Use Case ID:** UC-001  
**Use Case Name:** Search Catalog  
**Primary Actor:** Member  
**Goal:** Find a book in the library catalog and see how many of its copies are available to borrow  
**Status:** Draft

## Preconditions

- Actor is signed in with a member or a librarian account

## Main Success Scenario

1. Member opens the catalog.
2. System displays the books in the catalog, each with its title, author, total copies held, and copies currently available.
3. Member enters a search term matching a title or an author.
4. System displays only the books whose title or author matches the search term, each with its total copies held and copies currently available.
5. Member identifies the book they were looking for and reads how many of its copies are available to borrow.

## Alternative Flows

### A1: No Book Matches the Search Term

**Trigger:** No book in the catalog matches the search term (step 4)  
**Flow:**

1. System displays an empty result list and a message stating that no book matches the search term.
2. Member corrects or clears the search term.
3. Use case continues at step 3.

### A2: Every Copy Is on Loan

**Trigger:** The book the member wants has no copy available (step 5)  
**Flow:**

1. System shows the book with zero copies available out of its total holdings.
2. Member sees that the book cannot be borrowed at the moment.
3. Use case ends.

### A3: Catalog Is Empty

**Trigger:** The catalog holds no books (step 2)  
**Flow:**

1. System displays an empty catalog and a message stating that no books have been added yet.
2. Use case ends.

### A4: Member Clears the Search Term

**Trigger:** Member empties the search term after a search (step 4)  
**Flow:**

1. System displays the whole catalog again, each book with its total copies held and copies currently available.
2. Use case continues at step 3.

## Postconditions

### Success Postconditions

- Member has seen the books matching the search term, each with its availability
- Catalog, loans, and availability figures are unchanged

### Failure Postconditions

- No matching book is shown to the member
- Member is told that nothing matched the search term, or that the catalog is empty
- Catalog, loans, and availability figures are unchanged

## Business Rules

### BR-001: Search Scope

The search term is matched against the title and the author of a book. No other field takes part in the search.

### BR-002: Availability Is Derived

The number of available copies of a book is its total copies held minus the number of its loans that are still open. It is never recorded separately, so it can never disagree with the loans.

### BR-003: Availability Is Shown Against Total Holdings

Availability is always presented as the number of available copies out of the total copies the library holds, so the member can tell "none of three left" from "the library holds only one".

### BR-004: The Catalog Is Visible to Every Signed-In User

Any signed-in user may search the catalog, whether they hold a member account or a librarian account. The catalog carries no per-user restriction.
