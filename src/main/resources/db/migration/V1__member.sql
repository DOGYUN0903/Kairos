CREATE TABLE member (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NOT NULL,
    modified_at DATETIME(6)  NOT NULL,
    modified_by BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email)
);
