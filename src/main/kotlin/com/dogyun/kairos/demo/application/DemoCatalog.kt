package com.dogyun.kairos.demo.application

/** 데모 카테고리: 대분류 6개(시안 cat-nav 와 같은 이름·순서) + 소분류 각 2개. */
internal val DEMO_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "상의" to listOf("반팔 티셔츠", "셔츠"),
    "아우터" to listOf("자켓", "코트"),
    "바지" to listOf("데님", "슬랙스"),
    "신발" to listOf("스니커즈", "로퍼"),
    "가방" to listOf("백팩", "토트백"),
    "액세서리" to listOf("모자", "머플러"),
)

internal data class DemoOption(val name: String, val values: List<String>)

internal data class DemoProduct(
    val name: String,
    val description: String,
    /** (대분류, 소분류) */
    val category: Pair<String, String>,
    val originalPrice: Long,
    val salePrice: Long,
    val options: List<DemoOption>,
)

private fun colors(a: String, b: String) = DemoOption("색상", listOf(a, b))
private val SIZE_TOP = DemoOption("사이즈", listOf("M", "L"))
private val SIZE_PANTS = DemoOption("사이즈", listOf("30", "32"))
private val SIZE_SHOES = DemoOption("사이즈", listOf("250", "260"))
private val SIZE_BAG = DemoOption("크기", listOf("스몰", "라지"))
private val SIZE_FREE = DemoOption("사이즈", listOf("FREE", "L"))

/** 판매 중으로 만드는 데모 상품 20종. 금액은 원 단위 정수, 판매가 ≤ 정가. */
internal val DEMO_ON_SALE_PRODUCTS: List<DemoProduct> = listOf(
    DemoProduct("에센셜 코튼 반팔 티셔츠", "20수 싱글 코튼으로 만든 데일리 반팔 티셔츠입니다.", "상의" to "반팔 티셔츠", 29000, 19900, listOf(colors("화이트", "블랙"), SIZE_TOP)),
    DemoProduct("오버핏 그래픽 반팔 티셔츠", "여유 있는 오버핏 실루엣의 그래픽 프린팅 티셔츠입니다.", "상의" to "반팔 티셔츠", 39000, 27300, listOf(colors("차콜", "크림"), SIZE_TOP)),
    DemoProduct("옥스포드 버튼다운 셔츠", "탄탄한 옥스포드 원단의 클래식 버튼다운 셔츠입니다.", "상의" to "셔츠", 59000, 41300, listOf(colors("스카이블루", "화이트"), SIZE_TOP)),
    DemoProduct("린넨 블렌드 오픈카라 셔츠", "린넨 혼방으로 시원한 오픈카라 반팔 셔츠입니다.", "상의" to "셔츠", 49000, 39200, listOf(colors("베이지", "네이비"), SIZE_TOP)),
    DemoProduct("크롭 데님 트러커 자켓", "워싱 데님으로 만든 짧은 기장의 트러커 자켓입니다.", "아우터" to "자켓", 89000, 62300, listOf(colors("라이트블루", "인디고"), SIZE_TOP)),
    DemoProduct("라이트 나일론 윈드브레이커", "가볍고 바람을 막아 주는 나일론 바람막이입니다.", "아우터" to "자켓", 79000, 55300, listOf(colors("블랙", "올리브"), SIZE_TOP)),
    DemoProduct("울 블렌드 싱글 코트", "울 혼방 원단의 미니멀한 싱글 브레스트 코트입니다.", "아우터" to "코트", 239000, 167300, listOf(colors("카멜", "차콜"), SIZE_TOP)),
    DemoProduct("핸드메이드 발마칸 코트", "어깨선이 자연스러운 핸드메이드 발마칸 코트입니다.", "아우터" to "코트", 329000, 263200, listOf(colors("베이지", "네이비"), SIZE_TOP)),
    DemoProduct("스트레이트 셀비지 데님", "셀비지 원단의 일자 핏 데님 팬츠입니다.", "바지" to "데님", 99000, 69300, listOf(colors("인디고", "블랙"), SIZE_PANTS)),
    DemoProduct("와이드 워시드 데님", "넉넉한 와이드 핏의 워싱 데님 팬츠입니다.", "바지" to "데님", 69000, 48300, listOf(colors("라이트블루", "미드블루"), SIZE_PANTS)),
    DemoProduct("투턱 울 슬랙스", "울 혼방 원단의 투턱 와이드 슬랙스입니다.", "바지" to "슬랙스", 79000, 59000, listOf(colors("그레이", "블랙"), SIZE_PANTS)),
    DemoProduct("세미와이드 밴딩 슬랙스", "허리 밴딩으로 편한 세미와이드 슬랙스입니다.", "바지" to "슬랙스", 45000, 31500, listOf(colors("베이지", "차콜"), SIZE_PANTS)),
    DemoProduct("캔버스 로우탑 스니커즈", "가벼운 캔버스 소재의 로우탑 스니커즈입니다.", "신발" to "스니커즈", 69000, 48300, listOf(colors("화이트", "블랙"), SIZE_SHOES)),
    DemoProduct("레더 코트 스니커즈", "천연 가죽으로 만든 미니멀 코트 스니커즈입니다.", "신발" to "스니커즈", 129000, 90300, listOf(colors("화이트", "그린"), SIZE_SHOES)),
    DemoProduct("클래식 페니 로퍼", "부드러운 소가죽의 클래식 페니 로퍼입니다.", "신발" to "로퍼", 159000, 127200, listOf(colors("블랙", "브라운"), SIZE_SHOES)),
    DemoProduct("코듀라 데일리 백팩", "코듀라 원단으로 튼튼한 15인치 노트북 백팩입니다.", "가방" to "백팩", 89000, 66700, listOf(colors("블랙", "네이비"), SIZE_BAG)),
    DemoProduct("캔버스 빅 토트백", "수납이 넉넉한 두꺼운 캔버스 토트백입니다.", "가방" to "토트백", 39000, 29900, listOf(colors("아이보리", "블랙"), SIZE_BAG)),
    DemoProduct("코튼 볼캡", "워싱 코튼 소재의 6패널 볼캡입니다.", "액세서리" to "모자", 32000, 22400, listOf(colors("네이비", "베이지"), SIZE_FREE)),
    DemoProduct("램스울 비니", "램스울 혼방으로 따뜻한 골지 비니입니다.", "액세서리" to "모자", 25000, 17500, listOf(colors("그레이", "버건디"), SIZE_FREE)),
    DemoProduct("캐시미어 블렌드 머플러", "캐시미어 혼방의 부드러운 롱 머플러입니다.", "액세서리" to "머플러", 69000, 48300, listOf(colors("카멜", "그레이"), SIZE_FREE)),
)

