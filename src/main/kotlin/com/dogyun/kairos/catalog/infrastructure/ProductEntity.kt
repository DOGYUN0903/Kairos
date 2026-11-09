package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "product")
@SQLRestriction("status = 'ACTIVE'")
class ProductEntity(
    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,

    @Column(name = "brand_id", nullable = false, updatable = false)
    val brandId: Long,
    categoryId: Long,
    name: String,
    description: String,
    originalPrice: Long,
    salePrice: Long,
    productStatus: ProductStatus,
    rejectReason: String?,
    onSaleAt: LocalDateTime?,
    salesCount: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "category_id", nullable = false)
    var categoryId: Long = categoryId
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String = description
        protected set

    @Column(name = "original_price", nullable = false)
    var originalPrice: Long = originalPrice
        protected set

    @Column(name = "sale_price", nullable = false)
    var salePrice: Long = salePrice
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "product_status", nullable = false, length = 20)
    var productStatus: ProductStatus = productStatus
        protected set

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = rejectReason
        protected set

    @Column(name = "on_sale_at")
    var onSaleAt: LocalDateTime? = onSaleAt
        protected set

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = salesCount
        protected set

    /** salesCount 는 주문 쪽에서만 증가하므로 변경 대상이 아니다(오래된 스냅샷으로 덮어쓰지 않는다). */
    fun change(
        categoryId: Long,
        name: String,
        description: String,
        originalPrice: Long,
        salePrice: Long,
        productStatus: ProductStatus,
        rejectReason: String?,
        onSaleAt: LocalDateTime?,
    ) {
        this.categoryId = categoryId
        this.name = name
        this.description = description
        this.originalPrice = originalPrice
        this.salePrice = salePrice
        this.productStatus = productStatus
        this.rejectReason = rejectReason
        this.onSaleAt = onSaleAt
    }
}
