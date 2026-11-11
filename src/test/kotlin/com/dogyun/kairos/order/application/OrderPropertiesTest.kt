package com.dogyun.kairos.order.application

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderPropertiesTest {

    @Test
    fun return_window와_auto_confirm_after가_다르면_mismatch_true다() {
        assertEquals(false, OrderProperties().returnWindowMismatch())
        assertEquals(true, OrderProperties(returnWindow = Duration.ofDays(30)).returnWindowMismatch())
        assertEquals(true, OrderProperties(autoConfirmAfter = Duration.ofDays(3)).returnWindowMismatch())
    }
}
