package com.dogyun.kairos

import org.springframework.test.web.servlet.get
import kotlin.test.Test
import kotlin.test.assertEquals

class CommerceAuthorizationApiTest : CommerceTestSupport() {

    private fun applyOnly(token: String) {
        post(
            "/api/v1/seller/application", token,
            mapOf(
                "companyName" to "대기상사", "businessNumber" to "999-88-77665", "representativeName" to "홍길동",
                "phone" to "010-1234-5678", "bank" to "국민", "accountNumber" to "123456-01-123456", "holder" to "홍길동",
            ),
        ).andExpect { status { isCreated() } }
    }

    @Test
    fun 판매자_토큰으로_관리자_API_전부_403() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val body = mapOf("commissionRateBp" to 1000)
        get("/api/v1/admin/sellers", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/sellers/1/approve", sellerToken, body).andExpect { status { isForbidden() } }
        get("/api/v1/admin/brands", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/brands/1/approve", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/products", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/products/1/approve", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/change-requests", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/change-requests/1/approve", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/categories", sellerToken, mapOf("parentId" to null, "name" to "x", "displayOrder" to 1))
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자가_아닌_회원의_판매자_API는_403() {
        val token = newUserToken()
        get("/api/v1/seller/brands", token).andExpect { status { isForbidden() } }
        post("/api/v1/seller/brands", token, mapOf("name" to "브랜드")).andExpect { status { isForbidden() } }
        get("/api/v1/seller/products", token).andExpect { status { isForbidden() } }
        post("/api/v1/seller/products", token, mapOf("brandId" to 1)).andExpect { status { isForbidden() } }
    }

    @Test
    fun PENDING_판매자의_브랜드_신청은_409_SELLER_NOT_ACTIVE() {
        val token = newUserToken()
        applyOnly(token)
        post("/api/v1/seller/brands", token, mapOf("name" to "대기브랜드"))
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("SELLER_NOT_ACTIVE") } }
    }

    @Test
    fun 정지된_판매자는_상품_등록_409_일시중지_200_종료_200() {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val fileId = uploadImage(token)
        val brandId = approvedBrand(token)
        val categoryId = leafCategory()
        val pausable = onSaleProduct(token, brandId, categoryId, listOf(fileId))
        val endable = onSaleProduct(token, brandId, categoryId, listOf(fileId))

        suspendSeller(sellerId)

        post(
            "/api/v1/seller/products", token,
            mapOf(
                "brandId" to brandId, "content" to productContent(categoryId, listOf(fileId)),
                "originalPrice" to 10000, "salePrice" to 9000,
            ),
        ).andExpect { status { isConflict() }; jsonPath("$.code") { value("SELLER_NOT_ACTIVE") } }
        post("/api/v1/seller/products/$pausable/pause", token).andExpect { status { isOk() } }
        post("/api/v1/seller/products/$endable/end", token).andExpect { status { isOk() } }
    }

    @Test
    fun 다른_판매자의_상품_판매가_변경과_SKU_변경은_404() {
        val ownerToken = newUserToken()
        approvedSeller(ownerToken)
        val fileId = uploadImage(ownerToken)
        val productId = onSaleProduct(ownerToken, approvedBrand(ownerToken), leafCategory(), listOf(fileId))
        val skuId = sellerProductDetail(ownerToken, productId).get("skus").get(0).get("skuId").asLong()

        val otherToken = newUserToken()
        approvedSeller(otherToken)
        patch("/api/v1/seller/products/$productId/price", otherToken, mapOf("salePrice" to 7000))
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("PRODUCT_NOT_FOUND") } }
        patch(
            "/api/v1/seller/products/$productId/skus/$skuId", otherToken,
            mapOf("extraPrice" to 0, "stock" to 1, "onSale" to true),
        ).andExpect { status { isNotFound() }; jsonPath("$.code") { value("PRODUCT_NOT_FOUND") } }
    }

    @Test
    fun 다른_회원이_올린_파일을_상품_이미지로_쓰면_400_FILE_NOT_OWNED() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val brandId = approvedBrand(sellerToken)
        val categoryId = leafCategory()
        val othersFile = uploadImage(newUserToken())
        post(
            "/api/v1/seller/products", sellerToken,
            mapOf(
                "brandId" to brandId, "content" to productContent(categoryId, listOf(othersFile)),
                "originalPrice" to 10000, "salePrice" to 9000,
            ),
        ).andExpect { status { isBadRequest() }; jsonPath("$.code") { value("FILE_NOT_OWNED") } }
    }

    @Test
    fun 공개_API는_비로그인으로_200() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val fileId = uploadImage(sellerToken)
        val brandId = approvedBrand(sellerToken)
        val productId = onSaleProduct(sellerToken, brandId, leafCategory(), listOf(fileId))

        get("/api/v1/categories").andExpect { status { isOk() } }
        get("/api/v1/products").andExpect { status { isOk() } }
        get("/api/v1/products/$productId").andExpect { status { isOk() } }
        get("/api/v1/brands/$brandId").andExpect { status { isOk() } }

        val detail = json(get("/api/v1/products/$productId"))
        val imageUrl = detail.get("data").get("imageUrls").get(0).asString()
        val path = imageUrl.substring(imageUrl.indexOf("/files/"))
        assertEquals(200, mockMvc.get(path).andReturn().response.status)
    }
}
