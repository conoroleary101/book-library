-- member is the domain entity for a library patron: the profile of a person who borrows
-- books. Identity stays in app_user; member links to it through user_id. A librarian who
-- borrows holds a member row alongside their librarian account, exactly like any other
-- patron (docs/vision.md, docs/architecture.md).
--
-- app_user is created by V001 and is never recreated or altered here.

CREATE SEQUENCE member_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE member
(
    id         BIGINT       DEFAULT nextval('member_seq') PRIMARY KEY,
    user_id    BIGINT       NOT NULL UNIQUE REFERENCES app_user (id),
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL CHECK (email LIKE '%_@_%.%'),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_member_name ON member (name);
