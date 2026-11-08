CREATE TABLE deal (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    product_name     VARCHAR(255) NOT NULL,
    original_price   BIGINT       NOT NULL,
    deal_price       BIGINT       NOT NULL,
    total_quantity   INT          NOT NULL,
    per_member_limit INT          NOT NULL,
    open_at          DATETIME(6)  NOT NULL,
    close_at         DATETIME(6)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NOT NULL,
    modified_at      DATETIME(6)  NOT NULL,
    modified_by      BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id)
);

-- 애그리거트끼리는 ID로만 참조하므로 FK 제약을 두지 않는다.
CREATE TABLE stock (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    deal_id            BIGINT      NOT NULL,
    total_quantity     INT         NOT NULL,
    remaining_quantity INT         NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    created_by         BIGINT      NOT NULL,
    modified_at        DATETIME(6) NOT NULL,
    modified_by        BIGINT      NOT NULL,
    status             VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_deal_id UNIQUE (deal_id)
);
