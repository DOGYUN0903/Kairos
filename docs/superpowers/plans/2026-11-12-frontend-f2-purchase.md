# 프론트엔드 F2 구매 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 장바구니 → 주문서 → 결제 → 주문 내역·상세(취소·구매 확정)와 판매자·관리자 주문 화면을 실제 백엔드에 붙이고, 그에 필요한 백엔드 보강 5건을 넣는다.

**Architecture:** 백엔드는 읽기 전용 조회·응답 필드 추가와 멱등 경로 한 줄만 바꾼다(주문·결제·취소 트랜잭션과 금액 계산 클래스는 그대로). 프론트는 F1 구조(`app/`은 조립, 기능은 `features/`)에 `cart`·`checkout`·`payment`·`orders`·`seller/orders`·`admin/orders`를 더한다. 화면의 모든 합계·배송비·환불액은 서버 응답 값이고, 결제는 모의/토스 결제창이 같은 승인 페이지(`/checkout/success`)로 모인다.

**Tech Stack:** Kotlin 2.3 · Spring Boot 4.1 · QueryDSL · Testcontainers / Next.js 15 · React 19 · TanStack Query · react-hook-form + zod · openapi-fetch · Vitest · Playwright

**Spec:** `docs/superpowers/specs/2026-11-12-frontend-f2-purchase-design.md` (구현자는 자기 태스크가 가리키는 절을 반드시 읽는다. 상위 규칙: `CLAUDE.md`, F1 스펙 `docs/superpowers/specs/2026-11-11-frontend-f1-foundation-design.md`)

## Global Constraints

- 브랜치 `feature/frontend-f2-purchase`. 커밋 메시지는 한국어 Conventional Commits. `main`·`dev`에 직접 커밋하지 않는다. push·PR은 계획 밖(오케스트레이터가 한다).
- **SDD**: API 변경은 `docs/api/openapi.yaml` → 구현 → `OpenApiContractTest` 순서. OpenAPI를 고친 커밋에 `cd frontend && pnpm gen:api` 결과(`src/api/schema.d.ts`)를 함께 넣는다.
- 백엔드: 4계층 DDD와 모듈 규칙(`ArchitectureTest`)을 지킨다. 다른 모듈은 호출하는 쪽 `application`의 포트 + 자기 `infrastructure` 구현으로만 부른다. Flyway 마이그레이션·인덱스 추가 없음. `RefundCalculator`·`DiscountAllocator` 등 돈 계산 클래스는 **수정 금지**(호출만). PG 호출을 트랜잭션 안으로 옮기는 변경 금지.
- 백엔드 테스트: 이름은 한국어, 애플리케이션 서비스 테스트는 인메모리 Fake(`order/application/Fakes.kt`, `cart/application/Fake*.kt`), API 테스트는 기존 `*ApiTest` 패턴(`IntegrationTestSupport`, `CommerceTestSupport`). 실행 `./gradlew test --tests '<클래스>'`(Docker 필요). 끝나면 Gradle 데몬·컨테이너를 남기지 않는다.
- 금액: Long/정수 원. 프론트에서 나눗셈(`/`, `%`)은 `src/lib/money.ts`에서만(ESLint). 합계·배송비·환불액을 프론트가 계산하지 않는다. 프론트 계산은 상세의 선택 합계(기존)와 "무료배송까지 남은 금액"뿐.
- 프론트: 액세스 토큰은 메모리에만. `sessionStorage`에는 토큰이 아닌 값(승인 멱등 키, `orderNo → orderId`)만 둔다. 낙관적 업데이트 없음. 뮤테이션 자동 재시도 없음. 필터·탭·페이지는 URL 쿼리가 단일 진실. 모든 목록·상세는 로딩·빈·오류 세 상태. 에러 메시지는 `src/api/errors.ts` 표 또는 서버 메시지 그대로. 데스크톱 전용(1280px). `hot` 색은 쓰지 않는다.
- 서버가 `Idempotency-Key`를 강제하는 요청(장바구니 담기, 주문 생성, 결제 승인, 항목 취소 3종)은 반드시 키를 보낸다. 키 규칙: **응답을 받지 못한 시도(네트워크 오류·5xx)만 같은 키, 서버가 답한 뒤(2xx·4xx)의 다음 제출은 새 키**.
- 데모 전용 코드는 `src/features/demo`에만 두고 `process.env.NEXT_PUBLIC_DEMO_MODE === "true"` 리터럴 비교로 감싼다. 어떤 번들에도 `KAIROS_ADMIN`·토스 시크릿 키가 없어야 한다.
- `design/`의 기존 시안 파일은 고치지 않는다(새 파일 추가와 `index.html` 링크 추가, `kairos.css`에 클래스 **추가**만 허용).
- 구매자 전용 페이지(`/cart`, `/checkout*`, `/my/orders/**`)는 각 `page.tsx`에서 `RequireAuth`로 감싼다(`(store)/layout.tsx`에는 가드가 없다).
- 프론트 테스트 이름은 한국어. 순수 로직은 `.ts` + `.test.ts`(Vitest), 화면 흐름은 E2E. 각 프론트 태스크의 검증: `cd frontend && pnpm lint && pnpm typecheck && pnpm test`.
- 의존성을 추가하면 커밋 본문에 이유를 쓴다. claude.ai Artifact·Claude Design·Figma 토큰 사용 금지.

## Review Focus

스펙이 암시하지만 놓치기 쉬운 입력·상황. 각 줄의 테스트는 해당 태스크에 들어 있다.

1. `/checkout/success`에서 새로고침·뒤로 가기·StrictMode 이중 실행 → 승인 요청은 같은 `Idempotency-Key`로, 한 번의 마운트에 한 번만 나간다(Task 9).
2. 결제 승인 응답을 못 받음(네트워크 오류·5xx·409 진행 중) → "결제 완료"도 "실패"도 아닌 "확인 중"으로 보이고 주문을 다시 조회한다(Task 9).
3. 가격 변경(409) 뒤 같은 배송지로 다시 결제 → 새 키로 나가 422가 나지 않는다(Task 6, E2E Task 13).
4. `/checkout` 쿼리가 깨짐(`cartItemIds=abc`, `qty=0`, `qty=1000`, 남의·삭제된 장바구니 항목 ID, 둘 다 있음) → 예외 없이 장바구니로 보낸다(Task 8).
5. 주문 항목의 상품이 삭제됨(`thumbnailUrl=null`), 모르는 택배사 코드, 반품 상태(`RETURN_*`) 항목 → 화면이 깨지지 않고 자리 표시 이미지·코드 그대로·칩 문구로 보인다(Task 2, 6, 10).

---

## 파일 구조

**백엔드(수정)**
- `docs/api/openapi.yaml` — 경로 1개 추가, 스키마 필드 추가
- `order/application/OrderQueryService.kt`, `OrderResults.kt`, `ProductReader.kt`, `OrderService.kt` · `order/infrastructure/ProductReaderImpl.kt` · `order/presentation/OrderController.kt`, `OrderRequests.kt`
- `catalog/application/` — 대표 이미지 일괄 조회 메서드 1개
- `cart/application/CartQueryService.kt`, `CartResults.kt`, `ProductReader.kt` · 신규 `cart/application/SellerReader.kt`, `cart/infrastructure/SellerReaderImpl.kt` · `cart/infrastructure/ProductReaderImpl.kt`
- `common/idempotency/IdempotencyProperties.kt`

**프론트(신규, `frontend/src/`)**
```
features/cart/        api.ts  selection.ts(+test)  components/{CartPage,CartSellerGroup,CartItemRow,CartSummary,AddToCartButtons}.tsx
features/checkout/    api.ts  checkout-params.ts(+test)  schemas.ts(+test)  postcode.ts
                      components/{CheckoutPage,ShippingAddressForm,OrderItemsBySeller,PaymentSummary}.tsx
features/payment/     config.ts  payment-key.ts(+test)  confirm-state.ts(+test)  confirm-key-store.ts(+test)  order-ref-store.ts
                      api.ts  toss.ts  components/{PayPage,MockPaymentDialog,ConfirmPage,FailPage,CompletePage}.tsx
features/demo/        MockOutcomePicker.tsx (실패 선택지 — 데모 전용)
features/orders/      api.ts  progress.ts(+test)  actions.ts(+test)  carriers.ts(+test)  polling.ts(+test)
                      components/{OrderListPage,OrderCard,OrderDetailPage,OrderStatusHeader,SellerOrderCard,OrderItemRow,
                                  CancelItemDialog,PaymentSummaryCard,ShippingAddressCard,PayCountdown}.tsx
features/seller/orders/  api.ts  tabs.ts  components/{SellerOrdersPage,SellerOrderTable,SellerOrderDetailView,ShipDialog,SellerCancelDialog}.tsx
features/admin/orders/   api.ts  components/{AdminOrdersPage,AdminOrderFilters,AdminOrderDetailView,AdminCancelDialog}.tsx
hooks/useIdempotentAction.ts(+test)
app/(store)/cart/page.tsx  checkout/page.tsx  checkout/success/page.tsx  checkout/fail/page.tsx
app/(store)/my/orders/page.tsx  my/orders/[orderId]/page.tsx  …/pay/page.tsx  …/complete/page.tsx
app/seller/orders/page.tsx  seller/orders/[sellerOrderId]/page.tsx
app/admin/orders/page.tsx  admin/orders/[orderId]/page.tsx
```
**프론트(수정)**: `hooks/useIdempotentSubmit.ts`, `lib/money.ts`, `lib/datetime.ts`, `lib/query-keys.ts`, `api/errors.ts`, `components/common/StatusChip.tsx`, `components/layout/StoreHeader.tsx`, 판매자·관리자 사이드바와 대시보드, `features/catalog/components/ProductDetailView.tsx`, `scripts/check-bundle.sh`, `scripts/e2e-server.sh`, `.env.example`, `e2e/fixtures/api.ts`

**시안**: `design/user-cart.html`, `user-checkout.html`, `user-order-detail.html`, `user-order-list.html`, `seller-order.html`, `design/index.html`(링크), `design/assets/kairos.css`(추가)

---

### Task 1: 시안 5개 (사용자 확인 게이트)

스펙 §4. **이 태스크의 산출물을 사용자가 확인하기 전에는 Task 7 이후(화면 구현)를 시작하지 않는다.** Task 2~6은 시안과 독립이라 확인을 기다리는 동안 진행한다.

**Files:**
- Create: `design/user-cart.html`, `design/user-checkout.html`, `design/user-order-detail.html`, `design/user-order-list.html`, `design/seller-order.html`
- Modify: `design/index.html`(링크 5개 추가), `design/assets/kairos.css`(필요한 클래스 추가만)

