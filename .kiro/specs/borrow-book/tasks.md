# Implementation Plan: UC-002 Borrow Book

## Overview

Implement the borrow-book feature by adding a `loan` package with a domain layer
(`Loan`, `LoanRepository`, `LoanService`, two exception types) and a UI layer
(`BorrowDialog`), then wiring a Borrow button column into the existing `CatalogView`.
Atomicity is enforced via `SELECT FOR UPDATE` inside a `@Transactional` service method.
No new Flyway migration is required — the `loan` table already exists at V004.

---

## Tasks

- [x] 1. Create the `loan/domain` package skeleton
  - [x] 1.1 Create `loan/domain/package-info.java`
    - Annotate with `@NullMarked` from `org.jspecify.annotations`
    - Package: `ai.unified.process.demo.book.library.loan.domain`
    - _Requirements: 1.2 (identity separation — loans belong to the domain layer)_
  - [x] 1.2 Create `loan/ui/package-info.java`
    - Annotate with `@NullMarked` from `org.jspecify.annotations`
    - Package: `ai.unified.process.demo.book.library.loan.ui`
    - _Requirements: 1.2_
  - [x] 1.3 Create `Loan.java` domain record
    - Fields: `Long id`, `Long memberId`, `Long bookId`, `LocalDateTime borrowedAt`, `@Nullable LocalDateTime returnedAt`
    - No jOOQ or Vaadin types in the record — plain Java only
    - _Requirements: 1.2, 1.8 (no due date field)_

- [ ] 2. Create the domain exception types
  - [ ] 2.1 Create `NoMemberProfileException.java`
    - Extends `RuntimeException`
    - Constructor: `NoMemberProfileException(long appUserId)` — message: `"No member profile linked to app_user.id=" + appUserId`
    - _Requirements: 1.7 (A2 — no member row)_
  - [ ] 2.2 Create `NoAvailableCopyException.java`
    - Extends `RuntimeException`
    - Constructor: `NoAvailableCopyException(long bookId)` — message: `"No available copy for book.id=" + bookId`
    - _Requirements: 1.5 (A1 — all copies on loan)_

- [ ] 3. Implement `LoanRepository`
  - [ ] 3.1 Create `LoanRepository.java` annotated with `@Repository`
    - Constructor-inject `DSLContext dsl`
    - Implement `findMemberIdByAppUserId(long appUserId)` — returns `@Nullable Long`
      - `SELECT MEMBER.ID FROM MEMBER WHERE MEMBER.USER_ID = appUserId`
      - Returns `null` when no member row exists (triggers A2 in the service)
    - Implement `borrowAtomically(long memberId, long bookId)` — returns `boolean`
      - Lock the book row: `SELECT BOOK.COPIES FROM BOOK WHERE BOOK.ID = bookId FOR UPDATE`
      - Count open loans: `dsl.fetchCount(LOAN, LOAN.BOOK_ID.eq(bookId).and(LOAN.RETURNED_AT.isNull()))`
      - If `openLoans >= copies` return `false` (no copy free)
      - Otherwise `INSERT INTO LOAN (MEMBER_ID, BOOK_ID) VALUES (memberId, bookId)` and return `true`
      - `borrowed_at` defaults to `CURRENT_TIMESTAMP`; `returned_at` is omitted (defaults to null)
    - Use static imports from `ai.unified.process.demo.book.library.db.Tables`
    - Add Javadoc referencing UC-002, BR-009, BR-010
    - _Requirements: 1.1, 1.2, 1.5, 1.6 (BR-009 atomic check), 1.7 (BR-010 member identity)_

- [ ] 4. Implement `LoanService`
  - [ ] 4.1 Create `LoanService.java` annotated with `@Service`
    - Constructor-inject `LoanRepository loanRepository` and `CurrentUser currentUser`
    - Implement `@Transactional void borrow(long bookId)`
      - Call `currentUser.requireAppUserId()` to get the signed-in user's `app_user.id`
      - Call `loanRepository.findMemberIdByAppUserId(appUserId)`; if `null` throw `NoMemberProfileException`
      - Call `loanRepository.borrowAtomically(memberId, bookId)`; if `false` throw `NoAvailableCopyException`
    - The `@Transactional` boundary ensures `SELECT FOR UPDATE` and `INSERT` are in the same database transaction (BR-009)
    - The service must be the **sole** path from UI to `LoanRepository` — the view must never call the repository directly
    - Add Javadoc referencing UC-002, BR-009, BR-010, C-009
    - _Requirements: 1.1, 1.2, 1.5, 1.6, 1.7_

- [ ] 5. Checkpoint — domain layer complete
  - Run `.\mvnw spring-javaformat:apply` to auto-format all new files (formatter check runs in the `validate` phase, before compile)
  - Run `.\mvnw compile` to confirm no build errors
  - Fix any Error Prone / NullAway warnings before proceeding (the build is `failOnWarning=true`)

