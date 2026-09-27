# Project Structure & Coding Conventions

## Package Layout

Root package: `ai.unified.process.demo.book.library`

```
ai.unified.process.demo.book.library
├── Application.java                    # Spring Boot entry point
├── usecase/
│   └── UseCase.java                    # Annotation linking tests to use-case specs
├── core/                               # Cross-cutting concerns shared by ≥2 features
│   ├── configuration/                  # @Configuration beans (VjJooqConfiguration, DemoDataSeed)
│   ├── security/                       # SecurityConfig, AppUserDetailsService, AppUserDetails,
│   │                                   # CurrentUser, Role, SecuritySeed, LoginView
│   └── ui/
│       ├── layout/                     # MainLayout, navigation shell
│       └── components/                 # Shared Vaadin components
├── catalog/                            # Feature: browse / search books
│   ├── domain/                         # BookRepository, Book record
│   └── ui/                             # CatalogView
├── loan/                               # PLANNED, not yet built (UC-002 onwards)
└── member/                             # PLANNED, not yet built
```

Only `catalog`, `core` and `usecase` exist today. The `loan` and `member` packages, and any classes inside them, are decided by their use case specs. Do not assume a class exists because it is named in an example below.

Generated jOOQ sources land in `ai.unified.process.demo.book.library.db` under `target/generated-sources/jooq`. Never edit them.

## Architecture Rules

### Enforced by `ArchitectureTest` (a violation breaks the build)

- **Nothing may depend on `..ui..`.** UI code sits at the top; domain and core must never import from it.
- **Vaadin confinement.** Only `..ui..` and `..security..` packages may import `com.vaadin..*`. Domain repositories and services must be Vaadin-free.
- **`core` may not access `..greeting..`.** Stale: the greeting feature no longer exists, so this rule currently protects nothing. It should name the real feature packages.

### Conventions (not checked by any test, so follow them deliberately)

1. **Package-by-feature first.** There are no top-level `controllers/`, `services/`, or `repositories/` packages. The feature name is the top-level grouping; role is expressed by the class name and its position inside `ui` or `domain`.

2. **Two subpackages per feature: `ui` and `domain` only.** Do not create additional sub-layers inside a feature.

3. **Cross-feature access goes through the other feature's service.** A view in `catalog/ui` must not call `loan/domain` repositories directly; if it needs loan data, it calls a service in `loan/domain`.

4. **`core` is for shared code only.** A class used by exactly one feature belongs in that feature, not in `core`.

## Service Layer: When to Add One

Do **not** add a service for pure data-access use cases. The view calls the repository directly.

**Add a `@Service` class only when the feature needs one or more of:**
- Coordinating writes across more than one repository inside a transaction
- Validation or invariants that span multiple entities (e.g. "is a copy available before borrowing?")
- Side effects beyond the database (events, email, external calls)
- Logic that must be reused from more than one view

**Once a service exists, the view must go through it exclusively.** Mixed access (some calls via service, others bypassing to the repository) defeats the invariant guarantee.

**Current example:**
- `catalog` — read-only query, no service; `CatalogView` calls `BookRepository` directly.

**Applying the rule to future features:** borrowing (UC-002) checks availability before writing a loan, which matches the "invariants that span multiple entities" criterion. Whether that means a service is decided in the UC-002 design, not here.

## Naming Conventions

| Type | Pattern | Example |
|---|---|---|
| Vaadin view | `<Feature>View` | `CatalogView`, `ActiveLoansView` |
| Repository | `<Entity>Repository` | `BookRepository`, `LoanRepository` |
| Service | `<Feature>Service` | `LoanService`, `MemberService` |
| Domain record | Plain entity name | `Book`, `Loan`, `Member` |
| Dialog / form | `<Action>Dialog`, `<Entity>Form` | `BorrowDialog`, `MemberForm` |
| Flyway script | `V<NNN>__<description>.sql` | `V004__create_loan_table.sql` |
| Browserless test | `UC<NNN><UseCaseName>Test` | `UC001SearchCatalogTest` |
| Playwright E2E test | `UC<NNN><UseCaseName>IT` | `UC001SearchCatalogIT` |

