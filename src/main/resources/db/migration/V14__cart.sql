-- 서버 저장 장바구니. member·sku 는 ID 로만 참조한다(FK 없음). 항목 삭제는 소프트 딜리트(status = DELETED).
CREATE TABLE cart_item (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    sku_id      BIGINT      NOT NULL,
    quantity    INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