- [ ] 6. Implement `BorrowDialog`
  - [ ] 6.1 Create `BorrowDialog.java` in `loan/ui`
    - Extends `com.vaadin.flow.component.confirmdialog.ConfirmDialog`
    - Constructor: `BorrowDialog(long bookId, String title, LoanService loanService, Runnable onSuccess)`
    - Dialog header: `"Borrow \"" + title + "\"?"`
    - Dialog text: `"Borrow this book and record the loan against your account?"`
    - Confirm button label: `"Borrow"`; `setCancelable(true)`
    - On confirm: call `loanService.borrow(bookId)`
      - Success: `Notification.show("\"" + title + "\" borrowed successfully.")` with `NotificationVariant.LUMO_SUCCESS`; call `onSuccess.run()`
      - `NoAvailableCopyException`: `Notification.show("No copy of \"" + title + "\" is available right now.")` with `NotificationVariant.LUMO_ERROR`
      - `NoMemberProfileException`: `Notification.show("Borrowing is not available for your account.")` with `NotificationVariant.LUMO_ERROR`
    - Do **not** catch any other exception — let it propagate to Vaadin's error boundary
    - _Requirements: 1.3 (confirmation), 1.4 (BR-011 onSuccess triggers grid refresh), 1.5 (A1 error), 1.7 (A2 error)_

- [ ] 7. Update `CatalogView` to add the Borrow button column
  - [ ] 7.1 Modify `CatalogView.java`
    - Add `private final transient LoanService loanService;` field
    - Update the constructor signature to `CatalogView(BookRepository bookRepository, LoanService loanService)`
    - Store `this.loanService = loanService` in the constructor body
    - Call `configureBorrowColumn()` inside the constructor after `configureGrid()`
    - Add `configureBorrowColumn()` private method:
      - `grid.addComponentColumn(book -> { ... })` with header `""`, `setAutoWidth(true)`, `setFlexGrow(0)`
      - When `book.availableCopies() <= 0`, return `new Span()` (empty cell — no button)
      - Otherwise return a `Button("Borrow")` with `ButtonVariant.LUMO_PRIMARY` and `ButtonVariant.LUMO_SMALL`
      - Click listener: `new BorrowDialog(book.id(), book.title(), loanService, this::refresh).open()`
    - `this::refresh` is already `private` — it works as a method reference inside the class
    - Import `com.vaadin.flow.component.button.Button`, `com.vaadin.flow.component.button.ButtonVariant`, and `ai.unified.process.demo.book.library.loan.ui.BorrowDialog`
    - _Requirements: 1.1 (Borrow button entry point), 1.3, 1.4 (BR-011 refresh after borrow)_

- [ ] 8. Checkpoint — full feature compile and smoke
  - Run `.\mvnw spring-javaformat:apply` to auto-format all new and modified files (formatter check runs in the `validate` phase, before compile)
  - Run `.\mvnw compile` to confirm no build errors
  - Verify no ArchUnit violations are anticipated: `loan/ui` imports `loan/domain` (allowed); `catalog/ui` imports `loan/ui` through `BorrowDialog` — this is a cross-feature UI dependency and must be reviewed against the architecture rules in `structure.md` before proceeding

