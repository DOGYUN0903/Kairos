package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ProductLikeJpaRepository : JpaRepository<ProductLikeEntity, Long> {

    fun findAllByMemberIdAndProductIdInAndLikedTrue(memberId: Long, productIds: Collection<Long>): List<ProductLikeEntity>

    /**
     * 행이 없으면 취소 상태(`liked = 0`)로 만들고, 있으면 아무것도 바꾸지 않는다. 어느 쪽이든 이 트랜잭션이 그 행의 X 락을 쥔다.
     *
     * 단일 `INSERT ... ON DUPLICATE KEY UPDATE liked = 1` 의 영향 행 수로 "신규/변경/무변경"을 가르려 했으나, MySQL Connector/J 는
     * 기본으로 CLIENT_FOUND_ROWS 를 켜서 무변경도 1 로 돌려준다(측정으로 확인). 또 없는 키에 대한 UPDATE 다음 INSERT 는
     * gap lock 때문에, INSERT IGNORE 다음 UPDATE 는 S→X 승격 때문에 동시 요청에서 교착한다(측정으로 확인).
     * 그래서 ODKU 로 행과 X 락을 먼저 확보한 뒤([reactivate]) 조건부 UPDATE 의 영향 행 수로 상태 전이를 판정한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        INSERT INTO product_like (product_id, member_id, liked, created_at, created_by, modified_at, modified_by, status)
        VALUES (:productId, :memberId, 0, :now, :memberId, :now, :memberId, 'ACTIVE')
        ON DUPLICATE KEY UPDATE id = id
        """,
        nativeQuery = true,
    )
    fun ensureRow(@Param("productId") productId: Long, @Param("memberId") memberId: Long, @Param("now") now: LocalDateTime): Int

    /** 취소 상태였던 행을 좋아요로 바꾼다. 영향 행 1 = 상태가 바뀜. `modified_at` 은 내 좋아요 목록의 `likedAt` 이다. */
    @Modifying(flushAutomatically = true)
    @Query(
        "UPDATE product_like SET liked = 1, modified_at = :now, modified_by = :memberId WHERE product_id = :productId AND member_id = :memberId AND liked = 0",
        nativeQuery = true,
    )
    fun reactivate(@Param("productId") productId: Long, @Param("memberId") memberId: Long, @Param("now") now: LocalDateTime): Int

    /** 좋아요였을 때만 1행. */
    @Modifying(flushAutomatically = true)
    @Query(
        "UPDATE product_like SET liked = 0, modified_at = :now WHERE product_id = :productId AND member_id = :memberId AND liked = 1",
        nativeQuery = true,
    )
    fun unlike(@Param("productId") productId: Long, @Param("memberId") memberId: Long, @Param("now") now: LocalDateTime): Int
}
