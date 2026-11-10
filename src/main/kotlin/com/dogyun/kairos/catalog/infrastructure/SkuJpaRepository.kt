package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SkuJpaRepository : JpaRepository<SkuEntity, Long> {
    fun findAllByProductIdInOrderByIdAsc(productIds: Collection<Long>): List<SkuEntity>
    fun findAllByIdIn(ids: Collection<Long>): List<SkuEntity>

    /** 재고가 충분할 때만 차감한다. 영향 행이 0 이면 재고 부족(또는 없는 SKU). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SkuEntity s set s.stock = s.stock - :quantity where s.id = :skuId and s.stock >= :quantity")
    fun decreaseIfEnough(@Param("skuId") skuId: Long, @Param("quantity") quantity: Int): Int

    /** 취소·만료 복구. DISCONTINUED SKU 도 되돌린다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SkuEntity s set s.stock = s.stock + :quantity where s.id = :skuId")
    fun increase(@Param("skuId") skuId: Long, @Param("quantity") quantity: Int): Int
}