**Interfaces:**
- Consumes: 기존 `design/user-product-detail.html`·`seller-product-list.html`의 헤더·콘솔 구조와 `kairos.css` 토큰
- Produces: Task 7~11이 따르는 화면 구조. 시안 속 데이터는 스펙의 실제 필드로만 채운다(API에 없는 값을 그리지 않는다).

- [ ] **Step 1: 기존 시안과 `kairos.css`를 읽고 공용 구조(`.site-header`, `.cat-nav`, `.console`, `.chip-*`, `.pagination`, 버튼·폼 클래스)를 파악한다**
- [ ] **Step 2: `user-cart.html`** — 판매자 2명 묶음(각 묶음에 판매자명·묶음 선택·배송비 줄), 항목 행(체크박스, 이미지, 브랜드, 상품명, 옵션, 단가, 수량, 금액, 삭제), 구매 불가 항목 1개(흐림 + "판매 중지" + 삭제만), 상단에 전체 선택·"선택 삭제", 오른쪽 고정 요약(상품 금액, 배송비 "3,000원 × 2", 결제 예정 금액, "8,000원 더 담으면 무료배송", 주문하기). 같은 파일 아래에 "무료배송 달성" 요약과 "빈 장바구니" 상태를 구분선과 제목으로 함께 그린다.
- [ ] **Step 3: `user-checkout.html`** — 왼쪽: 배송지 폼(수령인, 연락처, 우편번호 + "주소 검색", 주소, 상세 주소, 배송 메모, "최근 배송지 불러오기"), 판매자별 주문 상품. 오른쪽: 결제 금액(상품 금액, 배송비, 결제 금액)과 "○○원 결제하기". 상태 그림: "금액이 변경되었습니다" 안내 배너, **모의 결제창** 다이얼로그 2종(일반: 주문명·금액·결제·닫기 / 데모: "시연용" 표시가 붙은 결과 선택 4개 — 정상 승인, 카드사 거절, 응답 없음(실제로는 승인됨), 응답 없음(승인 안 됨)).
- [ ] **Step 4: `user-order-detail.html`** — 상태 헤더(결제 완료 기준) + 상태 변형 3개(결제 대기: 남은 시간 "12:34"와 결제하기 / 결제 확인 중 / 취소), 판매자 주문 카드 2개(하나는 배송 준비 중 + 항목 "취소" 버튼, 하나는 배송 완료 + 택배사·송장 + "구매 확정"), 항목 상태 칩(주문 완료, 취소 확인 중, 취소 + 환불액), 결제 요약(상품 금액, 배송비, 결제 금액, 환불 금액, 배송비 차감, 남은 결제 금액), 배송지. **항목 취소 창**: 환불 예정 내역(항목 금액 30,000원, 배송비 차감 −3,000원, 환불 예정 27,000원), 차감 사유 한 줄, 사유 입력, 취소하기.
- [ ] **Step 5: `user-order-list.html`** — 주문 카드 4개(결제 대기 + 결제하기, 배송 중 1 · 배송 준비 1, 구매 확정, 취소), 페이지네이션, 빈 상태.
- [ ] **Step 6: `seller-order.html`** — 콘솔 레이아웃. 목록: 상태 탭(전체/발송 대기 3/배송 중/배송 완료/구매 확정/취소), 표(이미지, 주문번호, 상품, 금액, 상태, 주문 일시). 상세: 배송지 카드, 항목 표(항목 취소 버튼), 배송비·부담 주체, 진행 시각, "발송 처리"·"배송 완료" 버튼. **발송 처리 창**(택배사 선택, 송장번호)과 **항목 취소 창**("배송비 차감 없이 환불됩니다", 사유).
- [ ] **Step 7: `index.html`에 링크 5개를 기존 형식으로 추가하고, 브라우저에서 1440px로 5개 파일을 열어 스크린샷으로 확인한다(가로 스크롤·깨진 레이아웃·콘솔 오류 없음). `hot` 색 미사용 확인: `grep -n "hot" design/user-cart.html design/user-checkout.html design/user-order-*.html design/seller-order.html` → 출력 없음**
- [ ] **Step 8: Commit** — `docs(design): F2 구매 화면 시안 5종 추가`

---

### Task 2: 환불 미리보기 API + 주문 응답 보강 (백엔드)

스펙 §5-1, §5-2.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`(생성)
- Modify: `src/main/kotlin/com/dogyun/kairos/order/application/{OrderQueryService,OrderResults,ProductReader}.kt`, `order/infrastructure/ProductReaderImpl.kt`, `order/presentation/{OrderController,OrderRequests}.kt`
- Modify: `catalog/application/` 의 상품 조회 서비스(대표 이미지 일괄 조회 추가)
- Modify: `src/test/kotlin/com/dogyun/kairos/order/application/Fakes.kt`(Fake `ProductReader`)
- Test: 신규 `order/presentation/OrderCancelPreviewApiTest.kt`, 수정 `order/presentation/OrderQueryApiTest.kt`, `order/application/OrderQueryServiceTest.kt`

**Interfaces:**
- Produces (OpenAPI):
  - `GET /api/v1/orders/{orderId}/items/{orderItemId}/cancel-preview` (operationId `previewCancelMyOrderItem`, 인증, 헤더 없음) → `CancelPreview { orderItemId, paidAmount, shippingFeeRefund, shippingFeeDeducted, shippingFeeReturned, refundAmount, pointsRefund }` (전부 required int64). 오류 401, 404 `ORDER_NOT_FOUND`/`ORDER_ITEM_NOT_FOUND`, 409 `ORDER_ITEM_NOT_CANCELABLE`/`ORDER_INVALID_STATE_TRANSITION`.
  - `OrderItemResult.thumbnailUrl: string|null`, `OrderSummary.thumbnailUrl: string|null`, `SellerOrderSummary.thumbnailUrl: string|null`, `OrderSummary.sellerOrderStatuses: SellerOrderStatus[]`(required). 세 `thumbnailUrl`은 required + nullable.
- Produces (Kotlin):
  - `OrderQueryService.previewCancel(memberId: Long, orderId: Long, orderItemId: Long): CancelPreviewResult`
  - `data class CancelPreviewResult(orderItemId, paidAmount, shippingFeeRefund, shippingFeeDeducted, shippingFeeReturned, refundAmount, pointsRefund)` — 전부 `Long`
  - `ProductReader.findThumbnailUrls(productIds: Collection<Long>): Map<Long, String>` — 없는·삭제된 상품은 빠진다

- [ ] **Step 1: OpenAPI에 위 경로·스키마를 추가한다.** `cancel-preview` 설명에 "실제 취소와 같은 계산(`RefundCalculator`), 아무것도 바꾸지 않는다. 미리보기 뒤 상태가 바뀔 수 있으며 진실은 취소 응답이다"를 적는다.
- [ ] **Step 2: 실패하는 테스트 작성 — `OrderCancelPreviewApiTest`** (`OrderCancelApiTest`의 헬퍼는 private이라 `CommerceTestSupport`의 `paidOrder`·`postIdem`·`cancelItem`을 쓴다). 테스트와 단언:
  - `무료배송_주문에서_한_판매자_항목을_취소하면_배송비_3000원이_차감된_27000원이_미리보인다` — 판매자 B 30,000 + C 30,000 결제 후 C 항목: `paidAmount=30000, shippingFeeDeducted=3000, shippingFeeRefund=0, shippingFeeReturned=0, refundAmount=27000`
  - `남은_금액이_정확히_5만원이면_차감하지_않는다` — 50,000 + 10,000에서 10,000 항목: `shippingFeeDeducted=0, refundAmount=10000`
  - `이미_차감된_뒤의_취소는_다시_차감하지_않는다` — 20,000 ×3 주문(6만 원, 판매자 3명)에서 첫 항목 실제 취소(환불 17,000, 차감 3,000) 후 둘째 미리보기: `shippingFeeDeducted=0, refundAmount=20000`
  - `마지막_항목_취소는_앞서_차감한_배송비를_돌려준다` — 30,000 + 30,000에서 하나 실제 취소 후 나머지: `shippingFeeReturned=3000, refundAmount=33000`
  - `구매자가_배송비를_낸_판매자_주문이_비면_배송비도_환불된다` — 20,000(B) + 20,000(C) 주문(배송비 3,000 ×2)에서 C 항목: `shippingFeeRefund=3000, refundAmount=23000`
  - `미리보기_금액은_직후_실제_취소_응답과_같다` — 미리보기 후 `POST …/cancel`: `refundAmount`, `shippingFeeDeducted`, `pointsRefund` 일치
  - `미리보기는_주문_결제_재고를_바꾸지_않는다` — 호출 전후 `GET /orders/{id}` 응답 본문 동일, SKU 재고 동일
  - `남의_주문은_404다`, `없는_항목은_404다`, `배송이_시작된_항목은_409다`(`ORDER_ITEM_NOT_CANCELABLE`), `결제_전_주문은_409다`(`ORDER_INVALID_STATE_TRANSITION`)
- [ ] **Step 3: `./gradlew test --tests '*OrderCancelPreviewApiTest'` → 404(경로 없음)로 실패 확인**
- [ ] **Step 4: 구현.** `OrderQueryService.previewCancel`: `orderRepository.findById(orderId)`(락 없음) → 소유자 아니면 `ORDER_NOT_FOUND` → `order.findItem`(없으면 기존 예외 `ORDER_ITEM_NOT_FOUND`) → `RefundCalculator.plan(order, orderItemId, CancelActor.BUYER, properties.shippingPolicy())` → `CancelPreviewResult`. `OrderController`에 `@GetMapping("/{orderId}/items/{orderItemId}/cancel-preview")`. 응답 DTO는 `OrderRequests.kt`의 기존 `CancelItemResponse` 옆에 둔다.
- [ ] **Step 5: 테스트 통과 확인(Step 3 명령) → Commit** `feat(backend): 주문 항목 환불 미리보기 API 추가`(openapi + schema.d.ts 포함)
- [ ] **Step 6: 실패하는 테스트 — `OrderQueryApiTest`에 추가**
  - `주문_목록과_상세에_상품_대표_이미지가_있다` — `content[0].thumbnailUrl`이 상품의 첫 이미지 URL, 상세 `sellerOrders[0].items[0].thumbnailUrl` 동일, 판매자 목록 `content[0].thumbnailUrl` 동일
  - `주문_목록에_판매자_주문_상태가_있다` — 판매자 2명 주문에서 한쪽만 발송 후 `sellerOrderStatuses`가 `["SHIPPING","PAID"]`(정렬 무관, 내용 비교)
  - `삭제된_상품의_주문_이미지는_null이다` (Review Focus 5) — Fake `ProductReader`가 해당 상품을 빼도록 한 `OrderQueryServiceTest`로 검증: `thumbnailUrl == null`, 예외 없음
  - `주문_목록_한_페이지의_이미지는_한_번에_조회한다` — `OrderQueryServiceTest`에서 Fake의 `findThumbnailUrls` 호출 횟수 1
- [ ] **Step 7: 구현.** catalog application(`SkuQueryService` 또는 `ProductQueryService`)에 `findThumbnailUrls(productIds): Map<Long, String>`(상품의 첫 이미지 URL — 기존 `ProductRepository.findAllByIds`와 `imageFileReader.findImages(...)`로 `SkuQueryService`가 `thumbnailUrl`을 만드는 것과 같은 규칙. 이미지가 없으면 결과에서 뺀다)를 추가하고 `order.infrastructure.ProductReaderImpl`에서 호출. `OrderQueryService`는 목록·상세에서 필요한 `productId`를 모아 **한 번** 조회해 결과 DTO에 채운다(`toSummary`, `toDetail`, `getForSeller`, `listForSeller`). `sellerOrderStatuses = order.sellerOrders.map { it.status.name }`(이미 로드된 애그리거트, 추가 쿼리 없음).
- [ ] **Step 8: `./gradlew test --tests '*OrderQuery*' --tests '*OpenApiContractTest' --tests '*ArchitectureTest'` 통과 → `cd frontend && pnpm gen:api && pnpm check:api` → Commit** `feat(backend): 주문 응답에 상품 이미지와 판매자 주문 상태 추가`

---

### Task 3: 견적 응답 보강 (백엔드)

스펙 §5-3(견적 부분).

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`, `order/application/{OrderService,OrderResults,ProductReader}.kt`, `order/infrastructure/ProductReaderImpl.kt`, 테스트 Fake
- Test: `order/presentation/OrderQuoteApiTest.kt`, `order/application/OrderServiceTest.kt`

