package com.dogyun.kairos.cart.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "cart_item")
@SQLRestriction("status = 'ACTIVE'")
class CartItemEntity(
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "sku_id", nullable = false, updatable = false)
    val skuId: Long,
    quantity: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "quantity", nullable = false)
    var quantity: Int = quantity
        protected set

    fun changeQuantity(quantity: Int) {
        this.quantity = quantity
    }
}
