package com.dogyun.kairos.common.presentation

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** API 경계의 일시는 KST 오프셋 형식(예: 2026-12-09T10:00:00+09:00)이다. 도메인·애플리케이션은 KST 기준 LocalDateTime을 쓴다. */
object KstTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun now(): OffsetDateTime = OffsetDateTime.now(ZONE)

    fun toLocal(value: OffsetDateTime): LocalDateTime = value.atZoneSameInstant(ZONE).toLocalDateTime()

    fun toOffset(value: LocalDateTime): OffsetDateTime = value.atZone(ZONE).toOffsetDateTime()
}
