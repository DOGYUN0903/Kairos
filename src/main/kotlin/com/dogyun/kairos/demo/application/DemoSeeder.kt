package com.dogyun.kairos.demo.application

import com.dogyun.kairos.catalog.application.CategoryNode
import com.dogyun.kairos.catalog.application.CategoryQueryService
import com.dogyun.kairos.catalog.application.CategoryService
import com.dogyun.kairos.catalog.application.CreateCategoryCommand
import com.dogyun.kairos.catalog.application.OptionCommand
import com.dogyun.kairos.catalog.application.OptionValueCommand
import com.dogyun.kairos.catalog.application.ProductAdminService
import com.dogyun.kairos.catalog.application.ProductContentCommand
import com.dogyun.kairos.catalog.application.ProductQueryService
import com.dogyun.kairos.catalog.application.ProductService
import com.dogyun.kairos.catalog.application.RegisterProductCommand
import com.dogyun.kairos.catalog.application.SellerProductSummary
import com.dogyun.kairos.catalog.application.UpdateSkuCommand
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.file.application.FileService
import com.dogyun.kairos.member.application.MemberQueryService
import com.dogyun.kairos.member.application.SignUpCommand
import com.dogyun.kairos.member.application.SignUpService
import com.dogyun.kairos.seller.application.BrandAdminService
import com.dogyun.kairos.seller.application.BrandApplicationCommand
import com.dogyun.kairos.seller.application.BrandDetail
import com.dogyun.kairos.seller.application.BrandService
import com.dogyun.kairos.seller.application.SellerAdminService
import com.dogyun.kairos.seller.application.SellerApplicationCommand
import com.dogyun.kairos.seller.application.SellerQueryService
import com.dogyun.kairos.seller.application.SellerService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * demo 프로필 데이터 시더. 부족한 것만 만드는 멱등 보충이며 아무것도 지우지 않는다.
 * `@Transactional` 을 두지 않는다 — 각 모듈 서비스가 자기 트랜잭션으로 처리한다.
 *
 * 멱등 기준: 계정은 이메일, 판매자는 회원의 판매자 존재, 브랜드는 데모 판매자의 브랜드명,
 * 카테고리는 이름(대분류·소분류), 상품은 데모 브랜드의 ON_SALE 상품 수(20 미만이면 부족분),
 * 검수 대기는 PENDING_REVIEW 상품이 0이면 3개.
 */
