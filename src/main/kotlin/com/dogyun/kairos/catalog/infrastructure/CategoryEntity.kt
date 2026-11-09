package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "category")
@SQLRestriction("status = 'ACTIVE'")
class CategoryEntity(
    @Column(name = "parent_id", updatable = false)
    val parentId: Long?,
    name: String,
    displayOrder: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "name", nullable = false, length = 50)
    var name: String = name
        protected set

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = displayOrder
        protected set

    fun change(name: String, displayOrder: Int) {
        this.name = name
        this.displayOrder = displayOrder
    }
}
