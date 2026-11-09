-- 판매 중인 상품의 내용·정가 변경 요청. 상품은 product_id 로만 참조한다(FK 없음).
-- content_json 은 infrastructure 전용 DTO(ProductContentJson) 를 직렬화한 것으로 이미지 file_id, 옵션·값(ID 포함)을 담는다.
-- 정가는 변경 요청으로만 바뀌므로 original_price 를 함께 둔다. 상품 하나에 PENDING 요청은 하나뿐이다(애플리케이션에서 보장).
CREATE TABLE product_change_request (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    product_id     BIGINT       NOT NULL,
    seller_id      BIGINT       NOT NULL,
    original_price BIGINT       NOT NULL,
    content_json   TEXT         NOT NULL,
    change_status  VARCHAR(20)  NOT NULL,
    reject_reason  VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    created_by     BIGINT       NOT NULL,
    modified_at    DATETIME(6)  NOT NULL,
    modified_by    BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id)
);
