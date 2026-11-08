-- 결제 시도는 결과(승인/실패)와 무관하게 기록한다. order_id는 ID 참조만 하므로 FK를 두지 않는다.
CREATE TABLE payment (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    order_id       BIGINT      NOT NULL,
    amount         BIGINT      NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    fail_reason    VARCHAR(30) NULL,
    created_at     DATETIME(6) NOT NULL,
    created_by     BIGINT      NOT NULL,
    modified_at    DATETIME(6) NOT NULL,
    modified_by    BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