**Interfaces:**
- Produces (OpenAPI·Kotlin 동일 이름):
  - `QuoteLine` 추가 필드(required): `productId: Long`, `productName`, `brandName`, `optionDisplayName`, `thumbnailUrl: String`, `unitPrice: Long`(= salePrice + extraPrice), `sellerId: Long`
  - `OrderQuote` 추가 필드(required): `sellerQuotes: List<SellerQuote>`, `freeShippingThreshold: Long`, `baseShippingFee: Long`
  - `SellerQuote { sellerId: Long, sellerName: String, itemsAmount: Long, shippingFee: Long, shippingFeePayer: "BUYER"|"PLATFORM" }`
  - `OrderableSkuInfo.thumbnailUrl: String` 추가

- [ ] **Step 1: OpenAPI 수정**
- [ ] **Step 2: 실패하는 테스트 — `OrderQuoteApiTest`**
  - `판매자_두_명_5만원_미만이면_판매자마다_구매자_부담_배송비_3000원이다` — 20,000(B) + 20,000(C): `sellerQuotes` 2건 각각 `shippingFee=3000, shippingFeePayer=BUYER`, `buyerShippingFee=6000`, `totalAmount=46000`
  - `5만원_이상이면_배송비는_플랫폼_부담이다` — 30,000 + 30,000: 각 `shippingFeePayer=PLATFORM`, `buyerShippingFee=0`
  - `판매자별_합계가_전체와_맞는다` — `sellerQuotes.sumOf(itemsAmount) == itemsAmount`, `BUYER`인 `shippingFee` 합 `== buyerShippingFee`
  - `견적_항목에_표시용_상품_정보가_있다` — `productName`, `brandName`, `optionDisplayName`, `thumbnailUrl`(비어 있지 않음), `unitPrice`, `sellerId`
  - `견적에_무료배송_기준과_기본_배송비가_있다` — `freeShippingThreshold=50000, baseShippingFee=3000`
- [ ] **Step 3: 실패 확인** `./gradlew test --tests '*OrderQuoteApiTest'`
- [ ] **Step 4: 구현.** `OrderService.quote`가 이미 만드는 `order`에서 `order.sellerOrders`로 `sellerQuotes`를 만들고, 표시 필드는 SKU 조회 결과에서 채운다 — `snapshot()`은 도메인 `OrderLine`만 돌려주므로 `thumbnailUrl`을 도메인에 넣지 말고 `skuId → OrderableSkuInfo` 맵을 따로 들고 매핑한다(`thumbnailUrl`은 빈 문자열일 수 있다). **금액 계산 코드는 바꾸지 않는다**(응답 매핑만).
- [ ] **Step 5: `./gradlew test --tests '*OrderQuote*' --tests '*OrderServiceTest' --tests '*OpenApiContractTest'` 통과 → `pnpm gen:api` → Commit** `feat(backend): 주문 견적에 판매자별 배송비와 표시용 상품 정보 추가`

---

### Task 4: 장바구니 판매자 정보·상태, 담기 멱등 경로 (백엔드)

스펙 §5-3(장바구니 부분), §5-4, §5-5.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`, `cart/application/{CartQueryService,CartResults,ProductReader}.kt`, `cart/infrastructure/ProductReaderImpl.kt`, `common/idempotency/IdempotencyProperties.kt`
- Create: `cart/application/SellerReader.kt`, `cart/infrastructure/SellerReaderImpl.kt`, 테스트 `cart/application/FakeSellerReader.kt`
- Test: `cart/presentation/CartApiTest.kt`, `cart/application/CartServiceTest.kt`(조회 테스트 위치는 기존 파일 관례를 따른다)

**Interfaces:**
- Produces:
  - `CartItemResult.sellerId: Long?`, `CartItemResult.sellerName: String?` (OpenAPI: required + nullable. SKU가 사라진 항목만 null)
  - `cart.application.SellerReader { fun findSellers(sellerIds: Collection<Long>): Map<Long, CartSellerInfo> }`, `data class CartSellerInfo(sellerId: Long, companyName: String, orderable: Boolean)` — `orderable`은 order의 `SellerReaderImpl`과 **같은 기준**(`findSettlementSnapshots(...).canManage`). 빈 이름 `@Component("cartSellerReader")`.
  - `CartSkuInfo.sellerId: Long`
  - `addCartItem`에 `Idempotency-Key` 필수(OpenAPI 파라미터 + 400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_IN_PROGRESS`, 422), `IdempotencyProperties.paths`에 `"/api/v1/cart/items"`

- [ ] **Step 1: OpenAPI 수정**
- [ ] **Step 2: 실패하는 테스트 — `CartApiTest`**
  - `장바구니_항목에_판매자_정보가_있다` — `sellerId`, `sellerName == 회사명`
  - `정지된_판매자의_항목은_PAUSED이고_구매_가능_금액에서_빠진다` — 담기 → 관리자 정지 → `availability=PAUSED`, `availableAmount`에 미포함, `sellerName`은 채워짐
  - `정지가_풀리면_다시_AVAILABLE이다`
  - `장바구니가_AVAILABLE이라고_한_항목만으로_견적하면_판매자_사유로_거절되지_않는다` — 정지 판매자 항목 1 + 정상 판매자 항목 1에서 `AVAILABLE` 항목만 골라 `POST /orders/quote` → 200
  - (`CartServiceTest`, Fake 사용 — API로는 SKU를 없앨 수 없다) `SKU가_사라진_항목은_판매자_정보가_null이다`, `판매자가_조회_결과에_없으면_PAUSED다`
  - `담기에_멱등_키가_없으면_400이다`(`IDEMPOTENCY_KEY_REQUIRED`), `같은_키로_두_번_담아도_수량은_한_번만_더해진다`
- [ ] **Step 3: 실패 확인** `./gradlew test --tests '*CartApiTest'`
- [ ] **Step 4: 구현.** `CartQueryService.get`: SKU 조회 뒤 `sellerReader.findSellers(판매자 ID들)` 1회. 판매자가 없거나 `orderable=false`이고 SKU `availability`가 `AVAILABLE`이면 `PAUSED`로 바꾼다(이미 다른 구매 불가 사유면 그대로). `IdempotencyProperties.paths`에 경로 추가.
- [ ] **Step 5: 기존에 `/api/v1/cart/items`를 키 없이 POST하는 호출을 고친다.** `CommerceTestSupport.addToCart`와 `CartApiTest`의 직접 POST를 `postIdem`으로 바꾼다. 비로그인 401 케이스(`CartApiTest`, `CommerceAuthorizationApiTest`)는 그대로 둔다. `common/idempotency/IdempotencyFilterTest`가 `/api/v1/cart/items`를 "대상이 아닌 경로" 예시로 쓰므로 다른 비대상 POST 경로(예: `/api/v1/seller/brands`)로 바꾼다. `grep -rn "cart/items" src/test perf frontend/e2e`로 남은 곳이 없는지 확인한다.
- [ ] **Step 6: `./gradlew test`(전체) 통과 확인 → `pnpm gen:api && pnpm check:api` → Commit** `feat(backend): 장바구니에 판매자 정보·판매자 상태 반영, 담기에 Idempotency-Key 적용`

---

### Task 4b: 주문 응답 추가 필드 2개 (백엔드, 시안 확인에서 사용자 결정 2026-11-12)

