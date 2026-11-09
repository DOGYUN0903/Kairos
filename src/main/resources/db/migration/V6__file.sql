-- 모든 업로드 파일의 기록. URL은 저장하지 않고 storage_key 로 만든다. 업로더는 ID 로만 참조한다.
CREATE TABLE file (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    storage_key        VARCHAR(255) NOT NULL,
    original_name      VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    uploader_member_id BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT       NOT NULL,
    modified_at        DATETIME(6)  NOT NULL,
    modified_by        BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_file_storage_key UNIQUE (storage_key)
);
