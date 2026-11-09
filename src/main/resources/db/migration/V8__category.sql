-- 2단계 트리. 부모는 ID 로만 참조하므로 FK 를 두지 않는다. parent_id 가 NULL 이면 대분류.
CREATE TABLE category (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT      NULL,
    name          VARCHAR(50) NOT NULL,
    display_order INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BIGINT      NOT NULL,
    modified_at   DATETIME(6) NOT NULL,
    modified_by   BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