- [ ] 9. Write browserless unit tests — `UC002BorrowBookTest`
  - [ ] 9.1 Create `UC002BorrowBookTest.java` in `src/test/.../loan/ui/`
    - Extends `AbstractBrowserlessTest`
    - Class-level `@WithUserDetails("alice")` for happy path and most scenarios
    - `@Autowired LoanService loanService` and `@Autowired DSLContext dsl` for setup/teardown
    - `@AfterEach` cleanup: delete **all** loan rows for test books (both open and returned — `WHERE book_id IN (test book ids)`), then delete the test book rows (identified by UUID prefix in title). Deleting all loans, not only open ones, ensures the book rows can be removed without FK constraint violations.
    - All test books inserted with `UUID.randomUUID() + " "` prefix to avoid collisions with seeded data
    - Annotate each test method with `@UseCase(id = "UC-002", scenario = "...", businessRules = {"BR-XXX"})`
    - Test methods:
      - `borrow_button_appears_for_available_book()` — insert a 2-copy test book (UUID-prefixed title) via DSL **before** navigating; navigate to `CatalogView`; search for the UUID prefix; assert the Borrow button is present in the matching grid row (`@UseCase` businessRules `{"BR-009"}`)
      - `borrow_button_absent_for_unavailable_book()` — insert a 1-copy test book, insert 1 open loan via DSL, navigate, assert no Button in that row (`@UseCase` businessRules `{"BR-008", "BR-009"}`)
      - `successful_borrow_shows_success_notification()` — insert available test book, click Borrow, confirm in dialog, assert success notification text (`@UseCase` businessRules `{"FR-005"}`)
      - `successful_borrow_updates_availability_in_grid()` — insert 2-copy test book, borrow once via dialog, assert availability cell decremented from `"2 of 2"` to `"1 of 2"` without page reload (`@UseCase` businessRules `{"BR-011"}`)
      - `borrow_service_throws_when_no_copy_available()` — insert a 1-copy test book; insert 1 open loan via DSL; call `loanService.borrow(bookId)` directly; assert `NoAvailableCopyException` is thrown; assert open-loan count is unchanged (`@UseCase` businessRules `{"BR-009"}`)
      - `borrow_dialog_shows_error_notification_when_no_copy_available()` — construct a `BorrowDialog(bookId, title, loanService, onSuccess)` directly; programmatically fire the confirm listener (or use browserless `find`/`click` helpers); assert an error notification with text `"No copy of … is available right now."` is visible (`@UseCase` businessRules `{"BR-009"}`)
      - `loan_row_has_null_returned_at_and_no_due_date()` — borrow via service, query the loan row via DSL, assert `returned_at IS NULL` and no extra columns (`@UseCase` businessRules `{"BR-012", "C-019"}`)
      - `concurrent_borrow_creates_exactly_one_loan(int copies, int threads)` — annotate with `@ParameterizedTest` and `@CsvSource({"1,5", "3,5"})`; for each case: insert a test book with `copies` copies; capture `SecurityContextHolder.getContext()` before spawning threads; use a `CountDownLatch` start gate to release all `threads` worker threads simultaneously; each worker calls `SecurityContextHolder.setContext(ctx)` then `loanService.borrow(bookId)`, catching `NoAvailableCopyException`; call `executor.shutdown()` then `executor.awaitTermination(10, TimeUnit.SECONDS)` and fail the test if threads do not finish within that limit; after all threads finish assert exactly `min(copies, threads)` open loans exist and the number of caught `NoAvailableCopyException`s equals `threads - min(copies, threads)` (`@UseCase` businessRules `{"BR-009"}`)
    - For the A2 scenario, override security at method level with `@WithUserDetails("librarian")`:
      - `borrow_service_throws_when_no_member_profile()` — annotate with `@WithUserDetails("librarian")`; call `loanService.borrow(anyBookId)` directly; assert `NoMemberProfileException` is thrown (`@UseCase` businessRules `{"BR-010", "C-009"}`)
      - `borrow_dialog_shows_error_notification_when_no_member_profile()` — annotate with `@WithUserDetails("librarian")`; construct `BorrowDialog` directly; fire the confirm listener; assert error notification text `"Borrowing is not available for your account."` is visible (`@UseCase` businessRules `{"BR-010", "C-009"}`)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
  - [ ]* 9.2 Verify `UC002BorrowBookTest` passes
    - Run `.\mvnw test -Dtest=UC002BorrowBookTest`
    - All tests green; no Error Prone warnings; no data leaking between tests
  - [ ] 9.3 Canary check — verify the concurrency test detects missing guards
    - Temporarily remove `.forUpdate()` from `LoanRepository.borrowAtomically()` (comment it out)
    - Run `.\mvnw test -Dtest=UC002BorrowBookTest#concurrent_borrow_creates_exactly_one_loan*`
    - Confirm the test fails (more loans than `book.copies` created)
    - Restore `.forUpdate()`
    - Temporarily remove `@Transactional` from `LoanService.borrow()` (comment it out)
    - Run the same test again
    - Confirm the test fails (lock released between SELECT and INSERT, race observed)
    - Restore `@Transactional`
    - Record both results in a code comment on the test method

- [ ] 10. Write parameterised property tests — `UC002BorrowBookParameterizedTest`
  - [ ] 10.1 Create `UC002BorrowBookParameterizedTest.java` in `src/test/.../loan/domain/`
    - Extends `AbstractBrowserlessTest`
    - Class-level `@WithUserDetails("alice")`
    - `@Autowired LoanService loanService`, `@Autowired LoanRepository loanRepository`, `@Autowired DSLContext dsl`
    - `@AfterEach` cleanup: delete test loans and test books (UUID-prefix isolation)
    - **Property 1 — Borrow succeeds exactly when a copy is free**
      - `@ParameterizedTest @CsvSource({"1,0", "2,1", "5,3", "10,9"})`
      - `void borrow_succeeds_when_copy_is_available(int copies, int existingLoans)`
      - Insert book with `copies`; insert `existingLoans` open loans for alice's `member.id` via DSL
      - Call `loanService.borrow(bookId)`; assert no exception thrown
      - Assert open-loan count == `existingLoans + 1`
      - `@UseCase(id="UC-002", businessRules={"BR-009"})` — validates design Property 1
    - **Property 2 — Borrow is rejected when no copy is available**
      - `@ParameterizedTest @CsvSource({"1", "2", "5", "10"})`
      - `void borrow_rejected_when_fully_on_loan(int copies)`
      - Insert book with `copies`; insert `copies` open loans via DSL
      - Assert `assertThatThrownBy(() -> loanService.borrow(bookId)).isInstanceOf(NoAvailableCopyException.class)`
      - Assert open-loan count still == `copies` (unchanged)
      - `@UseCase(id="UC-002", businessRules={"BR-009"})` — validates design Property 2
    - _(Property 3 — availability never negative — is covered by the concurrency test in task 9.1)_
    - _Requirements: 1.1, 1.5, 1.6_
  - [ ]* 10.2 Verify `UC002BorrowBookParameterizedTest` passes
    - Run `.\mvnw test -Dtest=UC002BorrowBookParameterizedTest`

