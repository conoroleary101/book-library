-- loan records one copy of a book being taken out by a patron and later brought back.
--
-- A loan belongs to a member, never directly to an app_user (C-009). A loan is open while
-- returned_at is null; there is no due date in this version (C-019). Loans are retained
-- for at least 24 months after return (NFR-014) and are never hard-deleted by the
-- application, so neither foreign key cascades on delete.

CREATE SEQUENCE loan_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE loan
(
    id          BIGINT    DEFAULT nextval('loan_seq') PRIMARY KEY,
    member_id   BIGINT    NOT NULL REFERENCES member (id),
    book_id     BIGINT    NOT NULL REFERENCES book (id),
    borrowed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    returned_at TIMESTAMP,
    CONSTRAINT chk_loan_returned_after_borrowed CHECK (returned_at IS NULL OR returned_at > borrowed_at)
);

CREATE INDEX idx_loan_member ON loan (member_id);
CREATE INDEX idx_loan_book ON loan (book_id);

-- Deriving availability and listing active loans both scan the open loans of a book, so
-- the open rows are indexed on their own.
CREATE INDEX idx_loan_open_by_book ON loan (book_id) WHERE returned_at IS NULL;
