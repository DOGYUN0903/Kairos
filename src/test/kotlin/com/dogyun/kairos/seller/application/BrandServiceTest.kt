package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.seller.domain.BusinessNumber
import com.dogyun.kairos.seller.domain.Brand
import com.dogyun.kairos.seller.domain.BrandId
import com.dogyun.kairos.seller.domain.BrandRepository
import com.dogyun.kairos.seller.domain.BrandStatus
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SellerStatus
import com.dogyun.kairos.seller.domain.SettlementAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BrandServiceTest {

    private class FakeBrandRepository : BrandRepository {
        private val store = linkedMapOf<Long, Brand>()
        private var sequence = 0L

        override fun save(brand: Brand): Brand {
            val id = brand.id ?: BrandId(++sequence)
            val saved = Brand.restore(
                id, brand.sellerId, brand.name, brand.englishName, brand.logoFileId,
                brand.description, brand.status, brand.rejectReason,
            )
            store[id.value] = saved
            return saved
        }

        override fun findById(id: BrandId): Brand? = store[id.value]
        override fun existsByName(name: String): Boolean = store.values.any { it.name == name }
        override fun findAllBySellerId(sellerId: Long): List<Brand> = store.values.filter { it.sellerId == sellerId }
        override fun findPage(status: BrandStatus?, page: Int, size: Int): PageResult<Brand> {
            val all = store.values.filter { status == null || it.status == status }
            return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
        }

        override fun findAllByIds(ids: Collection<Long>): List<Brand> = store.values.filter { it.id!!.value in ids }
    }

    private class FakeSellerRepository : SellerRepository {
        val store = linkedMapOf<Long, Seller>()

        override fun save(seller: Seller): Seller {
            val id = seller.id ?: SellerId(store.size + 1L)
            val saved = Seller.restore(
                id, seller.memberId, seller.companyName, seller.businessNumber, seller.representativeName,
                seller.phone, seller.settlementAccount, seller.commissionRateBp, seller.status,
                seller.rejectReason, seller.suspendReason,
            )
            store[id.value] = saved
            return saved
        }

        override fun findById(id: SellerId): Seller? = store[id.value]
        override fun findAllByIds(ids: Collection<SellerId>): List<Seller> = ids.mapNotNull { store[it.value] }
        override fun findByMemberId(memberId: Long): Seller? = store.values.firstOrNull { it.memberId == memberId }
        override fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean = false
        override fun findPage(status: SellerStatus?, page: Int, size: Int): PageResult<Seller> =
            PageResult.of(store.values.toList(), page, size, store.size.toLong())
    }

    private class FakeImageFileReader(private val files: Map<Long, ImageFileRef>) : ImageFileReader {
        override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> =
            files.filterKeys { it in fileIds }
    }

    private val brands = FakeBrandRepository()
    private val sellers = FakeSellerRepository()
    private val files = FakeImageFileReader(
        mapOf(
            10L to ImageFileRef(10L, "http://files/a.png", uploaderMemberId = 1L, isImage = true),
            11L to ImageFileRef(11L, "http://files/b.png", uploaderMemberId = 2L, isImage = true),
        ),
    )
    private val service = BrandService(brands, files)
    private val query = BrandQueryService(brands, sellers, files)

    private val me = SellerPrincipal(sellerId = 1L, memberId = 1L, canManage = true, canView = true)
    private val other = SellerPrincipal(sellerId = 2L, memberId = 2L, canManage = true, canView = true)

    private fun command(name: String = "키로스", logoFileId: Long? = null) =
        BrandApplicationCommand(name, null, logoFileId, null)

    private fun saveSeller(): Seller = sellers.save(
        Seller.apply(
            1L, "키로스상사", BusinessNumber("123-45-67890"), "홍길동", "010-1234-5678",
            SettlementAccount("국민", "123456-01-123456", "홍길동"),
        ).also { it.approve(1000) },
    )

    @Test
    fun 이름_중복은_BRAND_NAME_DUPLICATED() {
        service.apply(me, command("키로스"))

        val e = assertFailsWith<DomainException> { service.apply(other, command("키로스")) }

        assertEquals(ErrorCode.BRAND_NAME_DUPLICATED, e.errorCode)
    }

    @Test
    fun 다른_회원이_올린_로고는_FILE_NOT_OWNED() {
        val e = assertFailsWith<DomainException> { service.apply(me, command(logoFileId = 11L)) }

        assertEquals(ErrorCode.FILE_NOT_OWNED, e.errorCode)
    }

    @Test
    fun 없는_파일_ID는_FILE_NOT_OWNED() {
        val e = assertFailsWith<DomainException> { service.apply(me, command(logoFileId = 999L)) }

        assertEquals(ErrorCode.FILE_NOT_OWNED, e.errorCode)
    }

    @Test
    fun 다른_판매자의_브랜드_재신청은_BRAND_NOT_FOUND() {
        val brandId = service.apply(me, command())

        val e = assertFailsWith<DomainException> { service.reapply(other, brandId, command("새이름")) }

        assertEquals(ErrorCode.BRAND_NOT_FOUND, e.errorCode)
    }

    @Test
    fun 공개_조회는_판매자가_정지되면_BRAND_NOT_FOUND() {
        val seller = saveSeller()
        val brandId = service.apply(me, command())
        brands.save(brands.findById(BrandId(brandId))!!.also { it.approve() })
        assertEquals("키로스", query.getPublic(brandId).name)

        sellers.save(seller.also { it.suspend("약관 위반") })

        val e = assertFailsWith<DomainException> { query.getPublic(brandId) }
        assertEquals(ErrorCode.BRAND_NOT_FOUND, e.errorCode)
    }

    @Test
    fun 상세의_logoUrl은_파일_모듈이_준_URL이다() {
        service.apply(me, command(logoFileId = 10L))

        val detail = service.listMine(me).single()

        assertEquals("http://files/a.png", detail.logoUrl)
        assertEquals("PENDING", detail.status)
    }
}