@Service
@Profile("demo")
class DemoSeeder(
    private val properties: DemoProperties,
    private val signUpService: SignUpService,
    private val memberQueryService: MemberQueryService,
    private val sellerService: SellerService,
    private val sellerAdminService: SellerAdminService,
    private val sellerQueryService: SellerQueryService,
    private val brandService: BrandService,
    private val brandAdminService: BrandAdminService,
    private val categoryService: CategoryService,
    private val categoryQueryService: CategoryQueryService,
    private val fileService: FileService,
    private val productService: ProductService,
    private val productAdminService: ProductAdminService,
    private val productQueryService: ProductQueryService,
) {

    /** 동시 호출로 부족분을 두 번 만들지 않도록 한 인스턴스 안에서 직렬화한다(데모는 단일 인스턴스). */
    @Synchronized
    fun seed(): DemoReseedResult {
        val counter = Counter()
        val adminId = ensureMember(DemoAccounts.ADMIN, "데모 관리자", admin = true, counter)
        ensureMember(DemoAccounts.BUYER, "데모 구매자", admin = false, counter)
        ensureMember(DemoAccounts.SETTLEMENT_BUYER, "정산 시연 구매자", admin = false, counter)
        val sellerMemberId = ensureMember(DemoAccounts.SELLER, "데모 판매자", admin = false, counter)
        val pendingSellerMemberId = ensureMember(DemoAccounts.SELLER_PENDING, "입점 대기 판매자", admin = false, counter)

        val leafIds = ensureCategories(counter)

        val seller = ensureApprovedSeller(sellerMemberId, adminId, counter)
        ensurePendingSeller(pendingSellerMemberId, counter)
        if (seller == null) {
            log.warn { "데모 판매자가 승인 상태가 아니어서 브랜드·상품 시드를 건너뜁니다" }
            return counter.result()
        }

        val brand = ensureBrands(seller, adminId, counter)
        if (brand == null) {
            log.warn { "데모 브랜드가 승인 상태가 아니어서 상품 시드를 건너뜁니다" }
            return counter.result()
        }
        // 정산 시연 상품을 먼저 만든다(상품 id 가 작아 판매자 상품 목록에서 데모 상품보다 뒤에 나온다).
        ensureSettlementProducts(seller, adminId, leafIds, counter)
        ensureProducts(seller, brand, leafIds, adminId, counter)
        return counter.result()
    }

    private fun ensureMember(email: String, name: String, admin: Boolean, counter: Counter): Long {
        memberQueryService.findIdByEmail(email)?.let { return it }
        val command = SignUpCommand(email, properties.password, name)
        counter.members++
        return if (admin) signUpService.signUpAdmin(command) else signUpService.signUp(command)
    }

    /** (대분류, 소분류) → 소분류 ID. */
    private fun ensureCategories(counter: Counter): Map<Pair<String, String>, Long> {
        val leafIds = mutableMapOf<Pair<String, String>, Long>()
        var roots: List<CategoryNode> = categoryQueryService.tree()
        DEMO_CATEGORIES.forEachIndexed { rootIndex, (rootName, childNames) ->
            val root = roots.firstOrNull { it.name == rootName }
            val rootId = root?.categoryId ?: categoryService.create(CreateCategoryCommand(null, rootName, rootIndex + 1))
                .also { counter.categories++ }
            childNames.forEachIndexed { childIndex, childName ->
                val childId = root?.children?.firstOrNull { it.name == childName }?.categoryId
                    ?: categoryService.create(CreateCategoryCommand(rootId, childName, childIndex + 1)).also { counter.categories++ }
                leafIds[rootName to childName] = childId
            }
        }
        return leafIds
    }

    private fun ensureApprovedSeller(memberId: Long, adminId: Long, counter: Counter): SellerPrincipal? {
        if (sellerQueryService.findSnapshotByMemberId(memberId) == null) {
            sellerService.apply(memberId, sellerApplication("카이로스 데모 스토어", "220-81-00001", "김데모"))
            counter.sellers++
        }
        val detail = sellerQueryService.getMine(memberId)
        if (detail.status == PENDING) sellerAdminService.approve(detail.sellerId, COMMISSION_RATE_BP, adminId)
        val snapshot = requireNotNull(sellerQueryService.findSnapshotByMemberId(memberId))
        if (!snapshot.canManage) return null
        return SellerPrincipal(snapshot.sellerId, snapshot.memberId, snapshot.canManage, snapshot.canView)
    }

    private fun ensurePendingSeller(memberId: Long, counter: Counter) {
        if (sellerQueryService.findSnapshotByMemberId(memberId) != null) return
        sellerService.apply(memberId, sellerApplication("오늘의 편집숍", "220-81-00002", "이대기"))
        counter.sellers++
    }

    /** 승인 브랜드("카이로스 데모")와 대기 브랜드("KAIROS DEMO")를 맞춘다. 승인 브랜드가 쓸 수 없으면 null. */
    private fun ensureBrands(seller: SellerPrincipal, adminId: Long, counter: Counter): BrandDetail? {
        val mine = brandService.listMine(seller)
        if (mine.none { it.name == PENDING_BRAND }) {
            brandService.apply(seller, BrandApplicationCommand(PENDING_BRAND, "KAIROS DEMO STUDIO", null, "심사 대기 중인 데모 브랜드입니다."))
            counter.brands++
        }
        var brand = mine.firstOrNull { it.name == MAIN_BRAND }
        if (brand == null) {
            brandService.apply(
                seller,
                BrandApplicationCommand(MAIN_BRAND, "KAIROS", null, "매일 입기 좋은 베이직 웨어를 만드는 데모 브랜드입니다."),
            )
            counter.brands++
            brand = brandService.listMine(seller).first { it.name == MAIN_BRAND }
        }
        if (brand.status == PENDING) {
            brandAdminService.approve(brand.brandId, adminId)
            brand = brandService.listMine(seller).first { it.name == MAIN_BRAND }
        }
        return brand.takeIf { it.status == APPROVED }
    }

    /** 정산 시연 전용 브랜드와 상품 5종(판매 중)을 이름 기준으로 보충한다. 기존 "판매 중 20개" 계산에는 영향이 없다. */
    private fun ensureSettlementProducts(
        seller: SellerPrincipal,
        adminId: Long,
        leafIds: Map<Pair<String, String>, Long>,
        counter: Counter,
    ) {
        var brand = brandService.listMine(seller).firstOrNull { it.name == SETTLEMENT_DEMO_BRAND }
        if (brand == null) {
            brandService.apply(seller, BrandApplicationCommand(SETTLEMENT_DEMO_BRAND, "KAIROS SETTLEMENT DEMO", null, "정산 시연 주문에 쓰는 데모 브랜드입니다."))
            counter.brands++
            brand = brandService.listMine(seller).first { it.name == SETTLEMENT_DEMO_BRAND }
        }
        if (brand.status == PENDING) {
            brandAdminService.approve(brand.brandId, adminId)
            brand = brandService.listMine(seller).first { it.name == SETTLEMENT_DEMO_BRAND }
        }
        if (brand.status != APPROVED) {
            log.warn { "정산 시연 브랜드가 승인 상태가 아니어서 정산 시연 상품 시드를 건너뜁니다" }
            return
        }
        val existing = listAllMine(seller).filter { it.brandName == SETTLEMENT_DEMO_BRAND && it.status == ON_SALE }.map { it.name }.toSet()
        val missing = DEMO_SETTLEMENT_PRODUCTS.filter { it.name !in existing }
        if (missing.isEmpty()) return
        val images = uploadImages(seller.memberId)
        missing.forEachIndexed { i, spec ->
            val productId = register(seller, brand.brandId, spec, leafIds, images, i)
            productAdminService.approve(productId, adminId)
            fillStock(seller, productId, stock = SETTLEMENT_STOCK)
            counter.products++
        }
    }

    private fun ensureProducts(
        seller: SellerPrincipal,
        brand: BrandDetail,
        leafIds: Map<Pair<String, String>, Long>,
        adminId: Long,
        counter: Counter,
    ) {
        val mine = listAllMine(seller).filter { it.brandName == brand.name }
        val onSale = mine.filter { it.status == ON_SALE }
        val shortage = (TARGET_ON_SALE - onSale.size).coerceAtLeast(0)
        val needPending = mine.none { it.status == PENDING_REVIEW }
        if (shortage == 0 && !needPending) return

        val images = uploadImages(seller.memberId)
        val onSaleNames = onSale.map { it.name }.toSet()
        // 판매 중이 아닌 이름부터 채우고, 모자라면 목록을 다시 돈다.
        val candidates = DEMO_ON_SALE_PRODUCTS.sortedBy { it.name in onSaleNames }
        repeat(shortage) { i ->
            val spec = candidates[i % candidates.size]
            val productId = register(seller, brand.brandId, spec, leafIds, images, i)
            productAdminService.approve(productId, adminId)
            fillStock(seller, productId, stock = 20 + (i * 7) % 40)
            counter.products++
        }
        if (needPending) {
            DEMO_PENDING_PRODUCTS.forEachIndexed { i, spec ->
                register(seller, brand.brandId, spec, leafIds, images, i)
                counter.pendingReviews++
            }
        }
    }

    private fun listAllMine(seller: SellerPrincipal): List<SellerProductSummary> {
        val all = mutableListOf<SellerProductSummary>()
        var page = 0
        do {
            val result = productQueryService.listMine(seller, null, page++, PAGE_SIZE)
            all += result.content
        } while (result.hasNext)
        return all
    }

    private fun uploadImages(memberId: Long): List<Long> =
        (1..IMAGE_COUNT).map { n ->
            val bytes = ClassPathResource("demo/demo-$n.webp").inputStream.use { it.readAllBytes() }
            fileService.uploadImage(memberId, "demo-$n.webp", "image/webp", bytes).fileId
        }

    private fun register(
        seller: SellerPrincipal,
        brandId: Long,
        spec: DemoProduct,
        leafIds: Map<Pair<String, String>, Long>,
        images: List<Long>,
        index: Int,
    ): Long {
        // 대표 이미지를 상품마다 돌려 목록이 단조롭지 않게 한다.
        val rotated = images.indices.map { images[(it + index) % images.size] }.take(2)
        val content = ProductContentCommand(
            name = spec.name,
            description = spec.description,
            categoryId = leafIds.getValue(spec.category),
            imageFileIds = rotated,
            options = spec.options.map { option ->
                OptionCommand(null, option.name, option.values.map { OptionValueCommand(null, it) })
            },
        )
        return productService.register(seller, RegisterProductCommand(brandId, content, spec.originalPrice, spec.salePrice))
    }

    private fun fillStock(seller: SellerPrincipal, productId: Long, stock: Int) {
        productQueryService.getMine(seller, productId).skus.forEach { sku ->
            productService.updateSku(seller, productId, sku.skuId, UpdateSkuCommand(extraPrice = 0, stock = stock, onSale = true))
        }
    }

    private fun sellerApplication(companyName: String, businessNumber: String, representative: String) =
        SellerApplicationCommand(
            companyName = companyName,
            businessNumber = businessNumber,
            representativeName = representative,
            phone = "02-1234-5678",
            bank = "국민",
            accountNumber = "123456-01-000001",
            holder = representative,
        )

    private class Counter {
        var members = 0
        var sellers = 0
        var brands = 0
        var categories = 0
        var products = 0
        var pendingReviews = 0

        fun result() = DemoReseedResult(members, sellers, brands, categories, products, pendingReviews)
    }

    private companion object {
        const val TARGET_ON_SALE = 20
        const val COMMISSION_RATE_BP = 1000
        const val PAGE_SIZE = 100
        const val IMAGE_COUNT = 4
        const val SETTLEMENT_STOCK = 500
        const val MAIN_BRAND = "카이로스 데모"
        const val PENDING_BRAND = "KAIROS DEMO"
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val ON_SALE = "ON_SALE"
        const val PENDING_REVIEW = "PENDING_REVIEW"
    }
}
