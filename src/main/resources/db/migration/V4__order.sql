-- order 는 예약어이므로 테이블명은 orders. member/deal 은 ID 참조만 하므로 FK 를 두지 않는다.
-- Phase 1: PK 외 인덱스를 추가하지 않는다(Phase 2 측정 대상).
CREATE TABLE orders (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    member_id     BIGINT      NOT NULL,
    deal_id       BIGINT      NOT NULL,
    quantity      INT         NOT NULL,
    amount        BIGINT      NOT NULL,
    order_status  VARCHAR(20) NOT NULL,
    cancel_reason VARCHAR(30) NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BIGINT      NOT NULL,
    modified_at   DATETIME(6) NOT NULL,
    modified_by   BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
