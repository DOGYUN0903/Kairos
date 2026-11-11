-- 판매자 주간 정산. 정산·라인은 생성 뒤 불변이고 보정은 settlement_adjustment 행으로만 한다.
-- 인덱스는 PK/UNIQUE 만 둔다. FK 없음. UNIQUE 가 멱등 가드다:
--   (seller_id, period_start) = 판매자·기간당 정산 1개, seller_order_id = 판매자 주문은 한 번만 정산,
--   settlement_run.period_start = 기간 생성 완료 기록 1개, settlement_adjustment.source_settlement_id = 정산당 이월 1개.
CREATE TABLE settlement (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id              BIGINT       NOT NULL,
    period_start           DATE         NOT NULL,
    period_end             DATE         NOT NULL,
    payout_date            DATE         NOT NULL,
    settlement_status      VARCHAR(20)  NOT NULL,
    sales_amount           BIGINT       NOT NULL,
    seller_coupon_discount BIGINT       NOT NULL,
    commission_amount      BIGINT       NOT NULL,
    shipping_fee_owed      BIGINT       NOT NULL,
    return_fee_borne       BIGINT       NOT NULL,
    adjustment_amount      BIGINT       NOT NULL,
    total_amount           BIGINT       NOT NULL,
    payable_amount         BIGINT       NOT NULL,
    line_count             INT          NOT NULL,
    confirmed_at           DATETIME(6)  NULL,
    paid_at                DATETIME(6)  NULL,
    paid_memo              VARCHAR(200) NULL,
    created_at             DATETIME(6)  NOT NULL,
    created_by             BIGINT       NOT NULL,
    modified_at            DATETIME(6)  NOT NULL,
    modified_by            BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_seller_period UNIQUE (seller_id, period_start)
);

CREATE TABLE settlement_line (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    settlement_id          BIGINT       NOT NULL,
    seller_order_id        BIGINT       NOT NULL,
    order_id               BIGINT       NOT NULL,
    order_no               VARCHAR(32)  NOT NULL,
    confirmed_at           DATETIME(6)  NOT NULL,
    sales_amount           BIGINT       NOT NULL,
    seller_coupon_discount BIGINT       NOT NULL,
    commission_base        BIGINT       NOT NULL,
    commission_rate_bp     INT          NOT NULL,
    commission_amount      BIGINT       NOT NULL,
    shipping_fee_owed      BIGINT       NOT NULL,
    return_fee_borne       BIGINT       NOT NULL,
    line_amount            BIGINT       NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    created_by             BIGINT       NOT NULL,
    modified_at            DATETIME(6)  NOT NULL,
    modified_by            BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_line_seller_order UNIQUE (seller_order_id)
);

CREATE TABLE settlement_run (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    period_start DATE        NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    seller_count INT         NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    created_by   BIGINT      NOT NULL,
    modified_at  DATETIME(6) NOT NULL,
    modified_by  BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_run_period UNIQUE (period_start)
);

-- settlement_id NULL = 아직 어느 정산에도 붙지 않음. 붙이기는 조건부 UPDATE(settlement_id IS NULL)라 한 번만 붙는다.
CREATE TABLE settlement_adjustment (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id            BIGINT       NOT NULL,
    settlement_id        BIGINT       NULL,
    adjustment_type      VARCHAR(20)  NOT NULL,
    amount               BIGINT       NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    ref_seller_order_id  BIGINT       NULL,
    source_settlement_id BIGINT       NULL,
    created_by_admin_id  BIGINT       NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           BIGINT       NOT NULL,
    modified_at          DATETIME(6)  NOT NULL,
    modified_by          BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_adjustment_source UNIQUE (source_settlement_id)
);
