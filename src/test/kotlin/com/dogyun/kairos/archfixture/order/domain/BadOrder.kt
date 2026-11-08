package com.dogyun.kairos.archfixture.order.domain

import org.springframework.stereotype.Component

/** 규칙 위반 fixture: domain 이 Spring 타입에 의존한다. */
class BadOrder(val marker: Component? = null)
