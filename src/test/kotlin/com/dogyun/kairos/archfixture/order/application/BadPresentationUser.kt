package com.dogyun.kairos.archfixture.order.application

import com.dogyun.kairos.archfixture.order.presentation.BadOrderController

/** 규칙 위반 fixture: application 이 presentation 에 의존한다. */
class BadPresentationUser(val controller: BadOrderController)