스펙 §5-2.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`, `order/application/{OrderQueryService,OrderResults}.kt`
- Test: `order/presentation/OrderQueryApiTest.kt`(또는 `OrderCancelApiTest.kt`), `order/application/OrderQueryServiceTest.kt`

**Interfaces:**
- Produces: `OrderDetail.remainingAmount: Long`(required, = `totalAmount − refundedAmount`, 구매자·관리자 상세 공통), `OrderSummary.expiresAt: LocalDateTime`(required, 주문의 결제 만료 시각 — `OrderDetail.expiresAt`과 같은 값)

- [ ] **Step 1: OpenAPI 수정** (`remainingAmount` 설명: "결제 금액에서 누적 환불액을 뺀 남은 결제 금액. 화면은 이 값을 그대로 보여준다")
- [ ] **Step 2: 실패하는 테스트**
  - `결제_직후_남은_결제_금액은_결제_금액과_같다` — B 30,000 + C 30,000 결제: `remainingAmount=60000`
  - `부분_취소_뒤_남은_결제_금액은_결제_금액에서_환불액을_뺀_값이다` — C 항목 취소(환불 27,000): `refundedAmount=27000, remainingAmount=33000`
  - `전부_취소하면_남은_결제_금액은_0이다` — 나머지도 취소: `refundedAmount=60000, remainingAmount=0`
  - `결제_전_주문의_남은_결제_금액은_주문_총액이다` — `PENDING_PAYMENT`: `remainingAmount == totalAmount`
  - `주문_목록에_결제_만료_시각이_있다` — `content[0].expiresAt`이 상세의 `expiresAt`과 같다(관리자 목록도)
- [ ] **Step 3: 실패 확인 → 구현(`OrderQueryService.toDetail`·`toSummary` 매핑. 돈 계산 클래스는 건드리지 않는다) → `./gradlew test --tests '*Order*' --tests '*OpenApiContractTest'` 통과 → `pnpm gen:api && pnpm check:api`**
- [ ] **Step 4: Commit** `feat(backend): 주문 상세에 남은 결제 금액, 주문 목록에 결제 만료 시각 추가`

---

### Task 5: 백엔드 보강 마무리 점검

- [ ] **Step 1: `./gradlew test` 전체 통과, `git diff dev --stat -- src/main`에 돈 계산 클래스(`RefundCalculator`, `DiscountAllocator`, `CouponTerms`, `ReturnRefundCalculator`, `SettlementCalculator`, `Settlement`)와 `db/migration`이 없음을 확인**
- [ ] **Step 2: `./gradlew --stop`, `docker ps`에 테스트 컨테이너가 남아 있지 않음을 확인**

---

### Task 6: 프론트 공통 기반

스펙 §11, §8-6(남은 시간), §9(택배사).

**Files:**
- Modify: `frontend/src/hooks/useIdempotentSubmit.ts`(+test), `src/api/idempotency.ts`(+test), 위 훅의 기존 호출자 6곳(Interfaces 참고), `lib/money.ts`(+test), `lib/datetime.ts`(+test), `lib/query-keys.ts`, `api/errors.ts`(+test), `components/common/StatusChip.tsx`(+test), `frontend/.env.example`, `frontend/scripts/e2e-server.sh`
- Create: `hooks/useIdempotentAction.ts`(+test), `features/orders/carriers.ts`(+test), `features/payment/config.ts`

**Interfaces:**
- Produces:
  - `isAnswered(e: unknown): boolean`(`src/api/idempotency.ts`에 추가) — 서버가 답한 실패인가: `e instanceof ApiError && e.status >= 400 && e.status < 500`. 단, 코드 `IDEMPOTENCY_IN_PROGRESS`(409)는 **false**다 — 같은 키의 첫 요청이 아직 처리 중이라는 뜻이지 저장된 결과가 아니므로 키를 유지해야 한다(리뷰 반영 2026-11-12). **`ApiError`의 `status === 0`(네트워크 오류·본문 없음, `client.ts`가 그렇게 만든다), `>= 500`, `ApiError`가 아닌 오류는 false**(응답을 받지 못함).
  - `useIdempotentSubmit(form, submit, opts?: { onError?: (e: unknown) => void })` — **계약 변경**: `submit`은 실패하면 **반드시 던진다**(안에서 삼키지 않는다). 훅이 잡아서 ① 키를 정하고 ② `opts.onError(e)`를 부르고 ③ 삼킨다(`onSubmit`·`retry`는 reject하지 않는다). 키 규칙: `submit`이 resolve되거나 `isAnswered(e)`면 저장된 키를 버린다(다음 제출은 새 키). 그렇지 않으면(응답 없음) 키를 유지해 같은 값 재제출·`retry()`가 같은 키를 쓴다. 값이 바뀌면 지금처럼 새 키.
  - 기존 호출자 6곳(`features/seller/apply/components/ApplyForm.tsx`, `features/seller/products/components/{ProductForm,PriceDialog}.tsx`, `features/seller/brands/components/BrandDialog.tsx`, `features/auth/components/SignupForm.tsx`, `features/admin/categories/components/CategoryDialog.tsx`)을 새 계약으로 옮긴다: `submit` 안의 `try/catch`를 없애고 같은 처리를 `onError`로 옮긴다(화면 동작은 그대로).
  - `useIdempotentAction<TArgs>(action: (args: TArgs, key: string) => Promise<void>, opts?: { onError?: (e: unknown) => void }): { run: (args: TArgs) => Promise<void>; pending: boolean }` — 폼 없는 요청용. 같은 계약·같은 키 규칙(직전 시도가 응답 없이 실패했고 `JSON.stringify(args)`가 같으면 같은 키), 처리 중 재호출 무시.
  - **이후 태스크(8·10·11·12)의 "오류 처리"는 전부 이 `onError`에서 한다.**
  - `money.ts`: `amountUntil(threshold: number, amount: number): number` — `max(threshold − amount, 0)`, 안전 정수 검사.
  - `money.ts`: `floorDiv(n: number, d: number): number`, `mod(n: number, d: number): number` — 안전 정수 전용 헬퍼. ESLint가 `/`·`%`를 `money.ts` 밖에서 막으므로 시간 계산도 이 헬퍼를 쓴다(ESLint 규칙·CLAUDE.md는 바꾸지 않는다).
  - `datetime.ts`: `secondsUntilKst(kstLocalDateTime: string, nowMs: number): number` — 초 이하 소수(최대 6자리)를 잘라낸 뒤 `+09:00`을 붙여 해석, 지났으면 0. `formatCountdown(seconds: number): string` — `"MM:SS"`. 둘 다 `floorDiv`·`mod` 사용.
  - `carriers.ts`: `CARRIERS: readonly { code: string; name: string }[]`(CJ대한통운 `04`, 한진택배 `05`, 롯데택배 `08`, 우체국택배 `01`, 로젠택배 `06` — `SweetTrackerDeliveryTracker`가 `code`로 그대로 보내는 값), `carrierName(code: string | null | undefined): string`(모르는 코드는 코드 그대로, null은 `""`).
  - `payment/config.ts`: `PG_PROVIDER: "mock" | "toss"`(`process.env.NEXT_PUBLIC_PG_PROVIDER === "toss" ? "toss" : "mock"`), `TOSS_CLIENT_KEY: string`.
  - `query-keys.ts`: `cart: ["cart"]`, `orders: { list(params), detail(id), cancelPreview(orderId, itemId), root: ["orders"] }`, `sellerOrders: { list(params), detail(id), count(status), root }`, `adminOrders: { list(params), detail(id), root }`, `checkout.quote(items)`.
  - `StatusChip`: kind `order`(`PENDING_PAYMENT` 결제 대기 / `PAID` 결제 완료 / `COMPLETED` 구매 확정 / `CANCELED` 취소), `sellerOrder`(`PAID` 배송 준비 중 / `SHIPPING` 배송 중 / `DELIVERED` 배송 완료 / `CONFIRMED` 구매 확정 / `CANCELED` 취소 / `PENDING_PAYMENT` 결제 대기), `orderItem`(`ORDERED` 주문 완료 / `CANCEL_REQUESTED` 취소 확인 중 / `CANCELED` 취소 / `RETURN_REQUESTED` 반품 요청 / `RETURN_APPROVED` 반품 승인 / `RETURN_REFUNDING` 반품 환불 중 / `RETURNED` 반품 완료), `payment`(`READY` 결제 대기 / `IN_PROGRESS`·`UNKNOWN` 확인 중 / `APPROVED` 승인 / `PARTIAL_CANCELED` 부분 환불 / `CANCELED` 전액 환불 / `FAILED` 실패). 기존 kind 추가 방식과 색 매핑 관례를 따른다. 모르는 값은 값 그대로 중립 칩.
  - `errors.ts` 추가: `ORDER_NOT_FOUND`·`SELLER_ORDER_NOT_FOUND` → `page`, `ORDER_INVALID` → `form`.

- [ ] **Step 1: 실패하는 테스트 — `useIdempotentSubmit.test.tsx`(기존 "같은 값이면 같은 키" 테스트를 새 규칙으로 고친다)**
  - `서버가_409로_답한_뒤_같은_값으로_다시_제출하면_새_키를_쓴다` (Review Focus 3)
  - `성공한_뒤_같은_값으로_다시_제출하면_새_키를_쓴다`
  - `네트워크_오류_뒤_같은_값으로_다시_제출하면_같은_키를_쓴다`(`new ApiError(0, "INTERNAL_ERROR", …)`), `5xx_뒤_retry는_같은_키를_쓴다`, `ApiError가_아닌_오류_뒤에도_같은_키를_쓴다`
  - `submit이_던지면_onError를_부르고_onSubmit은_reject하지_않는다`
  - `idempotency.test.ts`: `isAnswered` — 409·402·422 true / status 0·500·503·`new Error()` false
  - `값을_고치면_새_키를_쓴다`, `제출_중_두_번째_호출은_무시한다`(기존 유지)
- [ ] **Step 2: `useIdempotentAction.test.tsx`** — 위와 같은 4가지 키 규칙 + `처리_중_재호출은_무시한다` + `인자가_다르면_새_키를_쓴다`
- [ ] **Step 3: 순수 함수 테스트**
  - `money.test.ts`: `amountUntil(50000, 42000) === 8000`, `amountUntil(50000, 50000) === 0`, `amountUntil(50000, 61000) === 0`
  - `datetime.test.ts`: `secondsUntilKst("2026-11-12T10:15:00", Date.parse("2026-11-12T10:00:00+09:00")) === 900`, 지난 시각 → 0, `secondsUntilKst("2026-11-12T10:15:00.123456", …) === 900`, `formatCountdown(754) === "12:34"`, `formatCountdown(0) === "00:00"`
  - `carriers.test.ts`: `carrierName("04") === "CJ대한통운"`, `carrierName("99") === "99"`, `carrierName(null) === ""` (Review Focus 5)
  - `StatusChip.test.tsx`: `orderItem`/`RETURN_REFUNDING` → "반품 환불 중", 모르는 값 → 값 그대로
- [ ] **Step 4: `pnpm test` 실패 확인 → 구현(호출자 6곳 이전 포함) → 통과 확인. 기존 F1 폼 테스트(`BrandDialog.test.tsx` 등)가 그대로 통과해야 한다**
- [ ] **Step 5: `.env.example`에 추가**
  ```
  # 결제 방식: mock(기본) | toss. 백엔드 KAIROS_PG_PROVIDER 와 같은 값으로 맞춘다(다르면 결제 승인이 실패한다).
  NEXT_PUBLIC_PG_PROVIDER=mock
  # toss 일 때만: 토스페이먼츠 테스트 "클라이언트 키"(공개용). 시크릿 키는 백엔드 .env 의 TOSS_SECRET_KEY 에만 둔다.
  # NEXT_PUBLIC_TOSS_CLIENT_KEY=
  ```
  `scripts/e2e-server.sh`의 `export`에 `NEXT_PUBLIC_PG_PROVIDER=mock`을 더한다.
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 멱등 키 규칙 수정과 F2 공통 기반(상태 칩, 택배사, 남은 시간) 추가`

---