- [ ] 11. Write Playwright E2E tests — `UC002BorrowBookIT`
  - [ ] 11.1 Create `UC002BorrowBookIT.java` in `src/test/.../loan/ui/`
    - Extends `PlaywrightIT`
    - Signs in with seeded `alice` account (password `alice`)
    - `@Autowired DSLContext dsl` for test-book setup and teardown
    - `@AfterEach` cleanup: delete test loans and test books (UUID-prefix isolation)
    - Test methods:
      - `member_can_borrow_available_book()`:
        - Insert a test book with 2 copies (0 loans) via DSL before the test
        - Navigate to `catalog`; log in as `alice`
        - Wait for the grid to render; search for the UUID-prefix title
        - Click the Borrow button on the matching row
        - Assert the `ConfirmDialog` is visible with the correct header text
        - Click the confirm button
        - Assert `Notification` with success text is visible (`Mopo.waitForConnectionToSettle`)
        - Assert the availability cell for that row reads `"1 of 2"`
        - `@UseCase(id="UC-002", scenario="Main success scenario", businessRules={"FR-005","BR-011"})`
      - `no_borrow_button_for_unavailable_book()`:
        - Insert a 1-copy test book; insert 1 open loan via DSL
        - Navigate to catalog; search for that book
        - Assert no `<vaadin-button>` with text "Borrow" exists in the matching row
        - `@UseCase(id="UC-002", scenario="A1: No Copy Available", businessRules={"BR-009"})`
    - _Requirements: 1.1, 1.3, 1.4, 1.5_
  - [ ]* 11.2 Verify `UC002BorrowBookIT` passes
    - Run `.\mvnw verify -Dit.test=UC002BorrowBookIT`

- [ ] 12. Final checkpoint — all tests pass
  - Run `.\mvnw spring-javaformat:apply` to ensure all files are formatted before the validate phase runs
  - Run `.\mvnw test` — browserless and ArchUnit checks must be green
  - Run `.\mvnw verify` — Playwright E2E and JaCoCo merge must complete without failures

---

## Notes

- Sub-tasks marked with `*` are optional and can be skipped for a faster build cycle, but the corresponding implementation sub-tasks must still be complete before marking the parent task done.
- Test data isolation is mandatory: every test that writes data must prefix book titles with a `UUID.randomUUID()` string and delete those rows in `@AfterEach`. Seeded rows (alice's books, the librarian account) must never be mutated by UC-002 tests.
- The `librarian` account seeded by `SecuritySeed` has no `member` row — use it with `@WithUserDetails("librarian")` specifically to exercise alternative flow A2.
- The concurrency test (task 9.1) must call `LoanService.borrow()` — not `LoanRepository.borrowAtomically()` — so the full `@Transactional` boundary is in effect. Calling the repository directly releases the `FOR UPDATE` lock before the `INSERT`, defeating the atomicity guarantee under test.
- Spring Security context is thread-local: capture `SecurityContextHolder.getContext()` on the test thread before spawning workers, and call `SecurityContextHolder.setContext(ctx)` at the top of each worker's `Runnable`.
- The `CatalogView` modification introduces a cross-feature import (`catalog/ui` → `loan/ui`). This is intentional per the design document. Review against the ArchUnit rules in `ArchitectureTest` to confirm it does not violate any enforced constraint.

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "2.1", "2.2"] },
    { "id": 1, "tasks": ["3.1"] },
    { "id": 2, "tasks": ["4.1"] },
    { "id": 3, "tasks": ["6.1"] },
    { "id": 4, "tasks": ["7.1"] },
    { "id": 5, "tasks": ["9.1", "10.1"] },
    { "id": 6, "tasks": ["9.2", "9.3", "10.2", "11.1"] },
    { "id": 7, "tasks": ["11.2"] }
  ]
}
```
