-- member 는 ID 로만 참조하므로 FK 를 두지 않는다.
-- 판매자에는 삭제 경로가 없어(정지·반려만) UNIQUE 가 소프트 딜리트와 충돌하지 않는다.
CREATE TABLE seller (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    member_id           BIGINT       NOT NULL,
    company_name        VARCHAR(100) NOT NULL,
    business_number     VARCHAR(12)  NOT NULL,
    representative_name VARCHAR(50)  NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    bank                VARCHAR(50)  NOT NULL,
    account_number      VARCHAR(50)  NOT NULL,
    account_holder      VARCHAR(50)  NOT NULL,
    commission_rate_bp  INT          NOT NULL,
    seller_status       VARCHAR(20)  NOT NULL,
    reject_reason       VARCHAR(500) NULL,
    suspend_reason      VARCHAR(500) NULL,
    created_at          DATETIME(6)  NOT NULL,
    created_by          BIGINT       NOT NULL,
    modified_at         DATETIME(6)  NOT NULL,
    modified_by         BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_seller_member_id UNIQUE (member_id),
    CONSTRAINT uk_seller_business_number UNIQUE (business_number)
);

-- 판매자·브랜드 심사 이력(target_type 으로 구분). 대상은 ID 로만 참조한다.
CREATE TABLE seller_review_history (
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
