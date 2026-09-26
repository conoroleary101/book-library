-- book is a title held by the library together with the number of physical copies owned.
--
-- copies is the total holding. The number of copies currently available is derived as
-- copies minus the book's open loans and is deliberately not stored as a column (C-018),
-- so it can never disagree with the loan table. The rule "open loans must not exceed
-- copies" spans two tables and is enforced by LoanService, not by a CHECK constraint.

CREATE SEQUENCE book_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE book
(
    id         BIGINT       DEFAULT nextval('book_seq') PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    author     VARCHAR(150) NOT NULL,
    isbn       VARCHAR(20),
    copies     INTEGER      NOT NULL CHECK (copies BETWEEN 1 AND 999),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The catalog is searched by title and author only (C-020, UC-001 BR-001) and is listed
-- sorted by title, so both columns are indexed.
CREATE INDEX idx_book_title ON book (title);
CREATE INDEX idx_book_author ON book (author);
