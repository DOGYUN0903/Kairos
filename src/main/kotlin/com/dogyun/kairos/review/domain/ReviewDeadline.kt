package com.dogyun.kairos.review.domain

import java.time.Duration
import java.time.LocalDateTime

/** 후기를 쓸 수 있는 마지막 시각(포함). 작성 API 와 후기 상태 조회가 같은 판정을 쓰도록 한 곳에 둔다. 이 시각보다 늦으면 기한이 지난 것이다. */
fun reviewWritableUntil(confirmedAt: LocalDateTime, deadline: Duration): LocalDateTime = confirmedAt.plus(deadline)
