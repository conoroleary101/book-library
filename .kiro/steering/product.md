# Product: SimpleLibrary

## Purpose

SimpleLibrary is a small web application for school, club, or community libraries. It replaces paper lists and spreadsheets with a real-time system where members can find books themselves and the librarian has an instant overview of all loans.

## Actors

| Actor | Description |
|---|---|
| **Member** | A library patron who searches the catalog, borrows books, and returns them. Signs in with their own `app_user` account. |
| **Librarian** | Manages the catalog and member accounts, oversees all active loans and history. Signs in with their own `app_user` account. A librarian who wants to borrow books also holds a `member` row — nothing about the role prevents it. |

## Core Domain Entities

| Entity | Key Facts |
|---|---|
| `app_user` | Authentication identity only: `id`, `username`, `password_hash`, `role` (MEMBER\|LIBRARIAN), `created_at`. No profile data. |
| `member` | Patron profile: `id`, `user_id` (FK → app_user, UNIQUE), `name`, `email`, `created_at`. |
| `book` | Catalog entry: `id`, `title`, `author`, `isbn` (nullable), `copies` (1–999), `created_at`. Available copies is **derived** (copies minus open loans) — never stored as its own column. |
| `loan` | Borrow event: `id`, `member_id` (FK → member), `book_id` (FK → book), `borrowed_at`, `returned_at` (null = still on loan). No due dates. |

## Functional Scope

**Member actions:** search catalog by title or author, see availability per book, borrow an available copy, return a borrowed book, view own active loans.

**Librarian actions:** everything a member can do, plus view all active loans, view loan history, add/edit/remove books, create/list/edit member accounts.

## Hard Constraints (business rules)

- **No payments or fines.** The system must not handle fees.
- **No reservations or waiting lists.** Borrow it if a copy is available; otherwise wait.
- **Single branch only.** No multi-branch support.
- **No self-service sign-up.** The librarian creates all accounts; no password reset or social login.
- **No due dates on loans.** A loan records borrow timestamp and return timestamp only.
- **Identity separation.** `app_user` holds auth data; `member` holds the patron profile. Loans reference `member.id`, never `app_user.id`.
- **Derived availability.** Available copies = `book.copies` minus count of open loans. Do not add an `available_copies` column.
- **Loan retention.** Loan records must never be hard-deleted and must be retained for at least 24 months after return.
- **Seeded librarian.** A first librarian `app_user` must be seeded on startup (`SecuritySeed`) so the app is usable on first boot.
- **Search scope.** Catalog search matches `title` and `author` only. ISBN is displayed but not searched.
- **No publisher or publication year.** Neither is recorded on `book`; do not add columns for them (C-020).

## Non-Goals

- Payment or overdue fines handling
- Reservations or waiting lists
- Multi-branch support
- Self-service sign-up, password reset, or social login
