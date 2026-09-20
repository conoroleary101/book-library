# Requirements: SimpleLibrary

Derived from [vision.md](vision.md) and [architecture.md](architecture.md).

Actors: **Member** (borrows books), **Librarian** (manages the catalog, member accounts,
and oversees loans). Both sign in with their own `app_user` account.

## Functional Requirements

| ID     | Title                | User Story                                                                                                                                  | Priority | Status |
|--------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------|----------|--------|
| FR-001 | Sign In              | As a user, I want to sign in with my username and password so that the system can tie every action I take to my account.                     | High     | Open   |
| FR-002 | Sign Out             | As a user, I want to sign out so that nobody can use my session after I leave the computer.                                                  | High     | Open   |
| FR-003 | Search Catalog       | As a member, I want to search the catalog by title or author so that I can find a book without asking the librarian.                         | High     | Open   |
| FR-004 | See Book Availability| As a member, I want to see whether a book is currently on loan so that I know if I can borrow it now.                                        | High     | Open   |
| FR-005 | Borrow Book          | As a member, I want to borrow an available book so that I can take it home and the system records the loan.                                  | High     | Open   |
| FR-006 | Return Book          | As a member, I want to return a book I borrowed so that it becomes available to other members.                                               | High     | Open   |
| FR-007 | View My Loans        | As a member, I want to see the books I currently have on loan so that I know what I still have to return.                                    | High     | Open   |
| FR-008 | View Active Loans    | As a librarian, I want to see all active loans with member and book so that I have a real time overview of who has which book.               | High     | Open   |
| FR-009 | View Loan History    | As a librarian, I want to see past loans with borrow and return dates so that I can trace who borrowed a book and when.                      | Medium   | Open   |
| FR-010 | Add Book             | As a librarian, I want to add a book to the catalog so that members can find and borrow it.                                                  | High     | Open   |
| FR-011 | Edit Book            | As a librarian, I want to correct a book's details so that the catalog stays accurate.                                                       | Medium   | Open   |
| FR-012 | Remove Book          | As a librarian, I want to remove a book that is not on loan so that the catalog does not list books the library no longer owns.              | Medium   | Open   |
| FR-013 | Create Member Account| As a librarian, I want to create a member account with a username, password, and profile so that a new patron can sign in and borrow books.  | High     | Open   |
| FR-014 | List Members         | As a librarian, I want to see all members so that I can find the patron I need to work with.                                                 | Medium   | Open   |
| FR-015 | Edit Member Profile  | As a librarian, I want to update a member's name and email so that the member record stays current.                                          | Low      | Open   |

## Non-Functional Requirements

| ID      | Title                  | Requirement                                                                                                                                         | Category        | Priority | Status |
|---------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|----------|--------|
| NFR-001 | Search Response Time   | A catalog search must return results within 2 seconds for a catalog of up to 5,000 books.                                                          | Performance     | High     | Open   |
| NFR-002 | Page Load Time         | Every view must render its initial content within 3 seconds over a 10 Mbit/s connection.                                                            | Performance     | Medium   | Open   |
| NFR-003 | Concurrent Users       | The system must serve 50 concurrent signed-in users without any request exceeding the NFR-001 and NFR-002 thresholds.                               | Scalability     | Medium   | Open   |
| NFR-004 | Availability           | The system must maintain 99% uptime between 08:00 and 20:00 local time, measured over a calendar month.                                             | Availability    | Medium   | Open   |
| NFR-005 | Password Storage       | Passwords must be stored only as BCrypt hashes with a work factor of at least 10; no plaintext or reversibly encrypted password may be persisted.   | Security        | High     | Open   |
| NFR-006 | Default Deny Access    | Every routable view must declare an access rule; a view with no `@RolesAllowed` or `@AnonymousAllowed` annotation must be unreachable.               | Security        | High     | Open   |
| NFR-007 | Role Enforcement       | A signed-in member requesting a librarian-only route must be denied access and rerouted, with zero librarian-only data in the response.             | Security        | High     | Open   |
| NFR-008 | Loan Data Scoping      | A member must never see loan rows belonging to another member; the "my loans" query must filter by the signed-in user's `member.id` in the domain layer. | Security        | High     | Open   |
| NFR-009 | Transport Encryption   | All traffic in production must use TLS 1.2 or higher; plain HTTP requests must be redirected to HTTPS.                                              | Security        | High     | Open   |
| NFR-010 | Session Timeout        | A session must expire after 30 minutes of inactivity and require a new sign-in.                                                                     | Security        | Medium   | Open   |
| NFR-011 | Accessibility          | All views must conform to WCAG 2.2 Level AA.                                                                                                        | Usability       | Medium   | Open   |
| NFR-012 | Architecture Rules     | ArchUnit tests must enforce the package-by-feature rules in `docs/architecture.md`, and `./mvnw test` must fail on any violation.                     | Maintainability | High     | Open   |
| NFR-013 | Test Coverage per FR   | Every functional requirement must be covered by at least one automated test (browserless UI unit test or Playwright E2E test).                      | Maintainability | High     | Open   |
| NFR-014 | Loan History Retention | Loan records must be retained for at least 24 months after return and must never be hard-deleted by an application action.                          | Maintainability | Medium   | Open   |
| NFR-015 | Single Artifact Deploy | The application must deploy as a single executable JAR requiring only a JRE and a reachable PostgreSQL instance, and must start within 60 seconds.   | Portability     | Medium   | Open   |