### Task 7: 장바구니 (구현 전 Task 1 사용자 확인 필요)

스펙 §8-1, §8-2, §6(헤더). 시안 `design/user-cart.html`.

**Files:**
- Create: `features/cart/**`, `app/(store)/cart/page.tsx`
- Modify: `features/catalog/components/ProductDetailView.tsx`, `components/layout/StoreHeader.tsx`

**Interfaces:**
- Consumes: Task 4 스키마(`CartItemResult.sellerId/sellerName`), Task 3(`OrderQuote.sellerQuotes`, `freeShippingThreshold`), Task 6(`useIdempotentAction`, `amountUntil`, `queryKeys.cart`)
- Produces:
  - `features/cart/api.ts`: `useCart()`, `useAddCartItem()`(내부에서 `useIdempotentAction`, `Idempotency-Key` 헤더), `useChangeCartQuantity()`, `useRemoveCartItem()`, `useCartCount(): number | null`(비로그인 null), 타입 `CartItem`, `Cart`
  - `features/checkout/api.ts`: `useQuote(items: { skuId: number; quantity: number }[], enabled: boolean)` — 이 태스크에서 만든다(Task 8도 쓴다). 쿼리 키 `queryKeys.checkout.quote(items)`, `placeholderData: keepPreviousData`, 타입 `OrderQuote`, `QuoteLine`, `SellerQuote`
  - `features/cart/selection.ts`:
    - `isSelectable(item: CartItem): boolean` — `availability === "AVAILABLE"`
    - `initialSelection(items): Set<number>` — 선택 가능한 `cartItemId` 전부
    - `toggleItem(sel, id)`, `toggleAll(items, sel)`, `toggleSeller(items, sel, sellerId)`, `pruneSelection(items, sel)`(사라졌거나 구매 불가가 된 ID 제거)
    - `groupBySeller(items): { sellerId: number | null; sellerName: string; items: CartItem[] }[]`(판매자 정보가 null이면 "판매 종료 상품" 묶음, 맨 뒤)
    - `selectedQuoteItems(items, sel): { skuId; quantity }[]`
    - `unavailableLabel(availability): string` — `SOLD_OUT` 품절 / `PAUSED` 판매 중지 / `ENDED` 판매 종료 / `DISCONTINUED` 판매 종료
    - `clampQuantity(input: string): number` — 1~999, 숫자가 아니면 1
  - `checkoutHref.fromCart(cartItemIds: number[]): string` → `/checkout?cartItemIds=1,2`, `checkoutHref.direct(skuId, qty): string` → `/checkout?skuId=&qty=` (`features/checkout/checkout-params.ts`에 두고 Task 8이 파서를 더한다)

- [ ] **Step 1: 실패하는 테스트 — `selection.test.ts`**
  - `처음에는_구매_가능한_항목만_선택된다`, `구매_불가_항목은_토글해도_선택되지_않는다`
  - `전체_선택은_구매_가능한_항목만_고르고_다시_누르면_모두_해제한다`, `판매자_묶음_선택은_그_판매자의_구매_가능_항목만_바꾼다`
  - `다시_읽은_장바구니에서_사라졌거나_구매_불가가_된_항목은_선택에서_빠진다`
  - `판매자별로_묶고_판매자_정보가_없는_항목은_마지막_묶음이다`
  - `수량_입력은_1에서_999로_맞춘다` — `"0"→1`, `"-3"→1`, `"1000"→999`, `"abc"→1`, `"12"→12`
