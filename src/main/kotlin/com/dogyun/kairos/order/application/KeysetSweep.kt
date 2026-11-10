package com.dogyun.kairos.order.application

/**
 * 정리 작업이 쓰는 keyset 순회. [fetch] 가 `id > afterId` 인 항목을 id 오름차순으로 [pageSize] 건씩 돌려주면,
 * 짧은 페이지가 나올 때까지 이어서 가져온다. 처리하지 못해 집합에 남는 항목이 있어도 다음 페이지는 마지막 id 이후부터라서
 * 뒤의 항목을 막지 않는다. [handle] 이 true 를 돌려준 항목 수를 돌려준다.
 */
internal fun <T> sweepByKeyset(
    pageSize: Int,
    idOf: (T) -> Long,
    fetch: (afterId: Long) -> List<T>,
    handle: (T) -> Boolean,
): Int {
    var afterId = 0L
    var handled = 0
    while (true) {
        val page = fetch(afterId)
        page.forEach { if (handle(it)) handled++ }
        if (page.size < pageSize) return handled
        afterId = idOf(page.last())
    }
}