## Constraints

| ID    | Title                   | Constraint                                                                                                              | Category    | Priority | Status |
|-------|-------------------------|-------------------------------------------------------------------------------------------------------------------------|-------------|----------|--------|
| C-001 | Runtime Platform        | The backend must run on Java 25.                                                                                         | Technical   | High     | Open   |
| C-002 | Application Framework   | The application must be built on Spring Boot 4.1.                                                                        | Technical   | High     | Open   |
| C-003 | UI Technology           | The UI must be built with Vaadin Flow 25.2 server-side Java views; no Hilla or React views.                              | Technical   | High     | Open   |
| C-004 | Data Access Technology  | All database access must go through jOOQ 3.21; JPA and Hibernate must not be used.                                       | Technical   | High     | Open   |
| C-005 | Database Platform       | The system must use PostgreSQL as its only supported database.                                                           | Technical   | High     | Open   |
| C-006 | Schema Migrations       | Every schema change must be delivered as a versioned Flyway script under `src/main/resources/db/migration`.              | Technical   | High     | Open   |
| C-007 | Package Structure       | Code must follow the package-by-feature layout with a `ui` / `domain` split as defined in `docs/architecture.md`.        | Technical   | High     | Open   |
| C-008 | Identity Separation     | Authentication data must live in `app_user`; the `member` domain entity must link to it by `user_id` foreign key.        | Technical   | High     | Open   |
| C-009 | Loan Ownership          | A loan must reference `member.id`, never `app_user.id`.                                                                  | Technical   | High     | Open   |
| C-010 | Testing Stack           | UI tests must use Vaadin Browserless Testing; end-to-end tests must use Playwright with Mopo.                            | Technical   | Medium   | Open   |
| C-011 | Build Prerequisite      | The build requires Docker or Testcontainers Cloud, because jOOQ code generation runs against a Testcontainers PostgreSQL. | Operational | High     | Open   |
| C-012 | Browser Support         | The UI must support the latest two versions of Chrome, Firefox, Edge, and Safari.                                        | Technical   | Medium   | Open   |
| C-013 | No Payments or Fines    | The system must not handle payments, fees, or overdue fines.                                                             | Business    | High     | Open   |
| C-014 | No Reservations         | The system must not offer reservations or waiting lists.                                                                 | Business    | High     | Open   |
| C-015 | Single Branch           | The system must support exactly one library branch.                                                                      | Business    | High     | Open   |
| C-016 | No Self Service Signup  | The system must not offer self service sign-up, password reset, or social login; the librarian creates all accounts.     | Business    | High     | Open   |
| C-017 | Seeded Librarian        | A first librarian account must be seeded on startup so the application is usable on first boot.                          | Technical   | High     | Open   |

## Open Questions

These could not be derived from the vision or the existing code and need a decision before
the affected requirements are final:

1. **Loan period.** Is there a due date, and how long is a loan? The vision mentions no due
   dates and explicitly excludes fines (C-013), so FR-005 currently records only a borrow
   date. Confirm whether a due date is needed.
2. **Borrow limit.** `docs/architecture.md` uses "a member may not have more than five open
   loans" as an *example* of business logic, not as a decision. Confirm whether a limit
   exists and what the number is.
3. **Multiple copies.** Does a book have one physical copy or several? This decides whether
   FR-004 is a boolean available/on-loan flag or a copy count, and it drives the entity model.
4. **Search fields.** FR-003 assumes title and author. Confirm whether ISBN, publisher, or
   year should also be searchable.
5. **Budget and deadline.** No budget limit or delivery date is stated in the vision, so no
   corresponding constraint was written.
