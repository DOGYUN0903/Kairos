package com.dogyun.kairos.common

import com.dogyun.kairos.common.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `음수_금액은_생성할_수_없다`() {
        assertFailsWith<IllegalArgumentException> { Money(-1) }
    }

    @Test
    fun `금액에_수량을_곱한다`() {
        assertEquals(Money(3000), Money(1000) * 3)
    }
}
