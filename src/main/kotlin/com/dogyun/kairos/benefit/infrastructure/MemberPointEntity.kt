package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "member_point")
@SQLRestriction("status = 'ACTIVE'")
class MemberPointEntity(
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    balance: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "balance", nullable = false)
    val balance: Long = balance
}
