package com.dogyun.kairos.cart.presentation

import com.dogyun.kairos.cart.application.CartQueryService
import com.dogyun.kairos.cart.application.CartResult
import com.dogyun.kairos.cart.application.CartService
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/cart")
class CartController(
    private val cartService: CartService,
    private val cartQueryService: CartQueryService,
) {

    @GetMapping
    fun get(@AuthenticationPrincipal loginMember: LoginMember): ResponseEntity<ApiResponse<CartResult>> =
        ApiResponse.ok(cartQueryService.get(loginMember.id))

    @PostMapping("/items")
    fun add(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<ApiResponse<AddCartItemResponse>> =
        ApiResponse.ok(AddCartItemResponse(cartService.add(loginMember.id, request.skuId, request.quantity)))

    @PatchMapping("/items/{cartItemId}")
    fun changeQuantity(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable cartItemId: Long,
        @Valid @RequestBody request: ChangeCartItemQuantityRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        cartService.changeQuantity(loginMember.id, cartItemId, request.quantity)
        return ApiResponse.ok(null)
    }

    @DeleteMapping("/items/{cartItemId}")
    fun remove(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable cartItemId: Long,
    ): ResponseEntity<Void> {
        cartService.remove(loginMember.id, cartItemId)
        return ApiResponse.noContent()
    }
}