- [ ] **Step 2: 실패 확인 → `selection.ts`, `api.ts`, `checkout/api.ts`의 `useQuote`, `checkout-params.ts`의 `checkoutHref` 구현 → 통과**
- [ ] **Step 3: 화면 구현(시안 구조 그대로).** `CartPage`: 선택 상태는 `useState`, 장바구니를 다시 읽을 때마다 `pruneSelection`. 요약은 `useQuote(selectedQuoteItems, 선택 ≥ 1)`의 값만 표시(`itemsAmount`, 판매자 묶음마다 `sellerQuotes`의 배송비와 부담 주체, `buyerShippingFee`, `totalAmount`). 무료배송 문구는 `amountUntil(quote.freeShippingThreshold, quote.itemsAmount)`. 견적이 409 `SKU_NOT_ORDERABLE`이면 장바구니 쿼리를 무효화한다. 수량 변경은 300ms 모아 `PATCH`, 응답 뒤 장바구니·견적 무효화. 삭제는 확인 없이. "선택 삭제"(선택 항목을 차례로 DELETE, 구매 불가 항목은 행의 삭제 버튼으로). 선택 0이면 "주문하기" 비활성. 빈 장바구니는 `EmptyState`("장바구니가 비어 있습니다" → `/products`). 페이지는 `RequireAuth`.
- [ ] **Step 4: 상품 상세.** "구매 기능은 곧 열립니다" 문구를 지우고 `AddToCartButtons`(장바구니 / 바로 구매)를 넣는다. 옵션 미선택·품절 조합이면 둘 다 `disabled`. 장바구니: 비로그인이면 `/login?next=<현재 경로>`, 로그인이면 `useAddCartItem` → 성공 토스트 "장바구니에 담았습니다"(액션 "장바구니 보기" → `/cart`) + `queryKeys.cart` 무효화. 바로 구매: `checkoutHref.direct(sku.skuId, qty)`로 이동(비로그인이면 그 주소를 `next`로).
- [ ] **Step 5: 헤더.** 로그인 상태에서 "장바구니"(`/cart`, `useCartCount`가 1 이상이면 숫자 배지, `aria-label="장바구니 n개"`)와 "마이"(`/my/orders`) 링크를 "판매자 센터" 앞에 추가한다.
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 장바구니 화면과 상품 상세 구매 버튼, 헤더 장바구니·마이 추가`

---

### Task 8: 주문서

스펙 §8-3. 시안 `design/user-checkout.html`.

**Files:**
- Create: `features/checkout/{schemas.ts,postcode.ts}`, `features/checkout/components/*`, `app/(store)/checkout/page.tsx`
- Modify: `features/checkout/{api.ts,checkout-params.ts}`

**Interfaces:**
- Consumes: Task 7의 `useQuote`, `useCart`, `checkoutHref`; Task 6의 `useIdempotentSubmit`
- Produces:
  - `parseCheckoutParams(sp: URLSearchParams): { mode: "cart"; cartItemIds: number[] } | { mode: "direct"; skuId: number; quantity: number } | null` — null 조건: 둘 다 있음, 둘 다 없음, 숫자가 아닌 ID, 중복 제거 후 0개, `qty`가 1~999 밖
  - `resolveCheckoutItems(parsed, cart: Cart | undefined): { skuId; quantity }[] | null` — cart 모드는 `cartItemIds`에 해당하고 `AVAILABLE`인 항목만. 결과 0개면 null
  - `shippingAddressSchema`(zod): `receiverName` 1~50, `phone` 1~20 + `/^[0-9-]+$/`, `zipCode` 1~10, `address1` 1~200, `address2` 1~200, `memo` 0~200(빈 문자열은 제출 시 `undefined`)
  - `useCreateOrder()`: `(body: CreateOrderRequest, key: string) => Promise<CreateOrderResponse>`
  - `useRecentShippingAddress()`: `GET /orders?page=0&size=1` → 첫 주문의 `GET /orders/{id}` → `shippingAddress | null`(버튼을 누를 때 조회)
  - `openPostcode(onComplete: (r: { zipCode: string; address1: string }) => void): Promise<void>` — 카카오 우편번호 스크립트(`https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js`)를 처음 호출 때 한 번만 불러 연다. 로드 실패 시 reject.
  - 주문 생성 뒤 이동: `orderStatus === "PAID"`면 `/my/orders/{id}/complete`, 아니면 `/my/orders/{id}/pay?auto=1` — `router.replace`

- [ ] **Step 1: 실패하는 테스트 — `checkout-params.test.ts`** (Review Focus 4)
  - `cartItemIds를_숫자_배열로_읽고_중복을_없앤다` — `"3,1,3"` → `[3,1]`
  - `skuId와_qty를_읽는다`
  - `잘못된_쿼리는_null이다` — `cartItemIds=abc`, `cartItemIds=`, `skuId=5`(qty 없음), `skuId=5&qty=0`, `qty=1000`, `skuId=5&qty=2&cartItemIds=1`, 빈 쿼리
  - `장바구니에_없거나_구매_불가인_항목은_빠지고_남은_것이_없으면_null이다`
- [ ] **Step 2: `schemas.test.ts`** — 경계 길이(50/51, 200/201), 연락처에 문자 포함 시 실패, 빈 메모 허용, OpenAPI `ShippingAddress`의 `maxLength`와 같은 값인지 주석으로 근거 표시
- [ ] **Step 3: 실패 확인 → 구현 → 통과**
- [ ] **Step 4: 화면 구현.** `CheckoutPage`: `parseCheckoutParams` → null이면 `/cart`로 `replace`. cart 모드는 `useCart` 로드 뒤 `resolveCheckoutItems`. `useQuote(items)`로 판매자별 상품(`QuoteLine` 표시 필드, `sellerQuotes`로 묶음·배송비·부담 주체)과 금액을 그린다. 결제 버튼 문구 `"{formatKrw(quote.totalAmount)} 결제하기"`. 제출: `useIdempotentSubmit(form, …)` → `POST /orders`(`items`, `expectedTotalAmount: quote.totalAmount`, `shippingAddress`). 견적이 로딩·오류면 버튼 비활성.
- [ ] **Step 5: 오류 처리.** `ORDER_PRICE_CHANGED` → 견적 무효화 + 폼 위 배너 "금액이 변경되었습니다. 확인 후 다시 결제해 주세요"(자동 재제출 없음). `STOCK_SOLD_OUT`·`SKU_NOT_ORDERABLE` → 토스트 + cart 모드 `/cart`, direct 모드 뒤로(상품 상세). `ORDER_INVALID`·`IDEMPOTENCY_KEY_REUSED` → 폼 상단. 그 외 `resolveError`.
- [ ] **Step 6: 주소 검색·최근 배송지.** "주소 검색" → `openPostcode` → `setValue(zipCode, address1)`, 상세 주소로 포커스. 실패 시 토스트 "주소 검색을 불러오지 못했습니다. 직접 입력해 주세요". 우편번호·주소 입력란은 읽기 전용이 아니다. "최근 배송지 불러오기"는 주문이 없으면 숨긴다.
- [ ] **Step 7: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 주문서 화면(배송지, 견적, 주문 생성) 추가`

---

### Task 9: 결제 (결제 페이지, 모의·토스 결제창, 승인, 완료)

스펙 §7 전체, §8-4, §12.

**Files:**
- Create: `features/payment/**`, `features/demo/MockOutcomePicker.tsx`, `app/(store)/checkout/success/page.tsx`, `checkout/fail/page.tsx`, `my/orders/[orderId]/pay/page.tsx`, `my/orders/[orderId]/complete/page.tsx`
- Create: `features/orders/api.ts`(이 태스크에서 `useOrder`만), `features/orders/polling.ts`(+test)
- Modify: `frontend/scripts/check-bundle.sh`, `frontend/package.json`(토스 SDK)

**Interfaces:**
- Consumes: Task 6(`PG_PROVIDER`, `TOSS_CLIENT_KEY`, `useIdempotentAction`은 쓰지 않음 — 승인 키는 저장소 기반), `secondsUntilKst`
- Produces:
  - `features/orders/api.ts`: `useOrder(orderId: number, opts?: { refetchInterval?: number | false })`, 타입 `OrderDetail`, `SellerOrderResult`, `OrderItemResult`
  - `features/orders/polling.ts`: `needsPolling(order: OrderDetail): boolean` — 결제 상태 `IN_PROGRESS`/`UNKNOWN` 또는 `CANCEL_REQUESTED` 항목 존재. `POLL_INTERVAL_MS = 3000`, `POLL_LIMIT_MS = 120000`, `usePollingWindow(active: boolean): { interval: number | false; expired: boolean }`(active가 된 시점부터 2분)
  - `payment-key.ts`: `type MockOutcome = "approve" | "reject" | "unknown" | "lost"`, `mockPaymentKey(outcome: MockOutcome, uuid: string): string` — `approve → "mock-"+uuid`, 나머지 접두사는 **인자로 받는다**: `mockPaymentKey(outcome, uuid, prefixes?: Record<Exclude<MockOutcome,"approve">, string>)`. 실패 접두사 상수(`fail-`, `unknown-`, `lost-`)는 `features/demo/MockOutcomePicker.tsx`에만 둔다(운영 번들에서 빠지게).
  - `successUrl(orderId: number): string` → `/checkout/success?oid={orderId}`, `failUrl(orderId)` → `/checkout/fail?oid={orderId}`; 모의 결제 이동 주소 `mockSuccessHref(orderId, orderNo, paymentKey, amount)` → `/checkout/success?oid=&orderId={orderNo}&paymentKey=&amount=`
  - `order-ref-store.ts`: `rememberOrderRef(orderNo: string, orderId: number): void`, `recallOrderId(orderNo: string): number | null`(`sessionStorage`, 예외는 삼키고 null)
  - `confirm-key-store.ts`: `confirmKeyFor(paymentKey: string): string` — `sessionStorage["kairos:confirm:"+paymentKey]`에 있으면 그 값, 없으면 새 UUID를 저장하고 반환. storage 사용 불가면 모듈 메모리 Map으로 대체.
  - `confirm-state.ts`: `parseConfirmParams(sp): { orderNo: string; paymentKey: string; amount: number; orderId: number | null } | null`(`oid`가 없으면 `recallOrderId(orderNo)`), `type ConfirmView = { kind: "approved"; orderId: number } | { kind: "pending" } | { kind: "rejected" } | { kind: "error"; message: string }`, `toConfirmView(result: { ok: true; data: ConfirmPaymentResponse } | { ok: false; error: unknown }): ConfirmView`
    - `APPROVED` → approved / `UNKNOWN` → pending
    - `ApiError` 409 `PAYMENT_IN_PROGRESS`·`IDEMPOTENCY_IN_PROGRESS` → pending / `ApiError`의 `status === 0`(네트워크 오류)·`>= 500`·`ApiError`가 아닌 오류 → pending (`!isAnswered(e)`)
    - 402 `PAYMENT_REJECTED` → rejected / 그 외 `ApiError` → error(서버 메시지)
  - `toss.ts`: `requestTossPayment(args: { orderNo: string; orderName: string; amount: number; orderId: number }): Promise<void>` — SDK를 동적 import
  - `MockOutcomePicker`(default export): `({ value, onChange }: { value: MockOutcome; onChange(v: MockOutcome, prefixes: Record<Exclude<MockOutcome, "approve">, string>): void }) => JSX`. 접두사 상수는 이 파일 안에만 있다.

- [ ] **Step 1: 실패하는 테스트**
  - `payment-key.test.ts`: `정상_결제_키는_mock_접두사다`, `결과별_접두사를_붙인다`(prefixes 전달 시 `fail-…`), `접두사가_없으면_실패_결과도_정상_키를_만든다`(운영 번들 안전)
  - `confirm-key-store.test.ts`: `같은_paymentKey는_항상_같은_키를_준다`(다시 호출·모듈 재로딩 흉내), `다른_paymentKey는_다른_키를_준다`, `sessionStorage가_막혀도_동작한다`
  - `confirm-state.test.ts`: 위 매핑 전부(각 1 테스트) + `쿼리가_빠지거나_amount가_숫자가_아니면_null이다` + `oid가_없으면_저장해_둔_주문_ID를_쓴다` (Review Focus 2)
  - `polling.test.ts`: `결제_확인_중이면_다시_조회한다`, `취소_확인_중_항목이_있으면_다시_조회한다`, `둘_다_아니면_멈춘다`
  - `ConfirmPage.test.tsx`(Testing Library, `api` 모킹): 세 테스트 모두 `<StrictMode>`로 렌더한다(ref 가드와 effect cleanup의 취소 플래그를 함께 쓰면 StrictMode에서 결과가 버려져 로딩에 멈춘다 — 응답은 버리지 않는다). `승인_요청은_마운트당_한_번만_나간다`, `다시_마운트해도_같은_Idempotency_Key를_보낸다`, `네트워크_오류면_확인_중을_보여주고_완료나_실패_문구가_없다` (Review Focus 1, 2)
- [ ] **Step 2: 실패 확인 → 순수 모듈 구현 → 통과**
- [ ] **Step 3: 결제 페이지 `PayPage`(`/my/orders/[orderId]/pay`).** `useOrder`로 읽고: `PENDING_PAYMENT`가 아니거나 `secondsUntilKst(expiresAt) === 0`이거나 결제가 `IN_PROGRESS`/`UNKNOWN`이면 `/my/orders/{id}`로 `replace`. 주문 요약(주문번호, "첫 상품명 외 n건", 결제 금액)과 남은 시간, "결제하기" 버튼. `?auto=1`이면 처음 한 번 자동으로 결제창을 연다. 결제창을 열기 직전 `rememberOrderRef(orderNo, orderId)`.
  - `PG_PROVIDER === "mock"`: `MockPaymentDialog`. "결제" → `mockPaymentKey(outcome, crypto.randomUUID(), prefixes)` → `router.push(mockSuccessHref(...))`. "닫기"는 다이얼로그만 닫는다. `MockOutcomePicker`는 F1 `app/layout.tsx`의 DemoBar와 같은 방식 — `process.env.NEXT_PUBLIC_DEMO_MODE === "true"` 리터럴 가드 안의 `dynamic(() => import("@/features/demo/MockOutcomePicker"))` — 으로만 불러온다(정적 import 금지). 접두사는 picker가 `onChange(outcome, prefixes)` 콜백으로 넘기고, 결제 페이지는 `features/demo`의 상수를 import하지 않는다.
  - `PG_PROVIDER === "toss"`: `requestTossPayment`. `TOSS_CLIENT_KEY`가 비었으면 "토스 클라이언트 키가 설정되지 않았습니다" 오류 상태.
- [ ] **Step 4: 토스 연동 `toss.ts`.** `@tosspayments/tosspayments-sdk`를 추가하고(**현재 공식 문서로 패키지명·호출 형태를 확인**: 결제창 방식, `orderId=orderNo`, `amount`, `orderName`, `successUrl: origin + successUrl(orderId)`, `failUrl: origin + failUrl(orderId)`, 카드 결제) 구현한다. 토스가 `successUrl`의 기존 쿼리(`oid`)를 보존하는지 문서로 확인해 결과를 코드 주석에 적는다(보존하지 않아도 `recallOrderId`가 받친다).
- [ ] **Step 5: 승인 페이지 `ConfirmPage`(`/checkout/success`).** 인증 부트스트랩(`useAuth().status !== "loading"`)이 끝난 뒤 `parseConfirmParams` → null이면 오류 안내 + "주문 내역". `POST /payments/confirm`(`{ orderNo, paymentKey, amount }`, 헤더 `Idempotency-Key: confirmKeyFor(paymentKey)`)을 `useRef` 가드로 마운트당 한 번 호출 → `toConfirmView`.
  - approved: `queryKeys.cart`·`queryKeys.orders.root` 무효화 후 `router.replace(/my/orders/{id}/complete)`
  - pending: "결제 결과를 확인하고 있습니다". `orderId`가 있으면 `useOrder(orderId, { refetchInterval })`로 3초마다 조회 — 결제 `APPROVED`(주문 `PAID`)면 완료 화면으로, 주문 `CANCELED`면 rejected 화면으로. 2분이 지나면 "주문 내역에서 결과를 확인해 주세요" + 주문 상세 링크. `orderId`가 없으면 바로 "주문 내역" 링크.
  - rejected: "결제가 거절되어 주문이 취소되었습니다. 담아 둔 상품은 장바구니에 그대로 있습니다" + "장바구니로"
  - error: 서버 메시지 + 주문 상세(없으면 주문 내역) 링크
- [ ] **Step 6: `FailPage`(`/checkout/fail`, 토스 전용)** — 쿼리 `message`(없으면 "결제가 완료되지 않았습니다")와 "다시 결제"(→ `/my/orders/{oid}/pay`)·"주문 상세". **`CompletePage`** — `useOrder`; 주문이 `PAID`·`COMPLETED`가 아니면 주문 상세로 `replace`. 주문번호, 결제 금액, 배송지 요약, "주문 상세 보기"·"쇼핑 계속하기".
- [ ] **Step 7: `check-bundle.sh` 확장.** 운영 빌드 검사에 패턴 추가: 실패 접두사 상수와 선택지 문구가 없어야 한다 — `MOCK_FAIL_PATTERN='"(fail|unknown|lost)-"'` — 문자열 리터럴만 기준으로 삼는다(식별자는 압축 때 이름이 바뀌고 한글은 이스케이프될 수 있다). **운영 빌드에서 0건, 데모 빌드에서 1건 이상**을 둘 다 확인하고, 따옴표 표기가 번들과 다르면 패턴을 맞춘다. 두 빌드 모두 `TOSS_SECRET` 문자열 0건.
- [ ] **Step 8: `pnpm lint && pnpm typecheck && pnpm test && pnpm check:bundle` → Commit** `feat(frontend): 결제 페이지, 모의·토스 결제창, 결제 승인 처리와 주문 완료 화면 추가`(본문에 토스 SDK 추가 이유)

---

### Task 10: 구매자 주문 내역·상세

스펙 §8-5, §8-6. 시안 `design/user-order-list.html`, `user-order-detail.html`.

**Files:**
- Create: `features/orders/{progress.ts,actions.ts}`(+test), `features/orders/components/*`, `app/(store)/my/orders/page.tsx`, `my/orders/[orderId]/page.tsx`
- Modify: `features/orders/api.ts`

**Interfaces:**
- Consumes: Task 9(`useOrder`, `needsPolling`, `usePollingWindow`), Task 6(`StatusChip`, `carrierName`, `secondsUntilKst`, `formatCountdown`, `useIdempotentSubmit`), Task 2 스키마
- Produces:
  - `api.ts`: `useMyOrders(page: number)`, `useCancelPreview(orderId, orderItemId, enabled)`, `useCancelItem(orderId)`: `(itemId, reason, key) => Promise<CancelItemResponse>`, `useConfirmPurchase(orderId)`: `(sellerOrderId) => Promise<void>`
  - `progress.ts`: `orderProgressLabel(status: OrderStatus, sellerOrderStatuses: SellerOrderStatus[]): string`
    - `PENDING_PAYMENT` → "결제 대기", `CANCELED` → "취소", `COMPLETED` → "구매 확정"
    - `PAID`: `CANCELED`를 뺀 판매자 주문 상태를 센다. 한 종류면 그 문구("배송 준비 중"/"배송 중"/"배송 완료"/"구매 확정"), 여러 종류면 `SHIPPING → PAID → DELIVERED → CONFIRMED` 순으로 `"배송 중 1 · 배송 준비 1"`. 남은 것이 없으면 "결제 완료".
  - `actions.ts`: `canCancelItem(order, sellerOrder, item): boolean`(주문 `PAID` && 판매자 주문 `PAID` && 항목 `ORDERED`), `canConfirmPurchase(sellerOrder): boolean`(`DELIVERED` && `!hasOpenReturn`), `canPay(order, nowMs): boolean`(`PENDING_PAYMENT` && 남은 초 > 0 && 결제가 `IN_PROGRESS`/`UNKNOWN` 아님), `orderHeader(order, nowMs): "pending" | "checking" | "paid" | "completed" | "canceled"`, `refundLines(preview: CancelPreview): { label: string; amount: number; negative: boolean }[]`(항목 금액은 항상, "배송비 환불"·"배송비 차감"(negative)·"차감 배송비 반환"은 0이 아닐 때만)
  - 공유 표시 컴포넌트(관리자가 재사용): `SellerOrderCard({ sellerOrder, renderItemAction?, footer? })`, `OrderItemRow`, `PaymentSummaryCard({ order })`, `ShippingAddressCard({ address })`

- [ ] **Step 1: 실패하는 테스트**
  - `progress.test.ts`: `PAID`+`["PAID"]` → "배송 준비 중", `["SHIPPING","PAID"]` → "배송 중 1 · 배송 준비 1", `["CANCELED","DELIVERED"]` → "배송 완료", `["CANCELED","CANCELED"]`(주문 `PAID`) → "결제 완료", `PENDING_PAYMENT` → "결제 대기", `COMPLETED` → "구매 확정"
  - `actions.test.ts`: 취소 버튼 조건 4가지(배송 시작된 판매자 주문·취소 확인 중 항목·결제 대기 주문에서는 false), 구매 확정 조건(열린 반품이 있으면 false), `canPay`(만료 시각 지남 → false, 결제 확인 중 → false), `orderHeader` 5가지, `refundLines`: 27,000원 예시에서 `[항목 금액 30000, 배송비 차감 3000(negative)]`, 0인 줄은 없음, `RETURN_REQUESTED` 항목은 취소 불가 (Review Focus 5)
- [ ] **Step 2: 실패 확인 → 구현 → 통과**
- [ ] **Step 3: 주문 내역 화면.** `?page=`(URL). `OrderCard`: 주문일(`datetime.ts` 포맷), 주문번호, 이미지(`thumbnailUrl ?? 자리 표시`), `firstProductName` + (`itemCount > 1`이면 " 외 n건"), `<Money totalAmount>`, `orderProgressLabel`, "주문 상세". `PENDING_PAYMENT`면 "결제하기"(→ `/my/orders/{id}/pay`)와 남은 시간(`OrderSummary.expiresAt`, `PayCountdown` 재사용; 0이 되면 목록 재조회). 빈 상태 "주문 내역이 없습니다 → 상품 보러 가기". `Pagination`.
- [ ] **Step 4: 주문 상세 화면.** `useOrder(orderId, { refetchInterval: usePollingWindow(needsPolling(order)).interval })`. 404 → not-found. `OrderStatusHeader`(`orderHeader`별 문구, pending이면 `PayCountdown`(1초 간격, 0이 되면 주문 재조회)과 "결제하기", checking이면 "결제 결과를 확인하고 있습니다", 폴링 2분 초과 시 "새로고침해 결과를 확인해 주세요"). `SellerOrderCard`: 판매자명, `StatusChip sellerOrder`, `carrierName(carrierCode)` + 송장번호(있을 때), 배송비·부담 주체("무료배송" / "배송비 3,000원"), 항목들, `canConfirmPurchase`면 "구매 확정"(`ConfirmDialog` "확정하면 되돌릴 수 없습니다" → `PATCH`, 처리 중 버튼 잠금, 성공 시 주문 무효화). `PaymentSummaryCard`: 상품 금액, 배송비, 결제 금액, 환불 금액, 배송비 차감(`shippingFeeRecovered` > 0일 때), "남은 결제 금액"은 서버 필드 `remainingAmount`를 그대로 보여준다(프론트에서 빼지 않는다, 시안대로 한 줄). 쿠폰·포인트는 0이 아닐 때만.
- [ ] **Step 5: 항목 취소 창 `CancelItemDialog`.** 열리면 `useCancelPreview` → `refundLines` + "환불 예정 금액" `<Money refundAmount>`. `shippingFeeDeducted > 0`이면 "무료배송 기준 금액 아래로 내려가 배송비가 차감됩니다" 한 줄. 미리보기가 409면 서버 메시지를 보여주고 "취소하기" 비활성. 사유(필수, 500자, zod) + `useIdempotentSubmit` → `POST …/cancel`. 결과: `CANCELED` → 토스트 `"취소되었습니다. 환불 {금액}"`(차감이 있으면 "(배송비 3,000원 차감)"), `CANCEL_REQUESTED` → "취소를 확인하고 있습니다", 409 `PAYMENT_CANCEL_FAILED` → "취소가 거절되었습니다. 주문은 그대로입니다", 409 `ORDER_ITEM_NOT_CANCELABLE` → 서버 메시지. 어느 경우든 주문 상세와 목록을 무효화하고 창을 닫는다(거절은 창 유지).
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 주문 내역·상세, 항목 취소(환불 미리보기), 구매 확정 추가`

---

### Task 11: 판매자 주문 관리

스펙 §9. 시안 `design/seller-order.html`.

**Files:**
- Create: `features/seller/orders/**`, `app/seller/orders/page.tsx`, `app/seller/orders/[sellerOrderId]/page.tsx`
- Modify: 판매자 사이드바(`features/seller/gate/SellerGate.tsx`의 `FULL_NAV`·`titleOf`), `features/seller/dashboard/components/StatusCards.tsx`(또는 `QuickActions.tsx`), `features/seller/gate/useSellerCapabilities.ts`

**Interfaces:**
- Consumes: `OrderItemRow`·`ShippingAddressCard`(Task 10), `CARRIERS`·`StatusChip`(Task 6), `useIdempotentSubmit`
- Produces:
  - `api.ts`: `useSellerOrders(params: { status?: SellerOrderStatus; page: number })`, `useSellerOrder(id)`, `useSellerOrderCount(status)`(`size=1`의 `totalElements`), `useShip(id)`: `({ carrierCode, trackingNumber }) => Promise<void>`, `useDeliver(id)`, `useSellerCancelItem(id)`: `(itemId, reason, key) => Promise<CancelItemResponse>`
  - `tabs.ts`: `SELLER_ORDER_TABS = [전체(undefined), 발송 대기(PAID), 배송 중(SHIPPING), 배송 완료(DELIVERED), 구매 확정(CONFIRMED), 취소(CANCELED)]`, `parseSellerOrderParams(sp): { status?: SellerOrderStatus; page: number }`(모르는 status는 전체, 음수·비숫자 page는 0)
  - `shipSchema`(zod): `carrierCode` ∈ `CARRIERS` 코드, `trackingNumber` trim 후 1~50
  - `actions.ts`: `canShip(so): boolean`(`PAID` && `CANCEL_REQUESTED` 항목 없음 && `ORDERED` 항목 1개 이상), `canDeliver(so): boolean`(`SHIPPING`), `canSellerCancelItem(so, item): boolean`(판매자 주문 `PAID` && 항목 `ORDERED`)
  - `useSellerCapabilities`에 `canProcessOrders: boolean`(APPROVED만 true) 추가

- [ ] **Step 1: 실패하는 테스트** — `actions.test.ts`(`발송은_결제_완료이고_취소_확인_중_항목이_없을_때만`, `모든_항목이_취소된_판매자_주문은_발송할_수_없다`, `배송_완료는_배송_중일_때만`, `항목_취소는_배송_전_주문_완료_항목만`), `tabs.test.ts`(`모르는_상태는_전체로_읽는다`, `page가_숫자가_아니면_0이다`), `shipSchema`(`송장번호_앞뒤_공백을_없앤다`, `빈_송장번호와_51자는_거절한다`, `목록에_없는_택배사는_거절한다`)
- [ ] **Step 2: 실패 확인 → 구현 → 통과**
- [ ] **Step 3: 목록 화면.** 탭·페이지는 URL. 표: 이미지, 주문번호(상세 링크), 상품("이름 외 n건"), `<Money itemsAmount>`, `StatusChip sellerOrder`, 주문 일시. 탭별 빈 상태 문구 구분.
- [ ] **Step 4: 상세 화면.** 배송지, 항목(상태 칩, 금액), 배송비·부담 주체, 진행 시각. `PAID`: "발송 처리"(`ShipDialog` → `PATCH …/ship`, 409는 서버 메시지 토스트). `SHIPPING`: "배송 완료"(`ConfirmDialog` → `PATCH …/deliver`). 판매자 주문 `PAID` && 항목 `ORDERED`: "취소"(`SellerCancelDialog`: "배송비 차감 없이 환불됩니다" + 사유 → `POST …/cancel` with key → 결과 토스트에 환불액). `canProcessOrders`가 false면 세 버튼을 비활성 + 툴팁 "정지 중", 서버 409 `SELLER_NOT_ACTIVE`는 기존 배너 규칙. 성공 시 상세·목록·건수 무효화.
- [ ] **Step 5: 사이드바에 "주문 관리"(`/seller/orders`, `useSellerOrderCount("PAID")` 배지)를 추가한다 — `SellerGate.tsx`의 상수 `FULL_NAV`를 관리자 `app/admin/layout.tsx`의 `nav(reviewBadge)`처럼 배지를 받는 함수로 바꾸고, 전체 메뉴가 보이는 상태(`full`)일 때만 건수를 조회한다. `titleOf`에 `/seller/orders` → "주문 관리"를 더한다. 그리고 대시보드에 "발송 대기 n건" 카드(→ `/seller/orders?status=PAID`)를 추가한다.**
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 판매자 주문 관리(발송, 배송 완료, 항목 취소) 추가`

---

### Task 12: 관리자 주문

스펙 §10.

**Files:**
- Create: `features/admin/orders/**`, `app/admin/orders/page.tsx`, `app/admin/orders/[orderId]/page.tsx`
- Modify: 관리자 사이드바

**Interfaces:**
- Consumes: Task 10의 공유 표시 컴포넌트, `StatusChip`
- Produces: `api.ts`: `useAdminOrders(params: { status?: OrderStatus; memberId?: number; orderNo?: string; page: number })`, `useAdminOrder(id)`, `useAdminCancelItem(orderId)`; `parseAdminOrderParams(sp)`(잘못된 `memberId`·`status`는 무시)

- [ ] **Step 1: 실패하는 테스트** — `parseAdminOrderParams`: `memberId가_숫자가_아니면_무시한다`, `orderNo는_앞뒤_공백을_없애고_빈_값이면_무시한다`, `모르는_status는_무시한다`
- [ ] **Step 2: 구현 → 통과**
- [ ] **Step 3: 목록 화면.** 필터 폼(상태 선택, 주문번호, 회원 ID) — 제출 시 URL 갱신(page 0으로). 표: 주문번호(상세 링크), 대표 상품 외 n건, 금액, `StatusChip order`, 주문 일시. 결과 0건과 필터 0건 문구 구분.
- [ ] **Step 4: 상세 화면.** 구매자 상세의 `SellerOrderCard`·`PaymentSummaryCard`·`ShippingAddressCard` 재사용(결제하기·구매 확정 없음). 결제 정보 카드에 `payment.status` 칩, `payment.amount`, `payment.refundedAmount`, `payment.paymentKey`, `expiresAt`·`paidAt`. 취소 가능한 항목(`canCancelItem`)에 "취소"(`AdminCancelDialog`: "배송비 차감 없이 환불됩니다" + 사유 → key와 함께 POST). 결제 `UNKNOWN`/취소 확인 중이면 Task 10과 같은 폴링.
- [ ] **Step 5: 관리자 사이드바에 "주문"(`/admin/orders`) 추가**
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 관리자 주문 조회와 항목 취소 추가`

---

### Task 13: E2E

스펙 §13. 백엔드 `SPRING_PROFILES_ACTIVE=local,demo`(Mock PG·Mock 배송 조회 기본값), `KAIROS_DEMO_PASSWORD` 필요. 프론트는 `scripts/e2e-server.sh`(데모 아님, mock PG).

**Files:**
- Modify: `frontend/e2e/fixtures/api.ts`
- Create: `frontend/e2e/c2-purchase.spec.ts`, `c2-cancel.spec.ts`, `c2-payment-failure.spec.ts`

**Interfaces:**
- Produces (fixtures):
  - `createSellerWithProduct(request, admin, opts: { sellerName: string; productName: string; price: number; stock: number }): Promise<{ sellerToken: string; sellerEmail: string; productId: number; skuId: number }>` — 회원 생성 → 입점·승인 → 브랜드 승인 → 상품 등록·승인 → SKU 재고 설정(옵션 없는 상품). 기존 헬퍼를 넓혀야 한다: `registerProduct`에 `originalPrice`·`salePrice`·`options`(기본값은 지금 값, `[]` 허용) 인자를 추가하고, `api.patch`를 추가하고, `skuId`는 `GET /seller/products/{id}`의 `skus[0].skuId`, 재고는 `PATCH /seller/products/{id}/skus/{skuId}`에 `{ extraPrice: 0, stock, onSale: true }`(세 필드 모두). 정가는 판매가보다 넉넉히 크게(가격 변경 테스트용)
  - `changeSalePrice(request, sellerToken, productId, salePrice)` — `PATCH /seller/products/{id}/price`(즉시 반영, 판매가 ≤ 정가)
  - `fillShippingAddress(page)` — 수령인 "홍길동", 연락처 "010-1234-5678", 우편번호 "06236", 주소 "서울 강남구 테헤란로 1", 상세 "101호"(직접 입력)
  - `payWithMock(page)` — 모의 결제창에서 "결제" 클릭 후 주문 완료 화면 대기

- [ ] **Step 1: 픽스처 작성 후 `c2-purchase.spec.ts`** — 판매자 B(20,000원 상품), C(20,000원 상품) 준비. 구매자 UI 로그인 → 각 상세에서 "장바구니" → `/cart`에 판매자 묶음 2개, 배송비 3,000원 2건, 결제 예정 46,000원, "10,000원 더 담으면 무료배송" → 주문하기 → 배송지 입력 → **"결제하기"를 `dblclick()`으로 두 번 누른다**(첫 클릭 뒤 버튼이 잠기므로 `click()` 두 번은 타임아웃 난다) → 모의 결제 → 주문 완료 → `GET /orders`(API)로 구매자 주문이 정확히 1건 → 주문 내역 카드 "배송 준비 중" → 상세. 판매자 B로 UI 로그인 → 주문 관리 "발송 대기" 탭 → 발송 처리(CJ대한통운, 송장 "1234567890") → 배송 완료. 구매자 상세에서 B 카드 "배송 완료"·택배사명 표시 → "구매 확정" → 칩 "구매 확정". 헤더 장바구니 배지가 결제 후 사라짐.
  - 같은 spec의 두 번째 테스트 `가격이_바뀌면_안내하고_다시_결제할_수_있다`: 주문서를 연 뒤 API로 판매가를 **내림**(20,000 → 19,000) → 결제하기 → "금액이 변경되었습니다" + 새 금액 → 다시 결제하기 → 결제 페이지 진입(422 없음) (Review Focus 3)
- [ ] **Step 2: `c2-cancel.spec.ts`** — B 30,000 + C 30,000 주문·결제(무료배송). 구매자 상세에서 C 항목 "취소" → 창에 "배송비 차감" 3,000원, "환불 예정 금액" 27,000원 → 사유 입력 → 취소하기 → 항목 칩 "취소", 결제 요약 환불 금액 27,000원. B 항목 취소 → 창에 환불 예정 33,000원 → 취소 → 주문 상태 "취소", 환불 금액 60,000원. 두 번째 테스트: 판매자 화면에서 배송 전 항목 취소 → "배송비 차감 없이 환불됩니다" → 구매자 상세에 전액 환불(차감 줄 없음).
- [ ] **Step 3: `c2-payment-failure.spec.ts`**
  - `결제창을_닫으면_결제_대기로_남고_다시_결제할_수_있다`: 결제 페이지에서 "닫기" → `/my/orders/{id}` 헤더 "결제 대기" + 남은 시간 → "결제하기" → 모의 결제 → 완료
  - `결제가_거절되면_주문이_취소되고_장바구니는_그대로다`: `page.route("**/api/v1/payments/confirm", …)`로 요청 본문의 `paymentKey`를 `"fail-" + 원래값`으로 바꿔 `route.continue({ postData })` → "결제가 거절되어 주문이 취소되었습니다" → 장바구니에 항목 유지 → API로 SKU 재고가 주문 전과 같음 → 라우트 해제 후 다시 주문·결제 성공
  - `결제_응답을_받지_못하면_확인_중으로_보인다`: 같은 방식으로 `"unknown-"` 접두사 → "결제 결과를 확인하고 있습니다"가 보이고 "주문 완료"·"거절" 문구가 없음(대사 완료까지 기다리지 않는다)
