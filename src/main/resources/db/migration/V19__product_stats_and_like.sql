-- 상품 집계(좋아요 수·후기 수·별점 합)와 좋아요. 집계는 항상 증감 UPDATE 로만 바꾼다(엔티티 UPDATE 로 덮어쓰지 않는다).
-- average_rating 은 저장하지 않고 rating_sum / review_count 로 계산한다.
ALTER TABLE product
    ADD COLUMN like_count   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN review_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN rating_sum   BIGINT NOT NULL DEFAULT 0;

-- 좋아요. 취소는 행을 지우지 않고 liked = 0 으로 둔다(도메인 플래그라 status 와 별개). 회원·상품은 ID 로만 참조한다(FK 없음).
CREATE TABLE product_like (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    product_id  BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL,
    liked       BIT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    modified_by BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_like_product_member UNIQUE (product_id, member_id)
);
