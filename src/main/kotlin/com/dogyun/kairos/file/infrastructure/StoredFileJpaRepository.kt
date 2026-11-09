package com.dogyun.kairos.file.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface StoredFileJpaRepository : JpaRepository<StoredFileEntity, Long>
