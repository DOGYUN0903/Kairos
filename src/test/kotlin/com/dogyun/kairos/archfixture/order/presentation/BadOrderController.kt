package com.dogyun.kairos.archfixture.order.presentation

import com.dogyun.kairos.archfixture.order.infrastructure.OrderInfra

/** 규칙 위반 fixture: presentation 이 infrastructure 에 의존한다. */
class BadOrderController(val infra: OrderInfra)
