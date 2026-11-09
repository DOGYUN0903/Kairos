package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.Brand
import com.dogyun.kairos.seller.domain.BrandId
import com.dogyun.kairos.seller.domain.BrandRepository
import com.dogyun.kairos.seller.domain.BrandStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class BrandRepositoryImpl(
    private val jpaRepository: BrandJpaRepository,
    private val imageJpaRepository: BrandImageJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : BrandRepository {

    override fun save(brand: Brand): Brand {
        val id = brand.id
        val entity = if (id == null) {
            BrandEntity(
                brand.sellerId, brand.name, brand.englishName, brand.description, brand.status, brand.rejectReason,
            )
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id.value)) { "존재하지 않는 브랜드입니다: ${id.value}" }.also {
                it.change(brand.name, brand.englishName, brand.description, brand.status, brand.rejectReason)
            }
        }
        val saved = try {
            jpaRepository.saveAndFlush(entity)
        } catch (e: DataIntegrityViolationException) {
            // 사전 검사를 통과한 동시 요청은 UNIQUE 제약이 막는다.
            throw DomainException(ErrorCode.BRAND_NAME_DUPLICATED)
        }
        saveLogo(saved.id, brand.logoFileId)
        return toDomain(saved, brand.logoFileId)
    }

    override fun findById(id: BrandId): Brand? =
        jpaRepository.findByIdOrNull(id.value)?.let { toDomain(it, logoFileIds(listOf(it.id))[it.id]) }

    override fun existsByName(name: String): Boolean = jpaRepository.existsByName(name)

    override fun findAllBySellerId(sellerId: Long): List<Brand> = toDomains(jpaRepository.findAllBySellerIdOrderByIdDesc(sellerId))

    override fun findPage(status: BrandStatus?, page: Int, size: Int): PageResult<Brand> {
        val brand = QBrandEntity.brandEntity
        // status가 null이면 where 조건이 붙지 않는다(QueryDSL where는 null을 무시한다).
        val where = status?.let { brand.brandStatus.eq(it) }
        val content = queryFactory.selectFrom(brand)
            .where(where)
            .orderBy(brand.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(brand.count()).from(brand).where(where).fetchOne() ?: 0L
        return PageResult.of(toDomains(content), page, size, total)
    }

    override fun findAllByIds(ids: Collection<Long>): List<Brand> =
        if (ids.isEmpty()) emptyList() else toDomains(jpaRepository.findAllById(ids))

    /** 기존 LOGO 행이 같은 파일이면 그대로 두고, 다르면 소프트 딜리트한 뒤 새 행을 넣는다. */
    private fun saveLogo(brandId: Long, logoFileId: Long?) {
        val current = imageJpaRepository.findAllByBrandIdInAndPurpose(listOf(brandId), BrandImageEntity.PURPOSE_LOGO)
        if (current.size == 1 && current.single().fileId == logoFileId) return
        current.forEach { it.softDelete() }
        imageJpaRepository.saveAll(current)
        if (logoFileId != null) {
            imageJpaRepository.save(BrandImageEntity(brandId, logoFileId, BrandImageEntity.PURPOSE_LOGO))
        }
    }

    private fun logoFileIds(brandIds: Collection<Long>): Map<Long, Long> =
        if (brandIds.isEmpty()) emptyMap()
        else imageJpaRepository.findAllByBrandIdInAndPurpose(brandIds, BrandImageEntity.PURPOSE_LOGO)
            .associate { it.brandId to it.fileId }

    private fun toDomains(entities: List<BrandEntity>): List<Brand> {
        val logos = logoFileIds(entities.map { it.id })
        return entities.map { toDomain(it, logos[it.id]) }
    }

    private fun toDomain(entity: BrandEntity, logoFileId: Long?) = Brand.restore(
        id = BrandId(entity.id),
        sellerId = entity.sellerId,
        name = entity.name,
        englishName = entity.englishName,
        logoFileId = logoFileId,
        description = entity.description,
        status = entity.brandStatus,
        rejectReason = entity.rejectReason,
    )
}
