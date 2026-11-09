package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
import com.dogyun.kairos.catalog.domain.ProductChangeRequest
import com.dogyun.kairos.catalog.domain.ProductChangeRequestRepository
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper

/** 요청 내용은 [ProductContentJson] 으로 옮겨 적어 content_json 에 저장한다. 상태·반려 사유만 갱신된다. */
@Repository
class ProductChangeRequestRepositoryImpl(
    private val jpaRepository: ProductChangeRequestJpaRepository,
    private val objectMapper: ObjectMapper,
) : ProductChangeRequestRepository {

    override fun save(changeRequest: ProductChangeRequest): ProductChangeRequest {
        val id = changeRequest.id
        val entity = if (id == null) {
            ProductChangeRequestEntity(
                changeRequest.productId, changeRequest.sellerId, changeRequest.originalPrice,
                objectMapper.writeValueAsString(ProductContentJson.from(changeRequest.content)),
                changeRequest.status, changeRequest.rejectReason,
            )
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id)) { "존재하지 않는 변경 요청입니다: $id" }
                .also { it.change(changeRequest.status, changeRequest.rejectReason) }
        }
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findById(id: Long): ProductChangeRequest? = jpaRepository.findByIdOrNull(id)?.let(::toDomain)

    override fun findPendingByProductId(productId: Long): ProductChangeRequest? =
        jpaRepository.findFirstByProductIdAndChangeStatus(productId, ChangeRequestStatus.PENDING)?.let(::toDomain)

    override fun findPage(status: ChangeRequestStatus?, page: Int, size: Int): PageResult<ProductChangeRequest> {
        val pageable = PageRequest.of(page, size)
        val result = if (status == null) jpaRepository.findAllByOrderByIdDesc(pageable)
        else jpaRepository.findAllByChangeStatusOrderByIdDesc(status, pageable)
        return PageResult.of(result.content.map(::toDomain), page, size, result.totalElements)
    }

    private fun toDomain(entity: ProductChangeRequestEntity) = ProductChangeRequest.restore(
        id = entity.id,
        productId = entity.productId,
        sellerId = entity.sellerId,
        content = objectMapper.readValue(entity.contentJson, ProductContentJson::class.java).toDomain(),
        originalPrice = entity.originalPrice,
        status = entity.changeStatus,
        rejectReason = entity.rejectReason,
    )
}