/** 검수 대기로 남기는 데모 상품 3종. */
internal val DEMO_PENDING_PRODUCTS: List<DemoProduct> = listOf(
    DemoProduct("헤비 스웻 후드 집업", "도톰한 쭈리 원단의 후드 집업입니다.", "아우터" to "자켓", 79000, 59000, listOf(colors("멜란지", "블랙"), SIZE_TOP)),
    DemoProduct("코튼 치노 팬츠", "적당한 핏의 데일리 치노 팬츠입니다.", "바지" to "슬랙스", 59000, 41300, listOf(colors("카키", "베이지"), SIZE_PANTS)),
    DemoProduct("레더 미니 크로스백", "소가죽으로 만든 미니 크로스백입니다.", "가방" to "토트백", 119000, 95200, listOf(colors("블랙", "탄"), SIZE_BAG)),
)

/** 정산 시연 전용 브랜드. 기존 "판매 중 20개" 보충 계산(데모 브랜드 기준)에 섞이지 않게 따로 둔다. */
internal const val SETTLEMENT_DEMO_BRAND = "카이로스 정산 시연"

/**
 * 정산 시연 주문에 쓰는 상품 5종(판매가 = 정가). 순서가 곧 역할이다: 0 = 50,000(주문 A, 쿠폰 대상), 1 = 30,000(A), 2 = 20,000(B, 불량 반품),
 * 3 = 15,000(B, 남김), 4 = 30,000(C, 전부 반품). 한 항목 옵션 하나뿐인 단순 상품이다.
 */
internal val DEMO_SETTLEMENT_PRODUCTS: List<DemoProduct> = listOf(
    50_000L to "A-1",
    30_000L to "A-2",
    20_000L to "B-1",
    15_000L to "B-2",
    30_000L to "C-1",
).map { (price, tag) ->
    DemoProduct(
        name = "정산 시연 상품 $tag (${"%,d".format(price)}원)",
        description = "정산 시연 주문에 쓰는 상품입니다.",
        category = "상의" to "반팔 티셔츠",
        originalPrice = price,
        salePrice = price,
        options = listOf(DemoOption("구성", listOf("기본"))),
    )
}
