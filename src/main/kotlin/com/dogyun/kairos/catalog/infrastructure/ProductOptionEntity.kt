package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/** 옵션 축(같은 애그리거트). */
@Entity
@Table(name = "product_option")
@SQLRestriction("status = 'ACTIVE'")
class ProductOptionEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    val product: ProductEntity,
    name: String,
    sortOrder: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "name", nullable = false, length = 50)
    var name: String = name
        protected set

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = sortOrder
        protected set

    fun change(name: String, sortOrder: Int) {
        this.name = name
        this.sortOrder = sortOrder
    }
}