## Domain Records

Domain objects are plain Java `record`s in `<feature>/domain`. Keep them focused on what the repository returns — no Vaadin or jOOQ types in the record fields.

```java
// Good
public record Book(Long id, String title, String author, @Nullable String isbn,
        Integer copies, Integer availableCopies) {}

// Bad — jOOQ type leaks into the domain
public record Book(BookRecord record) {}
```

Use JSpecify `@Nullable` only on fields that genuinely may be null (e.g. `isbn`). Non-null is the default.

## jOOQ Repository Pattern

```java
@Repository
public class BookRepository {

    private final DSLContext dsl;

    public BookRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Book> search(String term) {
        return dsl.select(...)
            .from(BOOK)
            .where(...)
            .orderBy(BOOK.TITLE.asc())
            .fetch(Records.mapping(Book::new));
    }
}
```

- Constructor-inject `DSLContext`.
- Use static imports from `ai.unified.process.demo.book.library.db.Tables` for table references.
- Map results to domain records via `Records.mapping(Book::new)`.
- Compute derived values (e.g. available copies) as SQL subqueries — never add a stored column for a derived value.
- Wrap multi-repository writes in a Spring `@Transactional` method on the service, not in the repository.

## Vaadin View Pattern

```java
@RolesAllowed({ "MEMBER", "LIBRARIAN" })
@Route("catalog")
@PageTitle("Search Catalog")
@Menu(title = "Catalog", order = 1, icon = "vaadin:book")
public class CatalogView extends VerticalLayout {

    private final transient BookRepository bookRepository;

    public CatalogView(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
        // configure components, add to layout, load data
    }
}
```

- Every routable view **must** declare `@RolesAllowed`, `@AnonymousAllowed`, or `@PermitAll`. A view with no annotation is unreachable by design (default-deny).
- Use `@RolesAllowed({"MEMBER", "LIBRARIAN"})` for member-facing views, `@RolesAllowed("LIBRARIAN")` for librarian-only views, `@AnonymousAllowed` only on the login view.
- **Never use `@PermitAll`** — it hides role intent and grants access to any authenticated user regardless of role.
- Mark injected domain dependencies `transient` (Vaadin view serialization requirement).
- Views do not branch on roles. Role-specific data scoping belongs in the service or repository.

## Security Patterns

- `CurrentUser` (injectable from both `ui` and `domain`) reads `SecurityContextHolder` and returns the `AppUserDetails` principal. Use it in domain code to scope queries by the logged-in user.
- The path from "logged-in user" to "their loans": `AppUserDetails.appUserId()` → join through `member.user_id` → `member.id` → loan rows.
- `SecuritySeed` (`ApplicationRunner`, `@Order(10)`) seeds the initial librarian account. `DemoDataSeed` (`@Order(20)`) seeds catalog and loan data. Always respect ordering when adding new `ApplicationRunner` beans.

## Startup Seed Ordering

| Bean | `@Order` | Responsibility |
|---|---|---|
| `SecuritySeed` | 10 | Inserts the seeded librarian (and demo member) `app_user` rows |
| `DemoDataSeed` | 20 | Inserts demo books and loans; skips if data already exists |

New `ApplicationRunner` beans must pick an order value that reflects their dependencies on the above seeds.

## Code Style

- Formatting is enforced by `spring-javaformat` at the `validate` phase. Run `.\mvnw spring-javaformat:apply` to auto-format before committing.
- Javadoc on public methods in repositories and services. Reference the use-case ID (e.g. `UC-001`) and relevant business rules (e.g. `BR-001`) in the doc comment when the method implements a specific requirement.
- Package-level `package-info.java` files are present in `ui` and `domain` subpackages — add them for new feature packages.
