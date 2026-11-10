-- C3 혜택. 쿠폰 정책·발급 쿠폰(Task 2)과 포인트 테이블(Task 3)을 한 번에 만든다. 다른 테이블은 ID 로만 참조한다(FK 없음).
-- 쿠폰 정책: 발급 수량 issued_count 는 조건부 UPDATE 로만 올린다.
CREATE TABLE coupon_policy (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(50)  NOT NULL,
    issuer_type       VARCHAR(20)  NOT NULL,
    seller_id         BIGINT       NULL,
    coupon_type       VARCHAR(20)  NOT NULL,
    target_type       VARCHAR(20)  NOT NULL,
    target_product_id BIGINT       NULL,
    discount_type     VARCHAR(20)  NOT NULL,
    discount_value    BIGINT       NOT NULL,
    max_discount      BIGINT       NULL,
    min_order_amount  BIGINT       NULL,
    total_quantity    INT          NULL,
    issued_count      INT          NOT NULL DEFAULT 0,
    validity_days     INT          NULL,
    valid_from        DATETIME(6)  NOT NULL,
    valid_until       DATETIME(6)  NOT NULL,
    policy_status     VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);

-- 발급 쿠폰: 같은 정책을 같은 회원이 두 번 받을 수 없다.
CREATE TABLE coupon (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    policy_id     BIGINT      NOT NULL,
    member_id     BIGINT      NOT NULL,
    coupon_status VARCHAR(20) NOT NULL,
    used_order_id BIGINT      NULL,
    issued_at     DATETIME(6) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    used_at       DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coupon_policy_member UNIQUE (policy_id, member_id)
);

-- 포인트 잔액: 회원당 한 행. 변경은 이 행을 FOR UPDATE 로 잠근 뒤에 한다.
CREATE TABLE member_point (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    member_id BIGINT      NOT NULL,
    balance   BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_point_member UNIQUE (member_id)
);

-- 적립 로트: 사용은 만료가 가까운 로트부터(FIFO) 차감한다.
CREATE TABLE point_lot (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    member_id        BIGINT      NOT NULL,
    amount           BIGINT      NOT NULL,
    remaining_amount BIGINT      NOT NULL,
    source_type      VARCHAR(20) NOT NULL,
    source_id        BIGINT      NOT NULL,
    expires_at       DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);

-- 원장: UNIQUE(transaction_type, ref_type, ref_id) 가 적립·사용·환불·만료의 이중 처리를 막는다.
CREATE TABLE point_transaction (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    member_id        BIGINT      NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount           BIGINT      NOT NULL,
    balance_after    BIGINT      NOT NULL,
    ref_type         VARCHAR(20) NOT NULL,
    ref_id           BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_point_transaction_ref UNIQUE (transaction_type, ref_type, ref_id)
);

-- 사용 거래가 어느 로트에서 얼마를 가져갔는지. 항목 단위 되돌림은 reverted_amount 로 나눠 기록한다.
CREATE TABLE point_lot_usage (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    transaction_id  BIGINT      NOT NULL,
    lot_id          BIGINT      NOT NULL,
    amount          BIGINT      NOT NULL,
    reverted_amount BIGINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
