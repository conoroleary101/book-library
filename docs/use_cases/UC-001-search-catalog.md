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
2. System displays the books in the catalog, sorted by title in ascending order, each with its title, author, ISBN, total copies held, and copies currently available.
3. Member enters a search term matching a title or an author.
4. System displays only the books whose title or author matches the search term, sorted by title in ascending order, each with its title, author, ISBN, total copies held, and copies currently available.
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

1. System displays the whole catalog again, in the same order and with the same columns as in step 2.
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

The search term is matched against the title and the author of a book, ignoring case and matching any part of the value, so "tolk" finds "Tolkien". No other field takes part in the search: the ISBN shown in the results is displayed only, not searched.

### BR-002: Availability Is Derived

The number of available copies of a book is its total copies held minus the number of its loans that are still open. It is never recorded separately, so it can never disagree with the loans.

### BR-003: Availability Is Shown Against Total Holdings

Availability is always presented as the number of available copies out of the total copies the library holds, so the member can tell "none of three left" from "the library holds only one".

### BR-004: The Catalog Is Visible to Every Signed-In User

Any signed-in user may search the catalog, whether they hold a member account or a librarian account. The catalog carries no per-user restriction.

### BR-005: A Book May Have No ISBN

The ISBN of a book is optional. A book recorded without one shows an empty ISBN cell in the catalog and in the search results. It is never shown with a placeholder value, and a missing ISBN never keeps the book out of a result it otherwise matches.

### BR-006: Titles Are Compared Case-Insensitively

Titles are ordered using the database's default collation, which compares them without regard to case, so "the hobbit" and "The Hobbit" take the same position. That collation also disregards spaces and punctuation while comparing, so "An Beal Bocht" sorts before "A Wizard of Earthsea" — the two are compared as "anbealbocht" against "awizardofearthsea". The catalog listing and every search result use this same ordering.

### BR-007: Titles Sort Exactly as Written

A title is ordered on its full text, leading article included. "The Hobbit" sorts under T, not under H. No article is stripped before comparing, and the catalog holds no separate sort title alongside the displayed one.
