-- 멱등 키. JPA 엔티티 없이 JdbcTemplate 으로만 다룬다(BaseEntity 규칙의 예외, 필터에서 트랜잭션과 무관하게 써야 한다).
-- state: PROCESSING(처리 중) / COMPLETED(응답 저장됨). 같은 회원의 같은 키는 한 행뿐이다.
CREATE TABLE idempotency_key (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    member_id       BIGINT      NOT NULL,
    idem_key        VARCHAR(100) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    state           VARCHAR(20) NOT NULL,
    response_status INT         NULL,
    response_body   MEDIUMTEXT  NULL,
    expires_at      DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    modified_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_member_key UNIQUE (member_id, idem_key)
);
