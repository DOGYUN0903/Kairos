-- 상품·변경 요청 심사 이력(target_type 으로 구분). 대상은 ID 로만 참조한다.
CREATE TABLE product_review_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    result      VARCHAR(20)  NOT NULL,
    reason      VARCHAR(500) NULL,
    reviewer_id BIGINT       NOT NULL,
    reviewed_at DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NOT NULL,
    modified_at DATETIME(6)  NOT NULL,
    modified_by BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id)
);
