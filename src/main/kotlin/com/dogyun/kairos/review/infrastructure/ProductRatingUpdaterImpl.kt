package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.catalog.application.ProductStatsService
import com.dogyun.kairos.review.application.ProductRatingUpdater
import org.springframework.stereotype.Component

@Component
class ProductRatingUpdaterImpl(private val productStatsService: ProductStatsService) : ProductRatingUpdater {
    override fun applyReview(productId: Long, countDelta: Long, ratingDelta: Long) =
        productStatsService.applyReview(productId, countDelta, ratingDelta)
}
