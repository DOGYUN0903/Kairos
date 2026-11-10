-- 결제. order 는 ID 로만 참조한다(FK 없음). payment_key 는 PG 승인 시도 시점에 채워지며 UNIQUE 가 중복 승인의 마지막 방어선이다.
CREATE TABLE payment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL,
    order_no        VARCHAR(32)  NOT NULL,
    payment_key     VARCHAR(200) NULL,
    amount          BIGINT       NOT NULL,
    refunded_amount BIGINT       NOT NULL,
    payment_status  VARCHAR(20)  NOT NULL,
    fail_reason     VARCHAR(100) NULL,
    in_progress_at  DATETIME(6)  NULL,
    approved_at     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    created_by      BIGINT       NOT NULL,
    modified_at     DATETIME(6)  NOT NULL,
    modified_by     BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_key UNIQUE (payment_key)
);
