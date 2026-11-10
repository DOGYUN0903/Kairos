-- 후기. 구매 확정된 주문 항목당 하나이며, 소프트 삭제한 행도 UNIQUE 를 차지해 삭제 후 재작성(재적립)을 막는다.
-- 판매자 답글은 하나뿐이라 컬럼으로 둔다. product_name 은 작성 시점 주문 항목 상품명 스냅샷이다.
-- product_id 조회 인덱스는 일부러 두지 않는다(PK/UNIQUE 외 인덱스 금지, 3-2 측정 후보).
CREATE TABLE review (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_item_id  BIGINT        NOT NULL,
    product_id     BIGINT        NOT NULL,
    product_name   VARCHAR(200)  NOT NULL,
    member_id      BIGINT        NOT NULL,
    seller_id      BIGINT        NOT NULL,
    rating         INT           NOT NULL,
    content        VARCHAR(1000) NOT NULL,
    reply_content  VARCHAR(500)  NULL,
    replied_at     DATETIME(6)   NULL,
    reward_points  BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    created_by     BIGINT        NOT NULL,
    modified_at    DATETIME(6)   NOT NULL,
    modified_by    BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_order_item UNIQUE (order_item_id)
);

-- 후기 사진(같은 애그리거트). 수정 시 이전 사진은 소프트 삭제한다.
CREATE TABLE review_image (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    review_id     BIGINT      NOT NULL,
    file_id       BIGINT      NOT NULL,
    display_order INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BIGINT      NOT NULL,
    modified_at   DATETIME(6) NOT NULL,
    modified_by   BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_image_review FOREIGN KEY (review_id) REFERENCES review (id)
);
