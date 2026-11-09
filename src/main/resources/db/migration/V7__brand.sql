-- 판매자는 ID 로만 참조하므로 FK 를 두지 않는다.
-- 브랜드에는 삭제 경로가 없어(반려만) name UNIQUE 가 소프트 딜리트와 충돌하지 않는다.
CREATE TABLE brand (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id     BIGINT        NOT NULL,
    name          VARCHAR(50)   NOT NULL,
    english_name  VARCHAR(100)  NULL,
    description   VARCHAR(1000) NULL,
    brand_status  VARCHAR(20)   NOT NULL,
    reject_reason VARCHAR(500)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    created_by    BIGINT        NOT NULL,
    modified_at   DATETIME(6)   NOT NULL,
    modified_by   BIGINT        NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_brand_name UNIQUE (name)
);

-- 브랜드 이미지 연결. 파일은 file_id 로만 참조한다.
CREATE TABLE brand_image (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    brand_id    BIGINT      NOT NULL,
    file_id     BIGINT      NOT NULL,
    purpose     VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
