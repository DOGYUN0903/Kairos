-- 상품 애그리거트. 판매자·브랜드·카테고리는 ID 로만 참조하므로 FK 를 두지 않는다.
-- seller_id 는 브랜드의 판매자를 비정규화한 것으로 권한 검사·노출 조건에 쓴다.
-- on_sale_at 은 처음 승인될 때만 기록한다. sales_count 는 C2 에서 올린다.
CREATE TABLE product (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT       NOT NULL,
    brand_id       BIGINT       NOT NULL,
    category_id    BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    TEXT         NOT NULL,
    original_price BIGINT       NOT NULL,
    sale_price     BIGINT       NOT NULL,
    product_status VARCHAR(20)  NOT NULL,
    reject_reason  VARCHAR(500) NULL,
    on_sale_at     DATETIME(6)  NULL,
    sales_count    BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    created_by     BIGINT       NOT NULL,
    modified_at    DATETIME(6)  NOT NULL,
    modified_by    BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id)
);

-- 상품 이미지 연결. 파일은 file_id 로만 참조한다. display_order 0 이 대표 이미지(is_main).
CREATE TABLE product_image (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    product_id    BIGINT      NOT NULL,
    file_id       BIGINT      NOT NULL,
    display_order INT         NOT NULL,
    is_main       BIT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BIGINT      NOT NULL,
    modified_at   DATETIME(6) NOT NULL,
    modified_by   BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);

-- 옵션 축(최대 2개).
CREATE TABLE product_option (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    product_id  BIGINT      NOT NULL,
    name        VARCHAR(50) NOT NULL,
    sort_order  INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);

-- 옵션 값. SKU 조합은 이 ID 로 식별하므로 이름이 바뀌어도 같은 SKU 다.
CREATE TABLE product_option_value (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    option_id   BIGINT      NOT NULL,
    name        VARCHAR(50) NOT NULL,
    sort_order  INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);

-- 옵션 값 조합 하나. option_value_ids 는 정렬된 값 ID 를 '|' 로 이은 문자열(옵션이 없으면 '').
-- SKU 는 소프트 딜리트하지 않는다(스펙 13장). 사라진 조합은 sku_status = DISCONTINUED.
-- 재고 차감은 Phase 1 에서 락 없이 한다(CLAUDE.md Phase 규율).
CREATE TABLE sku (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    product_id       BIGINT       NOT NULL,
    option_value_ids VARCHAR(100) NOT NULL,
    extra_price      BIGINT       NOT NULL,
    stock            INT          NOT NULL,
    on_sale          BIT          NOT NULL,
    sku_status       VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NOT NULL,
    modified_at      DATETIME(6)  NOT NULL,
    modified_by      BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id)
);