- [ ] **Step 4: 실행.** 백엔드를 `local,demo`로 띄우고 `cd frontend && pnpm e2e` → 기존 4개 spec + 신규 3개 전부 통과. 실패하면 화면 버그인지 테스트 문제인지 구분해 고친다(셀렉터는 역할·이름 기반).
- [ ] **Step 5: 백엔드·프론트 서버·Gradle 데몬 종료, `docker compose`는 켜기 전 상태로 되돌린다 → Commit** `test(frontend): C2 구매·취소·결제 실패 E2E 추가`

---

### Task 14: 문서와 최종 검증

스펙 §14.

**Files:**
- Modify: `CLAUDE.md`, `README.md`(프론트 실행 절), `frontend/README.md`가 있으면 그것

- [ ] **Step 1: `CLAUDE.md`** — 상단 문서 목록에 F2 스펙·계획 추가, "현재 단계"를 **F2 구매 화면**으로, 프론트엔드 절에: 결제 방식(`NEXT_PUBLIC_PG_PROVIDER`를 백엔드 `KAIROS_PG_PROVIDER`와 맞춤, 토스 시크릿 키는 백엔드에만), 서버 강제 멱등 요청 목록에 장바구니 담기·주문 생성·결제 승인·항목 취소 추가와 새 키 규칙 한 줄, "합계·배송비·환불액은 서버 값만 표시", 승인 페이지의 `sessionStorage` 용도(토큰 아님) 한 줄.
- [ ] **Step 2: README** — 결제 방식 설정(모의/토스 테스트), 모의 결제 실패 선택지는 데모 빌드에서만, E2E 실행 전제.
- [ ] **Step 3: 전체 검증** — `./gradlew test` / `cd frontend && pnpm check:api && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle` 전부 통과, 출력 요약을 기록한다.
- [ ] **Step 4: 수동 점검 기록** — (a) 데모 빌드(`NEXT_PUBLIC_DEMO_MODE=true`)에서 모의 결제 결과 4종, (b) 카카오 주소 검색으로 주소 입력, (c) 토스 테스트 결제 1건(테스트 키가 없으면 "미실시 — 키 없음"). 결과를 PR 본문 초안에 적는다.
- [ ] **Step 5: 백그라운드 프로세스 정리 확인 → Commit** `docs: CLAUDE.md·README에 F2 구매 화면 규칙과 결제 설정 반영`
