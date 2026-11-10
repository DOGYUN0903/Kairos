package com.dogyun.kairos.review.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

/** 사진은 [ReviewEntity.images] 로 저장·조회한다. 테스트·운영 확인용 단순 조회만 둔다. */
interface ReviewImageJpaRepository : JpaRepository<ReviewImageEntity, Long>
