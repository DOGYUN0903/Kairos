package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
import com.dogyun.kairos.catalog.domain.ProductChangeRequest
import com.dogyun.kairos.catalog.domain.ProductChangeRequestRepository
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult

internal class FakeProductChangeRequestRepository : ProductChangeRequestRepository {
    val store = linkedMapOf<Long, ProductChangeRequest>()
    private var sequence = 0L

    override fun save(changeRequest: ProductChangeRequest): ProductChangeRequest {
        val id = changeRequest.id ?: ++sequence
        val saved = ProductChangeRequest.restore(
            id, changeRequest.productId, changeRequest.sellerId, changeRequest.content, changeRequest.originalPrice,
            changeRequest.status, changeRequest.rejectReason, createdAt = java.time.LocalDateTime.of(2026, 11, 9, 9, 0),
        )
        store[id] = saved
        return saved
    }

    override fun findById(id: Long): ProductChangeRequest? = store[id]

    override fun findPendingByProductId(productId: Long): ProductChangeRequest? =
        store.values.firstOrNull { it.productId == productId && it.status == ChangeRequestStatus.PENDING }

    override fun findLatestByProductId(productId: Long): ProductChangeRequest? =
        store.values.filter { it.productId == productId }.maxByOrNull { requireNotNull(it.id) }

    override fun findPendingProductIds(productIds: Collection<Long>): Set<Long> =
        store.values.filter { it.productId in productIds && it.status == ChangeRequestStatus.PENDING }.map { it.productId }.toSet()

    override fun findPage(status: ChangeRequestStatus?, order: ListOrder, page: Int, size: Int): PageResult<ProductChangeRequest> {
        val all = store.values.filter { status == null || it.status == status }.let { if (order == ListOrder.OLDEST) it else it.reversed() }
        return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
    }
}
