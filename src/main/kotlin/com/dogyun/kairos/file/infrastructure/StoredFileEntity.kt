package com.dogyun.kairos.file.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "file")
@SQLRestriction("status = 'ACTIVE'")
class StoredFileEntity(
    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    val storageKey: String,
    @Column(name = "original_name", nullable = false, updatable = false, length = 255)
    val originalName: String,
    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    val contentType: String,
    @Column(name = "size_bytes", nullable = false, updatable = false)
    val sizeBytes: Long,
    @Column(name = "uploader_member_id", nullable = false, updatable = false)
    val uploaderMemberId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
