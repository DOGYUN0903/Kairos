-- C3 혜택: 주문·주문 항목에 쿠폰 할인, 포인트 사용, 항목별 실제 결제액(paid_amount)을 저장한다.
-- 쿠폰 ID 는 benefit 테이블을 FK 없이 ID 로만 참조한다. C2 는 배포 전이라 기존 행이 없고 DEFAULT 0 은 마이그레이션 편의용이다.
ALTER TABLE orders
    ADD COLUMN product_coupon_discount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cart_coupon_id          BIGINT NULL,
    ADD COLUMN cart_coupon_discount    BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN points_used             BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN points_refunded         BIGINT NOT NULL DEFAULT 0;

ALTER TABLE order_item
    ADD COLUMN product_coupon_id       BIGINT NULL,
    ADD COLUMN product_coupon_discount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN product_coupon_issuer   VARCHAR(20) NULL,
    ADD COLUMN cart_coupon_allocated   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN points_allocated        BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN paid_amount             BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN points_refunded         BIGINT NOT NULL DEFAULT 0;
