package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.SkuStatus
import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/** 옵션 값 조합 하나(같은 애그리거트). 소프트 딜리트하지 않고 sku_status 로 단종을 표현한다(스펙 13장). */
@Entity
@Table(name = "sku")
@SQLRestriction("status = 'ACTIVE'")
class SkuEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    val product: ProductEntity,

    /** 정렬된 옵션 값 ID 를 '|' 로 이은 문자열. 옵션이 없으면 빈 문자열. */
    @Column(name = "option_value_ids", nullable = false, length = 100, updatable = false)
    val optionValueIds: String,
    extraPrice: Long,
    stock: Int,
    onSale: Boolean,
    skuStatus: SkuStatus,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "extra_price", nullable = false)
    var extraPrice: Long = extraPrice
        protected set

    @Column(name = "stock", nullable = false)
    var stock: Int = stock
        protected set

    @Column(name = "on_sale", nullable = false)
    var onSale: Boolean = onSale
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "sku_status", nullable = false, length = 20)
    var skuStatus: SkuStatus = skuStatus
        protected set

    fun change(extraPrice: Long, stock: Int, onSale: Boolean, skuStatus: SkuStatus) {
        this.extraPrice = extraPrice
        this.stock = stock
        this.onSale = onSale
        this.skuStatus = skuStatus
    }
}
