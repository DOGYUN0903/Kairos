package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.common.infrastructure.EntityStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "review")
@SQLRestriction("status = 'ACTIVE'")
class ReviewEntity(
    @Column(name = "order_item_id", nullable = false, updatable = false)
    val orderItemId: Long,

    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long,

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    val productName: String,

    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,

    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,

    rating: Int,
    content: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "rating", nullable = false)
    var rating: Int = rating
        protected set

    @Column(name = "content", nullable = false, length = 1000)
    var content: String = content
        protected set

    @Column(name = "reply_content", length = 500)
    var replyContent: String? = null
        protected set

    @Column(name = "replied_at")
    var repliedAt: LocalDateTime? = null
        protected set

    @Column(name = "reward_points", nullable = false)
    var rewardPoints: Long = 0L
        protected set

    /**
     * 사진은 같은 애그리거트(스펙 10장)라 양방향 `@OneToMany` 로 함께 저장·조회한다(후기 없이 사진만 다루는 경로가 없다).
     * 물리 삭제 금지 규칙 때문에 컬렉션에서 빼지 않고 [ReviewImageEntity.softDelete] 로 지운다. 실수로 물리 DELETE 가 나가지 않도록
     * `orphanRemoval` 은 쓰지 않는다. 조회 시 삭제된 사진은 [ReviewImageEntity] 의 `@SQLRestriction` 이 거른다.
     */
    @OneToMany(mappedBy = "review", cascade = [CascadeType.ALL])
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 100)
    val images: MutableList<ReviewImageEntity> = mutableListOf()

    fun update(rating: Int, content: String, replyContent: String?, repliedAt: LocalDateTime?, rewardPoints: Long) {
        this.rating = rating
        this.content = content
        this.replyContent = replyContent
        this.repliedAt = repliedAt
        this.rewardPoints = rewardPoints
    }

    /** 사진 구성이 바뀌었으면 이전 사진을 소프트 삭제하고 새로 붙인다. */
    fun replaceImages(next: List<Pair<Long, Int>>) {
        val current = images.filter { it.status == EntityStatus.ACTIVE }
        if (current.map { it.fileId to it.displayOrder } == next) return
        current.forEach { it.softDelete() }
        next.forEach { (fileId, order) -> images.add(ReviewImageEntity(this, fileId, order)) }
    }

    fun activeImages(): List<ReviewImageEntity> =
        images.filter { it.status == EntityStatus.ACTIVE }
}
