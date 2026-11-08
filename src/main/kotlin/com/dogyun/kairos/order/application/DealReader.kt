package com.dogyun.kairos.order.application

/** deal 모듈 호출용 인터페이스. 구현은 order.infrastructure 에서 deal.application 을 호출한다. */
interface DealReader {
    /** 없으면 DomainException(DEAL_NOT_FOUND). */
    fun getSnapshot(dealId: Long): DealSnapshot

    /** dealId -> 상품명. 삭제 등으로 찾을 수 없는 딜은 결과에서 빠진다. */
    fun getProductNames(dealIds: Collection<Long>): Map<Long, String>
}

/** order 가 바라보는 딜 정보. deal 모듈 타입을 노출하지 않는다. */
data class DealSnapshot(
    val dealId: Long,
    val dealPrice: Long,
    val perMemberLimit: Int,
    val ongoing: Boolean,
)
