package com.dogyun.kairos.review.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 후기 작성 기한(구매 확정 후). review 가 benefit 설정을 읽지 않도록 따로 둔다. */
@ConfigurationProperties("kairos.review")
data class ReviewProperties(val writeDeadline: Duration = Duration.ofDays(90))
