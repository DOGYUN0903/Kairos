package com.dogyun.kairos.archfixture.order.domain

import com.dogyun.kairos.archfixture.order.infrastructure.OrderInfra

/** 규칙 위반 fixture: domain 이 infrastructure 에 의존한다. */
class BadLayeredOrder(val infra: OrderInfra)
