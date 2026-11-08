package com.dogyun.kairos.archfixture.order.application

import com.dogyun.kairos.archfixture.deal.application.DealApi
import com.dogyun.kairos.archfixture.deal.domain.DealThing

/** 규칙 위반 fixture: 다른 모듈 domain 과 application 을 infrastructure 밖에서 사용한다. */
class BadOrderService(val thing: DealThing, val api: DealApi)
