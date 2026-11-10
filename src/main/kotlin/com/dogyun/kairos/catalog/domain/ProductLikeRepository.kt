package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

/** 내 좋아요 목록 한 줄. 삭제·판매 종료 상품도 포함하며 [productStatus] 로 구분한다. */
data class LikedProductRow(
    val productId: Long,
    val name: String,
    val brandName: String,
    val mainFileId: Long,
    val salePrice: Long,
    val productStatus: String,
    val likedAt: LocalDateTime,
)

/** 상품 좋아요. 쓰기는 조건부 SQL 의 영향 행 수로 "상태가 실제로 바뀌었는지"를 돌려준다(멱등 PUT/DELETE). */
interface ProductLikeRepository {
    /** 좋아요로 만든다. 새로 생겼거나 취소 상태에서 바뀌었으면 true, 이미 좋아요였으면 false. */
    fun like(productId: Long, memberId: Long): Boolean

    /** 좋아요를 취소한다. 좋아요였을 때만 true. */
    fun unlike(productId: Long, memberId: Long): Boolean

    fun findLikedProductIds(memberId: Long, productIds: Collection<Long>): Set<Long>

    fun findPageLiked(memberId: Long, page: Int, size: Int): PageResult<LikedProductRow>
}
