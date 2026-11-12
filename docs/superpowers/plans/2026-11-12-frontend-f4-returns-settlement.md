# 프론트엔드 F4 반품·정산 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 구매자의 반품 요청·내역, 판매자의 반품 처리·정산, 관리자의 반품·정산·조정 화면을 실제 백엔드에 붙이고, 그에 필요한 백엔드 보강 3건(반품 환불 미리보기, 반품 응답 표시용 필드, demo 전용 정산 시연 데이터)을 넣는다.

**Architecture:** 백엔드는 조회 추가와 demo 프로필 전용 시더만 더한다. 돈 계산 클래스 변경은 `ReturnRefundCalculator`의 계산 본체 추출 하나뿐이고 결과는 그대로다. 프론트는 F1~F3 구조(`app/`은 조립, 기능은 `features/`)에 `returns`·`settlements`와 판매자·관리자 하위 기능을 더한다. 화면의 환불액·반품비·정산 금액은 전부 서버 응답 값이고, 환불이 실행되는 수거 확인은 자동 재시도하지 않는다.

**Tech Stack:** Kotlin 2.3 · Spring Boot 4.1 · QueryDSL · Testcontainers / Next.js 15 · React 19 · TanStack Query · react-hook-form + zod · openapi-fetch · Vitest · Playwright

**Spec:** `docs/superpowers/specs/2026-11-12-frontend-f4-returns-settlement-design.md` (구현자는 자기 태스크가 가리키는 절을 반드시 읽는다. 상위 규칙: `CLAUDE.md`, C4 스펙 `docs/superpowers/specs/2026-11-10-c4-returns-settlement-design.md`, F3 스펙 `docs/superpowers/specs/2026-11-12-frontend-f3-benefits-design.md`)

## Global Constraints

- 브랜치 `feature/frontend-f4-returns-settlement`. 커밋 메시지는 한국어 Conventional Commits. `main`·`dev`에 직접 커밋하지 않는다. push·PR은 계획 밖(오케스트레이터가 한다).
- **SDD**: API 변경은 `docs/api/openapi.yaml` → 구현 → 테스트 순서. OpenAPI를 고친 커밋에 `cd frontend && pnpm gen:api` 결과(`src/api/schema.d.ts`)를 함께 넣는다. `OpenApiContractTest`는 경로만 비교하므로 응답 필드는 API 테스트의 단언으로 확인한다.
- 백엔드: 4계층 DDD와 모듈 규칙(`ArchitectureTest`). 다른 모듈은 호출하는 쪽 `application`의 포트 + 자기 `infrastructure` 구현으로만 부른다. Flyway 마이그레이션·인덱스 추가 없음. PG 호출을 트랜잭션 안으로 옮기는 변경 금지.
- **돈 계산 클래스**(`ReturnRefundCalculator`, `RefundCalculator`, `DiscountAllocator`, `CouponTerms`, `SettlementCalculator`): Task 2의 계산 본체 추출이 **유일하게 허용된 변경**이다. 계산 결과가 바뀌면 안 된다 — 기존 `ReturnRefundCalculatorTest`·`OrderReturnServiceTest`·`ReturnConsistencyApiTest`의 단언을 한 줄도 고치지 않고 통과해야 한다. 그 밖에 이 클래스들을 바꿔야 할 것 같으면 **멈추고 BLOCKED로 보고**한다. PIT 임계값(mutation 79, line 99)은 현재 기준선에 걸려 있다 — 돈 계산 클래스에 코드를 더하면 그만큼 테스트를 붙인다.
- 시연 코드는 demo 프로필에서만 동작한다(기존 `DemoProfileGuard` 규칙). 서비스 코드에 시각을 덮어쓰는 분기를 넣지 않는다.
- 백엔드 테스트: 이름은 한국어, 애플리케이션 서비스 테스트는 인메모리 Fake, API 테스트는 기존 `*ApiTest` 패턴. 실행 `./gradlew test --tests '<클래스>'`(Docker 필요). 끝나면 Gradle 데몬을 남기지 않는다.
- 금액: Long/정수 원. 프론트에서 `/`, `%`는 `src/lib/money.ts`에서만(ESLint). 환불액·반품비·정산 합계·수수료를 프론트가 계산하지 않는다. F4에서 새로 허용되는 프론트 계산은 **조정 금액의 부호 붙이기**뿐이다. 날짜 계산(가장 최근에 끝난 주의 월요일, 월요일 판정)은 `src/lib/datetime.ts`의 순수 함수로만 한다.
- 프론트: 액세스 토큰은 메모리에만. 낙관적 업데이트 없음. 뮤테이션 자동 재시도 없음. 필터·탭·페이지는 URL 쿼리가 단일 진실. 모든 목록·상세는 로딩·빈·오류 세 상태. 에러 메시지는 `src/api/errors.ts` 표 또는 서버 메시지 그대로. 경로의 ID는 `lib/ids.ts`의 `parseId`. 일시는 `lib/datetime.ts`. 구분점은 `lib/join-dot.ts`. 데스크톱 전용(1280px). `hot` 색은 쓰지 않는다.
- 서버가 `Idempotency-Key`를 강제하는 요청(반품 요청, 정산 생성, 정산 조정 등록)은 `useIdempotentSubmit`으로 보낸다. 철회·승인·거절·수거 확인·중단·정산 확정·지급 완료·조정 삭제는 키 없이 처리 중 재호출을 막는다. **수거 확인은 응답을 못 받아도 자동으로 다시 보내지 않는다.**
- 데모 전용 프론트 코드를 추가하지 않는다. 어떤 번들에도 `KAIROS_ADMIN`·토스 시크릿 키가 없어야 한다(`pnpm check:bundle`).
- `design/`의 기존 시안 파일은 고치지 않는다(새 파일 추가, `index.html` 링크 추가, `assets/kairos.css`에 클래스 **추가**만 허용).
- **시안 게이트**: Task 7~12(화면 구현)는 Task 1의 시안을 사용자가 확인한 뒤에만 시작한다. 화면은 확인된 시안의 구조·문구를 따른다. Task 2~6은 기다리지 않는다.
- 구매자 페이지는 `(store)/my/(menu)` 그룹 안에 둔다(레이아웃이 `RequireAuth` + `MyPageShell`을 이미 적용하므로 페이지에서 다시 감싸지 않는다). 판매자·관리자 페이지는 기존 콘솔 레이아웃·가드를 그대로 쓴다.
- 프론트 테스트 이름은 한국어. 순수 로직은 `.ts` + `.test.ts`(Vitest), 화면 흐름은 E2E. 각 프론트 태스크의 검증: `cd frontend && pnpm lint && pnpm typecheck && pnpm test`.
- 의존성을 추가하지 않는다. claude.ai Artifact·Claude Design·Figma 토큰 사용 금지. `.env` 내용을 출력하거나 커밋하지 않는다.

## Review Focus

스펙이 암시하지만 놓치기 쉬운 입력·상황. 각 줄의 테스트는 해당 태스크에 들어 있다.

1. 미리보기가 성공했는데 요청이 실패하거나 그 반대 → 미리보기와 요청은 같은 자격 판정 함수를 쓰고, 같은 상태에서 같은 오류 코드를 낸다(Task 2 일치 테스트).
2. 수거 확인을 연타하거나 응답이 끊김 → 요청은 한 번만 나가고, 결과를 모르면 "확인하지 못했습니다"로 보이며 자동으로 다시 보내지 않는다. `REFUNDING`이면 "환불 확인 중"으로 보이고 다시 읽는다(Task 9).
3. 반품할 수 없는 항목(기한 1초 지남, 판매자 주문이 확정·배송 중, 거절·중단된 반품, 열린 반품) → "반품 요청" 버튼이 보이지 않는다. 철회한 항목은 다시 보인다(Task 6·7).
4. 정산 합계가 음수, 라인이 음수, 라인 0개(조정만), 수수료율이 섞인 정산 → 화면이 깨지지 않고 서버 값 그대로 보이며 지급액 0원과 이월 안내가 뜬다(Task 10).
5. 정산 생성에 월요일이 아닌 날짜·아직 안 끝난 주·이미 생성된 주·앞 기간 미생성 → 화면이 먼저 막거나 서버 안내를 그대로 보이고, 연타해도 한 번만 실행된다(Task 6·12).

---

## 파일 구조

**백엔드(수정·신규)** — 경로는 `src/main/kotlin/com/dogyun/kairos/` 기준
- `docs/api/openapi.yaml`
- `order/domain/{ReturnRefundCalculator,Order}.kt`(계산 본체·자격 판정 추출만)
- `order/application/{OrderReturnService,ReturnQueryService}.kt`, 반품 결과 DTO, `order/presentation/`의 반품 컨트롤러·응답
- `common/infrastructure/ClockConfig.kt`(조건부 등록), 필요하면 `JpaAuditingConfig.kt`
- 신규 `demo/infrastructure/DemoClock.kt`(이름은 빌더), `demo/application/DemoSettlementSeeder.kt`, 수정 `demo/application/DemoSeeder.kt`·`DemoCatalog.kt`·`DemoAccounts.kt`

**프론트(신규, `frontend/src/`)**
```
features/returns/      api.ts  return-text.ts(+test)  return-actions.ts(+test)  return-steps.ts(+test)  schemas.ts(+test)
                       components/{ReturnRequestDialog,ReturnPreviewBox,RefundBreakdown,ReturnSteps,ReturnStatusLine,
                                   MyReturnsPage,MyReturnDetailPage,ReturnCard}.tsx
features/settlements/  settlement-text.ts(+test)  components/{SettlementBreakdown,SettlementLinesTable,SettlementAdjustmentsTable}.tsx
features/seller/returns/      api.ts  receive-outcome.ts(+test)  tabs.ts(+test)
                              components/{SellerReturnsPage,SellerReturnDetailPage,ReturnTable,ReturnDetailView,RejectDialog,ReceiveDialog}.tsx
features/seller/settlements/  api.ts  components/{SellerSettlementsPage,SellerSettlementDetailPage}.tsx
features/admin/returns/       api.ts  params.ts(+test)  components/{AdminReturnsPage,AdminReturnDetailPage}.tsx
features/admin/settlements/   api.ts  generate.ts(+test)  adjustment-schema.ts(+test)
                              components/{AdminSettlementsPage,AdminSettlementDetailPage,GenerateDialog,PayDialog,AdminAdjustmentsPage}.tsx
app/(store)/my/(menu)/returns/page.tsx  returns/[returnId]/page.tsx
app/seller/returns/page.tsx  returns/[returnId]/page.tsx  settlements/page.tsx  settlements/[settlementId]/page.tsx
app/admin/returns/page.tsx  returns/[returnId]/page.tsx  settlements/page.tsx  settlements/[settlementId]/page.tsx  settlements/adjustments/page.tsx
```
**프론트(수정)**: `lib/{money,datetime,query-keys}.ts`, `api/errors.ts`, `components/common/StatusChip.tsx`, `components/layout/console-titles.ts`, `features/mypage/menu.ts`, 판매자·관리자 사이드 메뉴 정의, `features/checkout/api.ts`(견적 재요청 제거), `features/orders/components/{OrderDetailPage,SellerOrderCard,OrderItemRow,ConfirmPurchaseButton,PaymentSummaryCard}.tsx`, `features/orders/{actions,polling}.ts`, `e2e/fixtures/api.ts`

**시안**: `design/user-return.html`, `seller-return.html`, `seller-settlement.html`, `admin-settlement.html`, `design/index.html`(링크), `design/assets/kairos.css`(추가)

---

### Task 1: 시안 4개 (사용자 확인 게이트)

스펙 §4, §7~§9.

**Files:**
- Create: `design/user-return.html`, `design/seller-return.html`, `design/seller-settlement.html`, `design/admin-settlement.html`
- Modify: `design/index.html`(링크 4개), `design/assets/kairos.css`(추가만)

- [ ] **Step 1: 기존 시안의 구조와 토큰을 읽는다** — `design/user-order-detail.html`, `user-mypage.html`, `seller-order.html`, `seller-benefit.html`, `assets/kairos.css`. 상태 전환은 F3 시안처럼 상단의 "상태별 보기" 칩으로 한다.
- [ ] **Step 2: 네 파일을 스펙 §4 표의 "담는 것" 그대로 그린다.** 숫자는 서로 맞아야 한다. 반드시 들어갈 상태와 숫자:
  - `user-return.html`: 요청 창 — 사유 미선택 / 단순 변심(결제 금액 20,000원, 반품비 −3,000원, 발송 배송비 회수 −3,000원, 환불 예정 14,000원, "예상" 안내) / 불량(결제 금액 30,000원, 배송비 환불 +3,000원, 환불 예정 33,000원, 판매자 부담 문구) / 미리보기 불가(409 문구, 요청 버튼 막힘). 주문 상세 항목 줄 — 반품 요청 버튼과 기한, 요청됨 + 철회, 승인 · 수거 대기, 환불 확인 중, 반품 완료 + 환불 금액, 거절, 중단됨, 기한 지남. 구매 확정 창(반품 안내, 열린 반품으로 막힌 버튼). 반품 내역 목록(여러 상태), 상세(진행 단계 4칸, 환불 내역, 거절 사유), 빈 상태. 마이페이지 왼쪽 메뉴에 "반품 내역".
  - `seller-return.html`: 상태 탭과 목록(자동 승인 예정일), 상세(요청 / 승인 / 환불 확인 중 / 완료 / 거절), 승인 확인, 거절 창, 수거 확인 창(변심: 재고 복구 안내 / 불량: 재고 미복구 안내 + 판매자 부담 반품비 3,000원), 완료 토스트 "환불 금액 14,000원", 환불 거절 안내, 정지 판매자 안내.
  - `seller-settlement.html`: 목록(생성 / 확정 / 지급 완료, 빈 상태), 상세 — 상품 매출 95,000원, 판매자 쿠폰 −5,000원, 수수료 −9,000원, 배송비 +6,000원, 판매자 부담 반품비 −6,000원, 조정 0원, 합계 81,000원, 지급액 81,000원; 포함된 주문 3줄(70,500 / 13,500 / −3,000); 음수 합계 상태(지급액 0원 + 이월 안내 + 이월 조정 행); 조정이 붙은 상태(−10,000원).
  - `admin-settlement.html`: 목록과 필터, 생성 창(기본 지난주 월요일, 월요일 아님 오류, 결과: 생성 1 · 건너뜀 0 · 실패 0 / 이미 생성된 기간 / 서버 안내), 상세의 확정·지급 완료 창(메모, 지급액 0원 문구), 조정 화면(판매자 미선택 / 목록: 반영 예정 · 반영됨 · 이월 / 등록 양식: 가산·차감 / 삭제 확인 / 삭제 불가 안내).
- [ ] **Step 3: `design/index.html`에 링크를 더하고, 헤드리스 브라우저로 모든 상태를 캡처해 깨진 곳이 없는지 확인한다.**
- [ ] **Step 4: Commit** `docs(design): F4 반품·정산 시안 4개 추가`
- [ ] **Step 5: 게이트** — 오케스트레이터가 사용자에게 시안을 보여 확인받는다.

---

### Task 2: 반품 환불 미리보기 (백엔드)

스펙 §5-1.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`(생성)
- Modify: `order/domain/{ReturnRefundCalculator,Order}.kt`, `order/application/OrderReturnService.kt` 또는 `ReturnQueryService.kt`(미리보기 조회), 반품 결과 DTO, `order/presentation/`의 주문·반품 컨트롤러와 응답 DTO
- Test: 수정(추가만) `order/domain/ReturnRefundCalculatorTest.kt`, 신규 `order/presentation/ReturnPreviewApiTest.kt`, 필요하면 `order/domain/OrderTest.kt`에 자격 판정 테스트 추가

**Interfaces:**
- Produces (OpenAPI): `GET /api/v1/orders/{orderId}/items/{orderItemId}/return-preview`(operationId `previewMyOrderItemReturn`, 인증, 쿼리 `reason` 필수 enum `CHANGE_OF_MIND|DEFECTIVE|WRONG_DELIVERY`, 헤더 없음) → `ReturnPreview { orderItemId, reason, feeBearer(BUYER|SELLER), paidAmount, returnFee, outboundFeeRecovered, shippingFeeRefund, refundAmount, pointsRefund, productCouponReturnable(boolean) }` 전부 required. 오류: 400 `RETURN_INVALID`(또는 잘못된 enum의 기존 처리 코드 — 반품 요청 API가 잘못된 사유에 내는 코드와 같게), 401, 404 `ORDER_NOT_FOUND`/`ORDER_ITEM_NOT_FOUND`, 409 `RETURN_NOT_ELIGIBLE`/`RETURN_WINDOW_EXPIRED`/`RETURN_ALREADY_REQUESTED`.
- Produces (Kotlin):
  - `ReturnRefundCalculator.preview(order: Order, itemId: Long, reason: ReturnReason, fees: ReturnFeePolicy): ReturnRefundPlan` — 상태 검사 없이 금액만 계산한다(자격은 호출 전에 판정). `plan`과 `preview`는 같은 private 본체를 부른다.
  - `Order.checkReturnRequestable(itemId: Long, window: Duration, now: LocalDateTime)` — 상태를 바꾸지 않고, `requestReturn`이 던지던 것과 같은 순서·같은 코드로 던진다. `requestReturn`은 이 함수를 먼저 부른다.
  - 미리보기 조회 서비스 메서드 `previewReturn(memberId: Long, orderId: Long, orderItemId: Long, reason: ReturnReason): ReturnPreviewResult`(락 없는 읽기 트랜잭션, 남의 주문은 `ORDER_NOT_FOUND`)

- [ ] **Step 1: OpenAPI에 경로와 스키마를 추가한다.** 설명에 "수거 확인 때 실제 환불과 같은 계산이지만 **예상값**이다. 계산은 호출 시점의 주문 상태로 하며, 같은 주문의 다른 항목이 취소·반품되면 실제 환불은 달라질 수 있다. 아무것도 바꾸지 않는다"를 적는다.
- [ ] **Step 2: 실패하는 테스트 — `ReturnRefundCalculatorTest`에 추가**(기존 테스트는 고치지 않는다. 기존 픽스처 재사용)
  - `미리보기_무료배송_주문의_변심_반품은_반품비와_발송비를_빼서_14000원이다` — C4 §5-1 예시 A의 a2: `paidAmount=20000, returnFee=3000, outboundFeeRecovered=3000, shippingFeeRefund=0, refundAmount=14000`
  - `미리보기_발송비를_이미_회수한_주문의_변심_반품은_반품비만_뺀다` — 예시 A에서 a2 반품 완료 뒤 a1: `returnFee=3000, outboundFeeRecovered=0, refundAmount=37000`
  - `미리보기_불량_반품은_차감이_없고_구매자_배송비를_돌려준다` — 예시 B: `returnFee=0, shippingFeeRefund=3000, sellerBorneFee=3000, refundAmount=33000`
  - `미리보기_취소_차감_뒤_변심_반품은_반품비만_뺀다` — 예시 C: `refundAmount=27000`
  - `미리보기_포인트와_쿠폰이_있는_항목` — 예시 D: `paidAmount=38000, pointsRefund=3000, productCouponReturn != null, refundAmount=38000`
  - `미리보기와_실제_계산은_같은_결과다` — 위 다섯 상태 각각에서 `preview(order, itemId, reason, fees)`의 결과가, 같은 주문을 요청 → 승인한 뒤 `plan(order, returnId, fees)`의 결과와 `==`
  - `미리보기는_다른_항목이_환불_진행_중이어도_계산한다` — 다른 항목이 `CANCEL_REQUESTED`일 때 `preview`는 값을 돌려주고 `plan`은 기존대로 `RETURN_INVALID_STATE_TRANSITION`
  - `현금_결제_몫이_반품비보다_작으면_미리보기_환불은_0원이다` — `paidAmount=2000` 변심: `returnFee=2000, refundAmount=0`
- [ ] **Step 3: 실패 확인 → 계산 본체를 private 함수로 추출하고 `preview`를 추가한다.** `plan`의 상태 검사 두 블록(승인된 반품인지, 다른 항목 환불 진행 중인지)은 `plan`에 그대로 남기고, `when (reason)` 분기와 그 뒤 `check` 네 개가 본체다. `plan`이 내는 값은 바뀌지 않는다.
- [ ] **Step 4: 자격 판정 추출.** `Order.requestReturn`의 검사(81~86행과 기존 반품 행 판정)를 `checkReturnRequestable`로 옮기고 `requestReturn`이 그것을 부른다. 테스트(`OrderTest` 또는 해당 도메인 테스트에 추가): 열린 반품 → `RETURN_ALREADY_REQUESTED`; 판매자 주문이 `DELIVERED` 아님 → `RETURN_NOT_ELIGIBLE`; 기한 + 1초 → `RETURN_WINDOW_EXPIRED`, 기한 정각은 통과; 거절된 반품 → `RETURN_ALREADY_REQUESTED`; 관리자 중단된 반품 → `RETURN_ALREADY_REQUESTED`; 철회된 반품 → 통과; **검사 순서**(열린 반품이면서 기한도 지난 항목은 `RETURN_ALREADY_REQUESTED`); 함수 호출 뒤 주문 상태가 그대로임.
- [ ] **Step 5: `./gradlew test --tests '*ReturnRefundCalculatorTest' --tests '*OrderReturnServiceTest' --tests '*ReturnConsistencyApiTest' --tests '*ReturnApiTest' --tests '*OrderTest'` 통과(기존 단언 무수정) → Commit** `refactor(backend): 반품 환불 계산 본체와 요청 자격 판정 추출`
- [ ] **Step 6: 실패하는 테스트 — `ReturnPreviewApiTest`**(실제 DB, `ReturnApiTest`·`CommerceTestSupport`의 주문·발송·배송 완료 헬퍼 재사용)
  - `변심_미리보기는_14000원이고_이어서_요청_승인_수거_확인한_실제_환불과_같다` — 40,000 + 20,000 한 판매자 주문(무료배송), 20,000 항목 미리보기 `refundAmount=14000` → 요청 → 판매자 승인 → 수거 확인 응답의 `refundAmount`가 14000
  - `불량_미리보기는_33000원이다` — 30,000 + 배송비 3,000(구매자 부담)
  - `미리보기는_주문과_반품을_바꾸지_않는다` — 호출 전후 `GET /orders/{id}` 본문 동일, `GET /returns/me` 빈 목록
  - **일치(Review Focus 1)** `미리보기의_오류는_요청의_오류와_같다` — 각 상태에서 미리보기와 `POST …/returns`가 같은 HTTP 상태·코드: 배송 중 판매자 주문(`RETURN_NOT_ELIGIBLE`), 구매 확정된 판매자 주문(`RETURN_NOT_ELIGIBLE`), 이미 요청한 항목(`RETURN_ALREADY_REQUESTED`), 거절된 항목(`RETURN_ALREADY_REQUESTED`), 기한 지난 항목(`RETURN_WINDOW_EXPIRED` — 기존 테스트가 시각을 다루는 방식 그대로), 남의 주문(404), 없는 항목(404), 잘못된 사유(요청 API와 같은 400)
  - `철회한_항목은_미리보기가_다시_된다`
- [ ] **Step 7: 구현 → `./gradlew test --tests '*ReturnPreview*' --tests '*Return*' --tests '*OpenApiContractTest' --tests '*ArchitectureTest'` 통과 → `cd frontend && pnpm gen:api && pnpm check:api && pnpm typecheck` → Commit** `feat(backend): 반품 환불 미리보기 API 추가`

---

### Task 3: 반품 응답의 표시용 필드 (백엔드)

스펙 §5-2.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`(생성)
- Modify: `order/application/{OrderReturnService.kt(returnResult·toResult),ReturnQueryService.kt}`, 반품 결과 DTO, `order/presentation/`의 응답 DTO(컨트롤러가 결과 타입을 그대로 돌려주면 변경 없음)
- Test: 수정 `order/presentation/{ReturnApiTest,SellerReturnApiTest,AdminReturnApiTest}.kt`, `order/application/OrderReturnServiceTest.kt` 또는 조회 서비스 테스트(Fake)

**Interfaces:**
- Produces (OpenAPI, `ReturnRequestResponse`에 추가): `thumbnailUrl: string|null`(required + nullable), `optionDisplayName: string`(required), `orderNo: string`(required), `sellerName: string`(required), `paidAmount: int64`(required — 그 항목의 현금 결제 몫), `autoApproveAt: LocalDateTime|null`(`REQUESTED`일 때만 `requestedAt + kairos.order.return-auto-approve-after`). nullable 표기는 이 파일에서 `| null`이 실제로 생성되는 방식(기존 `thumbnailUrl` 필드와 같은 선언)을 따르고, 생성된 `schema.d.ts`에서 `| null`을 확인한다.
- Produces (Kotlin): `ReturnRequestResult`에 같은 6개 필드. 이미지: `ProductReader.findThumbnailUrls(productIds)`(F2에서 추가된 것)로 **목록 한 페이지당 한 번**.

- [ ] **Step 1: OpenAPI에 필드를 추가한다.**
- [ ] **Step 2: 실패하는 테스트**
  - `반품_응답에_상품_이미지_옵션_주문번호_판매자_이름_결제_금액이_있다` — 구매자 `GET /returns/me`·`GET /returns/{id}`, 판매자 목록·상세, 관리자 목록·상세, 그리고 요청 응답(`POST …/returns`) 모두에서: `thumbnailUrl`이 상품 첫 이미지 URL, `orderNo`가 주문의 것, `sellerName`이 판매자 주문 스냅샷의 이름, `paidAmount`가 항목의 `paidAmount`
  - `자동_승인_예정_시각은_요청_상태에서만_있고_요청_시각에_3일을_더한_값이다` — `REQUESTED`: `autoApproveAt == requestedAt + 3일`; 승인 뒤: null
  - `삭제된_상품의_반품_이미지는_null이다`(Fake `ProductReader`가 그 상품을 빼는 서비스 테스트)
  - `반품_목록_한_페이지의_이미지는_한_번에_조회한다`(Fake 호출 횟수 1, 반품 3건)
- [ ] **Step 3: 구현 → `./gradlew test --tests '*Return*' --tests '*OpenApiContractTest' --tests '*ArchitectureTest'` 통과 → `pnpm gen:api && pnpm check:api && pnpm typecheck` → Commit** `feat(backend): 반품 응답에 표시용 필드 추가`

---

### Task 4: 정산 시연 데이터 (백엔드, demo 프로필 전용)

스펙 §5-3.

**Files:**
- Modify: `common/infrastructure/ClockConfig.kt`(시스템 시계 빈을 다른 `Clock` 빈이 없을 때만 등록), 필요하면 `common/infrastructure/JpaAuditingConfig.kt`
- Create: `demo/infrastructure/` 아래 demo 전용 `Clock`(스레드 로컬 오프셋), `demo/application/DemoSettlementSeeder.kt`
- Modify: `demo/application/{DemoSeeder,DemoCatalog,DemoAccounts}.kt`, `demo/infrastructure/DemoDataInitializer.kt`·`demo/presentation/DemoController.kt`(기동·`POST /demo/reseed`에서 시더 호출)
- Test: 신규 `demo/` 통합 테스트(기존 demo 테스트 위치·패턴을 따른다)

**Interfaces:**
- Produces:
  - demo 프로필 전용 `Clock` 빈: `fun <T> withShift(offset: Duration, block: () -> T): T`를 가진 클래스 — 블록을 실행하는 **그 스레드에서만** `instant()`가 옮겨지고, 블록이 끝나면(예외 포함) 원래대로 돌아간다. 다른 스레드는 항상 실제 시각.
  - `DemoSettlementSeeder.seedLastWeek(): SeedResult`(만든 판매자 주문 수, 이미 있으면 0) — 지난주(가장 최근에 끝난 월~일 기간, KST) 안의 시각으로 주문 3건을 **애플리케이션 서비스로만** 만든다.
  - 시연 구매자 계정 `settlement-buyer@kairos.demo`(`DemoAccounts`에 추가, 비밀번호는 기존 데모 계정과 같은 설정값), 시연 상품 5개(50,000 / 30,000 / 20,000 / 15,000 / 30,000원, 데모 판매자 `seller@kairos.demo` 소유, 기존 "판매 중 20개" 보충 계산에 영향이 없게 별도 브랜드 또는 계산에서 제외), 판매자 상품 쿠폰 정책(정액 5,000원, 50,000원 상품 대상).

- [ ] **Step 1: 기존 구조를 읽는다** — `DemoSeeder`(멱등 판정, 상품 보충 계산), `DemoProfileGuard`, `ClockConfig`, 주문 생성부터 구매 확정·반품까지의 서비스 진입점(`OrderService.place`, 결제 승인 `OrderPaymentService`, `OrderShippingService`의 발송·배송 완료·확정, `OrderReturnService`의 요청·승인·수거 확인, 쿠폰 발급 `CouponService.claim`·정책 생성 `CouponPolicyService`), `SettlementGenerationService.generate`와 `kairos.settlement.start-date`.
- [ ] **Step 2: 실패하는 테스트 — demo 시계**(단위): `블록_안에서만_시각이_옮겨진다`(블록 안 `LocalDateTime.now(clock)`이 오프셋만큼 과거, 블록 뒤 실제 시각), `다른_스레드는_실제_시각을_본다`(블록 실행 중 다른 스레드에서 읽은 시각이 옮겨지지 않음), `블록이_예외로_끝나도_오프셋이_풀린다`.
- [ ] **Step 3: 구현.** `ClockConfig`는 `@ConditionalOnMissingBean(Clock::class)`(또는 같은 효과)로 바꾸고, demo 전용 `Clock`은 demo 프로필 조건으로 등록한다. demo가 아닌 프로필에서 컨텍스트의 `Clock`이 시스템 시계임을 확인하는 테스트를 둔다(`demo가_아니면_시스템_시계_하나다`).
- [ ] **Step 4: 실패하는 테스트 — 시더 통합 테스트**(demo 프로필, 실제 DB, `kairos.settlement.start-date`를 지난주 월요일로 준 테스트 설정)
  - `시더_실행_뒤_지난주_정산을_생성하면_데모_판매자의_라인이_70500_13500_마이너스3000이고_합계는_81000이다` — `seedLastWeek()` → `SettlementGenerationService.generate(지난주 월요일)` → 그 판매자 정산: 라인 금액 집합 `{70500, 13500, -3000}`, `salesAmount=95000`, `sellerCouponDiscount=5000`, `commissionAmount=9000`, `shippingFeeOwed=6000`, `returnFeeBorne=6000`, `totalAmount=81000`, `payableAmount=81000`
  - `같은_주에_시더를_두_번_실행해도_주문_수가_같다`
  - `시연_주문의_확정_시각은_지난주_기간_안이다` — 세 판매자 주문의 `confirmedAt` ∈ [지난주 월 00:00, 이번 주 월 00:00)
  - `시더가_만든_주문은_일반_주문과_같은_불변식을_만족한다` — 각 주문에서 (결제 금액 − 누적 환불액)이 C4 §5의 확장 불변식과 일치(기존 `ReturnConsistencyApiTest`가 쓰는 검증 헬퍼가 있으면 재사용)
  - `demo가_아닌_프로필에는_시더_빈이_없다`
  - `시연_상품을_더해도_기존_데모_상품_보충_수가_변하지_않는다` — 시더 실행 전후 기존 `DemoSeeder`가 만드는 판매 중 상품 수(20)가 같음
- [ ] **Step 5: 시더 구현.** 지난주 화요일 10:00(KST)을 기준 시각으로 삼아 `withShift` 안에서: (1) 시연 상품·쿠폰 정책 준비(없을 때만), 구매자가 쿠폰 받기 (2) 주문 A: 50,000 + 30,000 한 판매자 주문, 50,000 항목에 판매자 쿠폰 5,000 → 결제 승인(Mock PG) → 발송 → 배송 완료 → 구매 확정 (3) 주문 B: 20,000 + 15,000(합 35,000, 배송비 3,000 구매자 부담) → … 배송 완료 → 20,000 항목 불량 반품 요청 → 승인 → 수거 확인 → 남은 항목 구매 확정 (4) 주문 C: 30,000(배송비 3,000 구매자 부담) → … 배송 완료 → 불량 반품 요청 → 승인 → 수거 확인(판매자 주문이 전부 반품으로 자동 확정). 단계 사이에 시각을 조금씩 앞으로 옮겨(예: 한 시간씩) 모두 지난주 안에 들어가게 한다. 금액 값을 직접 저장하는 코드는 없어야 한다. 멱등: 지난주 기간에 시연 구매자의 주문이 이미 있으면 건너뛴다.
- [ ] **Step 6: 주문 일시.** `created_at` 감사 시각이 `Clock` 빈을 쓰게 바꿀 수 있는지 확인한다(`JpaAuditingConfig`의 `dateTimeProviderRef`). 바꿔도 `./gradlew test`의 기존 테스트가 그대로 통과하면 바꾸고, 아니면 바꾸지 않고 보고서에 "주문 일시는 실제 시각"이라고 적는다.
- [ ] **Step 7: `./gradlew test --tests '*Demo*' --tests '*Settlement*' --tests '*ArchitectureTest'` 통과 → Commit** `feat(backend): demo 프로필 전용 정산 시연 데이터 추가`

---

### Task 5: 백엔드 보강 마무리 점검

- [ ] **Step 1: `./gradlew test` 전체 통과**(실패·건너뜀 0).
- [ ] **Step 2: `./gradlew pitest`** — 임계값(mutation 79, line 99) 통과. 통과하지 못하면 `ReturnRefundCalculator`의 새 진입점에서 살아남은 변이를 리포트에서 찾아 Task 2의 테스트를 보강한다(임계값을 낮추지 않는다). 클래스별 결과(`ReturnRefundCalculator` 생성/kill)를 기록한다.
- [ ] **Step 3: `git diff dev -- src/main/kotlin/com/dogyun/kairos/order/domain/ src/main/kotlin/com/dogyun/kairos/settlement/domain/`를 읽어 돈 계산 클래스의 변경이 Task 2의 추출뿐인지 확인한다.** `git diff dev --stat -- src/test/kotlin`에서 기존 테스트 파일의 삭제 줄이 없는지 확인한다.
- [ ] **Step 4: Gradle 데몬 정리.** 고친 것이 있으면 Commit `test(backend): F4 백엔드 보강 점검`.

---

### Task 6: 프론트 공통 기반

스펙 §6, §10.

**Files:**
- Modify: `lib/{money,datetime,query-keys}.ts`(+테스트), `api/errors.ts`(+테스트), `components/common/StatusChip.tsx`(+테스트), `components/layout/console-titles.ts`(+테스트), `features/mypage/menu.ts`(+테스트), 판매자 사이드 메뉴(`features/seller/gate/SellerGate.tsx`)·관리자 사이드 메뉴(`app/admin/layout.tsx`), `features/checkout/api.ts`(+테스트)
- Create: `features/returns/{return-text.ts,return-actions.ts,return-steps.ts}`(+테스트), `features/settlements/settlement-text.ts`(+테스트)

**Interfaces:**
- Produces:
  - `money.ts`: `signedAmount(kind: "ADD" | "SUBTRACT", amount: number): number`(양의 안전 정수만, 아니면 throw — `SUBTRACT`면 음수), `formatSignedKrw(n: number): string`(`+3,000원` / `−3,000원`(U+2212) / `0원`)
  - `datetime.ts`: `lastFinishedWeekMonday(nowMs: number): string`(KST 기준 가장 최근에 끝난 월~일 기간의 월요일 `"YYYY-MM-DD"`), `isMonday(date: string): boolean`(`"YYYY-MM-DD"`), `isWeekFinished(monday: string, nowMs: number): boolean`(그 월요일 + 7일 00:00 KST ≤ now). 브라우저 시간대와 무관해야 한다.
  - `features/returns/return-text.ts`: `returnReasonLabel(r)`(`CHANGE_OF_MIND` "단순 변심" / `DEFECTIVE` "상품 불량" / `WRONG_DELIVERY` "오배송"), `returnStatusLabel(s)`(`REQUESTED` "반품 요청됨" / `APPROVED` "반품 승인 · 수거 대기" / `REFUNDING` "환불 확인 중" / `COMPLETED` "반품 완료" / `REJECTED` "반품 거절" / `WITHDRAWN` "철회됨" / `CANCELED_BY_ADMIN` "반품 중단됨"), `feeBearerNotice(b)`(`BUYER` "반품 배송비는 구매자 부담입니다" / `SELLER` "판매자 부담이라 차감이 없습니다"), `decidedByLabel(d)`(`SELLER` "판매자" / `SYSTEM` "자동 승인" / `ADMIN` "관리자")
  - `features/returns/return-actions.ts`: `returnEntry(input: { sellerOrderStatus: string; itemStatus: string; returnableUntil: string | null | undefined; returnStatus: string | null | undefined }, nowMs: number): { kind: "request"; until: string } | { kind: "expired" } | { kind: "blocked"; text: string } | { kind: "none" }` — 판매자 주문 `DELIVERED` + 항목 `ORDERED` + 반품 없음 또는 `WITHDRAWN`: 기한 안이면 `request`, 지났으면 `expired`; `REJECTED` → `blocked`("반품이 거절된 상품입니다"), `CANCELED_BY_ADMIN` → `blocked`("반품이 중단된 상품입니다"); 그 밖은 `none`. `canWithdraw(returnStatus): boolean`(`REQUESTED`만). `refundRows(r: { paidAmount; returnFee; outboundFeeRecovered; shippingFeeRefund; refundAmount; pointsRefund })`: 0이 아닌 줄만 순서대로(`paidAmount`와 `refundAmount`는 항상) — 금액은 그대로 옮기고 부호만 정한다.
  - `features/returns/return-steps.ts`: `returnSteps(r: { status; requestedAt; decidedAt?; receivedAt?; completedAt? }): { key: "requested" | "approved" | "received" | "completed"; state: "done" | "current" | "todo" | "stopped"; at?: string }[]`와 `returnEndNote(status): string | null`(`REJECTED` "판매자가 반품을 거절했습니다" / `WITHDRAWN` "반품 요청을 철회했습니다" / `CANCELED_BY_ADMIN` "관리자가 반품을 중단했습니다")
  - `features/settlements/settlement-text.ts`: `settlementStatusLabel(s)`(`GENERATED` "정산 생성" / `CONFIRMED` "확정" / `PAID` "지급 완료"), `adjustmentTypeLabel(t)`(`MANUAL` "수동 조정" / `CARRY_OVER` "이월"), `settlementRows(d)`: 계산 내역 8줄을 고정 순서로(상품 매출 +, 판매자 쿠폰 할인 −, 수수료 −, 배송비 +, 판매자 부담 반품비 −, 조정(부호 그대로), 합계, 지급액 — 값은 서버 필드 그대로, 0도 포함), `isCarriedOver(d: { totalAmount: number }): boolean`(합계 < 0)
  - `query-keys.ts`: `returns: { root, mine(params?), detail(id), preview(orderId, itemId, reason) }`, `sellerReturns: { lists, list(params?), detail(id) }`, `adminReturns: { lists, list(params?), detail(id) }`, `sellerSettlements: { lists, list(params?), detail(id) }`, `adminSettlements: { lists, list(params?), detail(id) }`, `adminAdjustments: (sellerId: number)`
  - `MY_MENU`에 `{ href: "/my/returns", label: "반품 내역" }`(주문 내역 다음), `activeMenuHref("/my/returns/3") === "/my/returns"`
  - `console-titles.ts`: `/seller/returns*` "반품 관리", `/seller/settlements*` "정산", `/admin/returns*` "반품", `/admin/settlements/adjustments` "정산 조정", `/admin/settlements*` "정산"
  - `StatusChip`: kind `return`(7 상태, 문구는 `returnStatusLabel`), kind `settlement`(3 상태), kind `adjustment`(`PENDING` "다음 정산에 반영 예정" / `APPLIED` "정산에 반영됨")
  - `errors.ts`: `RETURN_NOT_FOUND`·`SETTLEMENT_NOT_FOUND` → `page`, `RETURN_INVALID`·`SETTLEMENT_ADJUSTMENT_INVALID`·`SETTLEMENT_PERIOD_INVALID` → `form`

- [ ] **Step 1: 실패하는 테스트**
  - `money.test.ts`: `signedAmount("SUBTRACT", 10000) === -10000`, `("ADD", 10000) === 10000`, `0`·`-5`·`1.5`·`NaN`·안전 정수 초과는 throw; `formatSignedKrw(3000) === "+3,000원"`, `(-3000) === "−3,000원"`, `(0) === "0원"`
  - `datetime.test.ts`(KST 고정 — 실행 환경 시간대를 바꿔도 같아야 한다): 2026-11-12(목) 15:00 KST → `"2026-11-02"`; 2026-11-15(일) 23:59:59 KST → `"2026-11-02"`; 2026-11-16(월) 00:00:00 KST → `"2026-11-09"`; `isMonday("2026-11-09") === true`, `("2026-11-10") === false`, 형식이 아니면 false; `isWeekFinished("2026-11-09", <2026-11-15 23:59:59 KST>) === false`, `(…, <2026-11-16 00:00:00 KST>) === true`
  - `return-actions.test.ts`(Review Focus 3): 기한 1초 전 `request`, 기한 정각 `request`, 1초 뒤 `expired`; 판매자 주문 `SHIPPING`·`CONFIRMED`·`PAID` → `none`; 항목 `RETURN_REQUESTED`(반품 `REQUESTED`) → `none`; `REJECTED` → `blocked`; `CANCELED_BY_ADMIN` → `blocked`; `WITHDRAWN` → `request`; `returnableUntil` null → `none`; `canWithdraw` 7 상태; `refundRows`: 변심(20000, 3000, 3000, 0, 14000, 0) → 결제 금액·반품비·발송 배송비 회수·환불 금액 4줄, 불량(30000, 0, 0, 3000, 33000, 0) → 3줄, 포인트 반환 포함 시 줄 추가
  - `return-steps.test.ts`: `REQUESTED`(요청 done·승인 current), `APPROVED`, `REFUNDING`(수거 확인 done·환불 완료 current), `COMPLETED`(전부 done, 각 `at`), `REJECTED`·`WITHDRAWN`·`CANCELED_BY_ADMIN`(멈춘 칸 `stopped`, `returnEndNote` 문구)
  - `settlement-text.test.ts`(Review Focus 4): 8줄 순서와 0 포함; 음수 합계(`totalAmount=-3000, payableAmount=0`)에서 값 그대로이고 `isCarriedOver` true; 라벨 전부
  - `return-text.test.ts`, `menu.test.ts`, `console-titles.test.ts`, `errors.test.ts`, `StatusChip.test.tsx`: 위 Interfaces의 값 전부
- [ ] **Step 2: 실패 확인 → 구현 → 통과.**
- [ ] **Step 3: 메뉴.** 판매자 사이드 메뉴: "정산"의 `disabled`·"준비 중"을 없애고 `/seller/settlements`로, "반품 관리"(`/seller/returns`)를 주문 관리 다음에 추가. 관리자 사이드 메뉴: "반품"(`/admin/returns`), "정산"(`/admin/settlements`) 추가.
- [ ] **Step 4: F3 후속 — 견적 재요청 제거.** `features/checkout/api.ts`의 `useCreateOrder`가 주문 성공 뒤 `checkout.quotes`를 무효화하며 재요청을 일으킨다(아직 켜져 있는 견적 조회가 409를 받는다). 재요청 없이 캐시만 버리도록 바꾼다(`refetchType: "none"` 무효화 또는 `removeQueries` — 장바구니·주문·쿠폰·포인트 무효화는 그대로). `CheckoutPage.test.tsx`에 `주문이_만들어진_뒤에는_견적을_다시_요청하지_않는다`(주문 성공 뒤 견적 호출 수가 늘지 않음) 추가. 기존 0원 주문 테스트의 "견적 캐시 무효화" 단언은 새 동작에 맞게 고친다(이 한 줄은 수정 허용 — 보고서에 적는다).
- [ ] **Step 5: `pnpm lint && pnpm typecheck && pnpm test && pnpm build` → Commit** `feat(frontend): F4 공통 기반(반품·정산 문구, 날짜 함수, 메뉴) 추가`

---

### Task 7: 구매자 — 반품 요청, 주문 상세의 반품 상태, 철회

스펙 §7-1. 시안 `design/user-return.html`.

**Files:**
- Create: `features/returns/{api.ts,schemas.ts}`(+테스트), `features/returns/components/{ReturnRequestDialog,ReturnPreviewBox,RefundBreakdown,ReturnStatusLine}.tsx`(+테스트)
- Modify: `features/orders/components/{OrderDetailPage,SellerOrderCard,OrderItemRow,ConfirmPurchaseButton,PaymentSummaryCard}.tsx`(+테스트), `features/orders/{actions.ts,polling.ts}`(+테스트)

**Interfaces:**
- Consumes: Task 2의 `ReturnPreview`, Task 3의 응답 필드, Task 6의 `returnEntry`·`canWithdraw`·`refundRows`·문구·쿼리 키; `useIdempotentSubmit`; F2의 `SellerOrderCard.renderItemNote`·`OrderItemRow.note` 슬롯(F3에서 후기 영역이 쓰는 것과 같은 자리 — 후기 영역과 함께 보이게 한다)
- Produces:
  - `useReturnPreview(orderId, itemId, reason | null)` — 사유가 있을 때만 조회, `retry: false`, `staleTime: 0`
  - `requestReturn(orderId, itemId, body, key)`, `withdrawReturn(returnId)`
  - `returnRequestSchema`(zod): `reason` 필수(미선택 메시지 "반품 사유를 선택해 주세요"), `detail` 0~500자(빈 문자열은 보내지 않는다)
  - `previewBlocksRequest(error: unknown): boolean` — `RETURN_NOT_ELIGIBLE`·`RETURN_WINDOW_EXPIRED`·`RETURN_ALREADY_REQUESTED`만 true(그 밖의 실패는 요청을 막지 않는다)
  - `RefundBreakdown` props: `rows: ReturnType<typeof refundRows>`, `title: "환불 예정 금액" | "환불 금액"` — 판매자·관리자·반품 상세가 재사용
  - `features/orders/actions.ts`: `confirmBlockedReason(sellerOrder: { status; hasOpenReturn }): string | null`(열린 반품이면 "반품이 진행 중인 상품이 있어 확정할 수 없습니다"), `sellerOrderChip(sellerOrder)`: `allItemsReturned`면 "전체 반품"
  - `polling.ts`: 재조회 조건에 항목 `RETURN_REFUNDING` 추가

- [ ] **Step 1: 실패하는 테스트** — `schemas.test.ts`(사유 없음, detail 500/501, 빈 detail은 요청 본문에 없음), `previewBlocksRequest`(세 코드 true, 네트워크 오류·500·모르는 코드 false), `actions.test.ts`(`confirmBlockedReason`, `sellerOrderChip`), `polling.test.ts`(`RETURN_REFUNDING` 항목이 있으면 재조회).
- [ ] **Step 2: 구현 → 통과.**
- [ ] **Step 3: `ReturnRequestDialog`.** 상품 요약, 사유 라디오 3개(`role="radiogroup"`), 상세 내용(글자 수), `ReturnPreviewBox`: 사유 전 "사유를 선택하면 환불 예정 금액을 보여 드립니다" / 로딩 / 성공 — `RefundBreakdown(refundRows(preview), "환불 예정 금액")` + `feeBearerNotice` + (`outboundFeeRecovered > 0`이면) "무료배송으로 받은 상품이라 발송 배송비가 함께 차감됩니다" + (`productCouponReturnable`이면) 쿠폰 안내 + **항상** "실제 환불 금액은 판매자가 상품을 받은 뒤 확정됩니다. 같은 주문의 다른 상품을 취소·반품하면 달라질 수 있습니다" / 실패(막지 않음) "환불 예정 금액을 불러오지 못했습니다" / 409(막음) 서버 메시지. 제출 `useIdempotentSubmit` → 성공 토스트 "반품을 요청했습니다. 판매자가 3일 안에 확인합니다", `orders.detail(orderId)`·`returns.root` 무효화. 409 → 닫고 서버 메시지 + `orders.detail` 무효화. 400 → 폼 상단.
  - 컴포넌트 테스트: `사유를_바꾸면_미리보기를_다시_요청한다`, `미리보기가_409면_요청_버튼이_막힌다`, `미리보기가_네트워크_오류여도_요청할_수_있다`, `요청은_멱등_키와_함께_한_번만_나간다`(연타), `요청이_409면_창을_닫고_주문_상세를_다시_읽는다`
- [ ] **Step 4: 주문 상세.** 항목 줄에 `returnEntry` 결과로: `request` → "반품 요청" 버튼 + `"{formatDateTimeMinute(until)}까지 반품 가능"`; `expired` → "반품 가능 기간이 지났습니다"; `blocked` → 문구 + 반품 상세 링크. 항목에 `returnRequest`가 있고 `WITHDRAWN`이 아니면 `ReturnStatusLine`(상태 칩, `COMPLETED`면 환불 금액, 반품 상세 링크 `/my/returns/{returnId}`, `canWithdraw`면 "철회" — `ConfirmDialog` 뒤 `withdrawReturn`, 처리 중 재호출 막기, 409 → 서버 메시지 + 다시 읽기). 구매자 주문 상세에서만 보인다(판매자·관리자 주문 화면은 그대로).
- [ ] **Step 5: 구매 확정과 요약.** `ConfirmPurchaseButton`: 확인 창에 "확정하면 이 판매자의 상품은 반품할 수 없습니다" 추가; `confirmBlockedReason`이 있으면 버튼 비활성 + 이유; 409 `SELLER_ORDER_HAS_OPEN_RETURN` → "반품이 진행 중인 상품이 있어 확정할 수 없습니다. 반품을 철회하거나 완료된 뒤 다시 시도해 주세요". `SellerOrderCard` 칩은 `sellerOrderChip`. `PaymentSummaryCard`: 반품이 완료된 항목이 하나라도 있으면 환불 금액 줄 아래 "반품 배송비가 차감된 금액입니다"(반품 완료 항목 중 `returnFee > 0`인 것이 있을 때만 — 비교이지 합산이 아니다). 테스트에 문구 노출 조건 추가.
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 반품 요청과 주문 상세의 반품 상태 추가`

---

### Task 8: 구매자 — 반품 내역과 상세

스펙 §7-2, §7-3. 시안 `design/user-return.html`.

**Files:**
- Create: `features/returns/components/{MyReturnsPage,MyReturnDetailPage,ReturnCard,ReturnSteps}.tsx`, `app/(store)/my/(menu)/returns/page.tsx`, `app/(store)/my/(menu)/returns/[returnId]/page.tsx`
- Modify: `features/returns/api.ts`

**Interfaces:**
- Consumes: Task 6의 `returnSteps`·`returnEndNote`·문구, Task 7의 `RefundBreakdown`·`withdrawReturn`
- Produces: `useMyReturns(page)`, `useMyReturn(returnId)`(상태가 `REFUNDING`이면 3초 간격 재조회, 최대 2분 — `features/orders/polling.ts`의 규칙 함수를 재사용), `ReturnSteps`(진행 단계 표시 — 판매자·관리자 상세가 재사용), `refundSummaryText(r)`: `COMPLETED` → 환불 금액, 그 밖 → "수거 확인 뒤 확정"

- [ ] **Step 1: 테스트** — `refundSummaryText`(7 상태), `useMyReturn`의 재조회 조건(순수 함수로 분리해 테스트).
- [ ] **Step 2: 목록.** `ReturnCard`: 이미지(`thumbnailUrl` 없으면 F2 자리 표시), 이름·`joinDot(옵션, 수량)`, 판매자, `returnReasonLabel`, `StatusChip(kind="return")`, 요청일, `refundSummaryText`. `?page=`, 빈 상태 "반품 내역이 없습니다" + 주문 내역 링크, 오류 상태.
- [ ] **Step 3: 상세.** `parseId`로 경로 ID, 404 → 없음 페이지. `ReturnSteps` + `returnEndNote`, 승인 주체(`decidedByLabel`), 사유·상세 내용, 거절 사유, 주문번호 → `/my/orders/{orderId}`. 환불 내역: `COMPLETED`·`REFUNDING`이면 `RefundBreakdown(refundRows(r), "환불 금액")`, 그 밖은 "환불 금액은 판매자가 상품을 받은 뒤 확정됩니다". `REQUESTED`면 철회 버튼(Task 7과 같은 동작, 성공 뒤 상세·목록·주문 상세 무효화).
- [ ] **Step 4: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 반품 내역과 상세 화면 추가`

---

### Task 9: 판매자 — 반품 관리

스펙 §8-1. 시안 `design/seller-return.html`.

**Files:**
- Create: `features/seller/returns/{api.ts,receive-outcome.ts,tabs.ts}`(+테스트), `features/seller/returns/components/*`, `app/seller/returns/page.tsx`, `app/seller/returns/[returnId]/page.tsx`

**Interfaces:**
- Consumes: Task 6·7·8의 반품 문구·`RefundBreakdown`·`ReturnSteps`
- Produces:
  - `tabs.ts`: `RETURN_TABS`(전체 / 요청 `REQUESTED` / 승인 · 수거 대기 `APPROVED` / 환불 확인 중 `REFUNDING` / 완료 `COMPLETED` / 거절 `REJECTED`), `parseReturnTab(sp)`(모르는 값 → 전체)
  - `receive-outcome.ts`: `receiveOutcome(result: { ok: true; status: string; refundAmount: number } | { ok: false; error: unknown }): { kind: "completed"; refundAmount: number } | { kind: "refunding" } | { kind: "rejected" } | { kind: "unknown" } | { kind: "error"; message: string }` — `COMPLETED` → completed, `REFUNDING` → refunding, `PAYMENT_CANCEL_FAILED` → rejected, 응답 없음(`isAnswered(e) === false`) → unknown, 그 밖 → error(서버 메시지)
  - `ReturnTable` props: `rows`, `detailHref(returnId)`, `showSeller?: boolean` · `ReturnDetailView` props: `data: ReturnRequestResponse`, `orderHref?: string`, `actions: ReactNode` — 관리자 화면이 재사용
  - `ReceiveDialog` props: `reason`, `onConfirm(): Promise<void>`, `busy` — 관리자 화면이 재사용
  - `canProcessReturns(sellerStatus: string): boolean`(`APPROVED`만 true)

- [ ] **Step 1: 실패하는 테스트** — `tabs.test.ts`, `receive-outcome.test.ts`(Review Focus 2: 다섯 결과 전부, 네트워크 오류·5xx·`IDEMPOTENCY_IN_PROGRESS`는 unknown), `canProcessReturns`.
- [ ] **Step 2: 구현 → 통과.**
- [ ] **Step 3: 목록.** 상태 탭(`?status=`)·`?page=`. 표: 요청일, 주문번호, 상품(이미지·이름·옵션·수량), 사유, 부담 주체, 상태 칩, `REQUESTED`면 `"{formatMonthDay(autoApproveAt)} 자동 승인"`. 줄 → 상세. 빈·오류 상태.
- [ ] **Step 4: 상세와 처리.** `ReturnDetailView`: `ReturnSteps`, 사유·상세 내용, 주문번호(→ `/seller/orders/{sellerOrderId}`), 환불 내역(`COMPLETED`·`REFUNDING`), `sellerBorneFee > 0`이면 "판매자 부담 반품비 {금액} — 정산에서 차감됩니다". 버튼:
  - 승인(`REQUESTED`): `ConfirmDialog`(안내 "불량이 아니라고 판단되면 거절해 주세요. 관리자가 다시 확인합니다") → `PATCH …/approve`.
  - 거절(`REQUESTED`): `RejectDialog`(사유 필수 1~500자, 안내 "거절하면 구매자는 같은 상품을 다시 반품 요청할 수 없습니다") → `PATCH …/reject`.
  - 수거 확인(`APPROVED`): `ReceiveDialog` — "상품을 받으셨나요? 확인하면 구매자에게 환불이 실행되며 되돌릴 수 없습니다" + 사유별 재고 안내(변심: "재고가 자동으로 복구됩니다" / 불량·오배송: "재고는 자동으로 복구되지 않습니다. 재판매할 수 있으면 상품 관리에서 재고를 수정해 주세요"). `receiveOutcome`에 따라: completed → 토스트 `"환불을 완료했습니다. 환불 금액 {formatKrw}"`; refunding → "환불을 확인하고 있습니다" + 상세가 3초 간격 재조회(최대 2분); rejected → "환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요"; unknown → "결과를 확인하지 못했습니다. 반품 상태를 확인해 주세요"(자동 재전송 없음); 모든 경우 상세·목록 무효화.
  - 409 `RETURN_INVALID_STATE_TRANSITION` → 서버 메시지 + 다시 읽기.
  - `canProcessReturns`가 false면 세 버튼을 숨기고 "정지 상태에서는 반품을 처리할 수 없습니다. 관리자가 대신 처리합니다".
  - 컴포넌트 테스트(Review Focus 2): `수거_확인을_연타해도_요청은_한_번만_나간다`, `응답이_REFUNDING이면_환불_확인_중으로_보인다`, `네트워크_오류면_결과를_확인하지_못했다고_알리고_다시_보내지_않는다`.
- [ ] **Step 5: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 판매자 반품 관리 화면 추가`

---

### Task 10: 판매자 — 정산

스펙 §8-2. 시안 `design/seller-settlement.html`.

**Files:**
- Create: `features/settlements/components/{SettlementBreakdown,SettlementLinesTable,SettlementAdjustmentsTable}.tsx`(+테스트), `features/seller/settlements/{api.ts,components/*}`, `app/seller/settlements/page.tsx`, `app/seller/settlements/[settlementId]/page.tsx`

**Interfaces:**
- Consumes: Task 6의 `settlementRows`·`isCarriedOver`·라벨·`formatSignedKrw`·`formatBp`
- Produces: `SettlementBreakdown` props `detail: SettlementDetail` · `SettlementLinesTable` props `lines`, `orderHref?(line): string | undefined` · `SettlementAdjustmentsTable` props `adjustments` — 관리자 화면이 재사용. `useSellerSettlements(page)`, `useSellerSettlement(id)`.

- [ ] **Step 1: 컴포넌트 테스트**(Review Focus 4) — `SettlementBreakdown`: 8줄이 고정 순서로 0 포함해 보인다; 값이 서버 필드 그대로(매출 95,000 / −5,000 / −9,000 / +6,000 / −6,000 / 0 / 81,000 / 81,000); 음수 합계(−3,000, 지급액 0)에서 "합계가 0원보다 적어 이번 지급액은 0원입니다. 부족한 금액은 다음 정산으로 넘어갑니다"가 보인다. `SettlementLinesTable`: 음수 라인 "−3,000원", 수수료율 `formatBp(1000)` "10%"와 800bp "8%"가 섞여도 줄마다 표시, 라인 0개면 "포함된 주문이 없습니다".
- [ ] **Step 2: 목록.** 기간(`{periodStart} ~ {periodEnd}`), 지급 예정일, `StatusChip(kind="settlement")`, 포함 주문 수, 합계, **지급액**(강조). `?page=`. 빈 상태 "아직 정산이 없습니다. 정산은 매주 월요일에 지난주 구매 확정 주문으로 만들어집니다".
- [ ] **Step 3: 상세.** `SettlementBreakdown` + `SettlementLinesTable`(주문번호 → `/seller/orders/{sellerOrderId}`) + `SettlementAdjustmentsTable`(종류, 금액 `formatSignedKrw`, 사유) + 안내 "플랫폼이 부담한 쿠폰·포인트·무료배송은 정산에서 차감되지 않습니다" + `PAID`면 지급일·메모. 404 → 없음 페이지.
- [ ] **Step 4: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 판매자 정산 화면 추가`

---

### Task 11: 관리자 — 반품

스펙 §9-1.

**Files:**
- Create: `features/admin/returns/{api.ts,params.ts}`(+테스트), `features/admin/returns/components/*`, `app/admin/returns/page.tsx`, `app/admin/returns/[returnId]/page.tsx`

**Interfaces:**
- Consumes: Task 9의 `ReturnTable`·`ReturnDetailView`·`ReceiveDialog`·`receiveOutcome`
- Produces: `parseAdminReturnParams(sp): { status?: ReturnStatus; sellerId?: number; memberId?: number; page: number }`(잘못된 값은 버린다), `adminReturnActions(status: string): ("approve" | "receive" | "cancel")[]`(`REQUESTED`·`REJECTED` → approve, `APPROVED` → receive·cancel, 그 밖 → 없음)

- [ ] **Step 1: 테스트** — `params.test.ts`(`sellerId=abc`·`0`·음수는 버림, 모르는 status는 버림), `adminReturnActions` 7 상태.
- [ ] **Step 2: 화면.** 목록: 필터(상태 선택, 판매자 ID, 회원 ID — URL 쿼리), `ReturnTable showSeller`. 상세: `ReturnDetailView` + 버튼 — 승인(`ConfirmDialog`; 409 `RETURN_NOT_ELIGIBLE` → "이미 구매 확정된 주문이라 승인할 수 없습니다. 정산 조정으로 처리해 주세요"), 대신 수거 확인(`ReceiveDialog`, Task 9와 같은 결과 처리), 중단(`ConfirmDialog` "구매자 요청으로 반품을 중단합니다. 상품은 주문 상태로 돌아가며, 구매자는 이 상품을 다시 반품 요청할 수 없습니다" → `PATCH …/cancel`). 주문번호 → `/admin/orders/{orderId}`.
- [ ] **Step 3: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 관리자 반품 화면 추가`

---

### Task 12: 관리자 — 정산과 조정

스펙 §9-2, §9-3. 시안 `design/admin-settlement.html`.

**Files:**
- Create: `features/admin/settlements/{api.ts,generate.ts,adjustment-schema.ts}`(+테스트), `features/admin/settlements/components/*`, `app/admin/settlements/page.tsx`, `app/admin/settlements/[settlementId]/page.tsx`, `app/admin/settlements/adjustments/page.tsx`

**Interfaces:**
- Consumes: Task 10의 정산 컴포넌트, Task 6의 `lastFinishedWeekMonday`·`isMonday`·`isWeekFinished`·`signedAmount`·`parseId`
- Produces:
  - `generate.ts`: `validatePeriodStart(date: string, nowMs: number): null | "NOT_MONDAY" | "NOT_FINISHED" | "INVALID"`, `generateResultText(r: GenerateSettlementResult): { tone: "success" | "info" | "warning"; lines: string[] }` — `alreadyGenerated` → info "이미 생성된 기간입니다"; `failedCount > 0` → warning "생성 {n} · 건너뜀 {n} · 실패 {n}" + "다시 생성하면 실패한 판매자만 만들어집니다"; 그 밖 → success "대상 판매자 {n}명 · 생성 {n} · 건너뜀 {n}"
  - `adjustment-schema.ts`: `adjustmentFormSchema`(zod) — `kind` `ADD`|`SUBTRACT`, `amount` 양의 정수 원(`parseWon`, 0·음수·소수 거절), `reason` trim 1~500, `refSellerOrderId` 선택(양의 정수) → `toAdjustmentRequest(values): CreateSettlementAdjustmentRequest`(`amount = signedAmount(kind, amount)`)
  - `adjustmentState(a: { type; settlementId? }): "PENDING" | "APPLIED"`, `canDeleteAdjustment(a): boolean`(`MANUAL`이고 미부착만)

- [ ] **Step 1: 실패하는 테스트**(Review Focus 5) — `generate.test.ts`: 화요일 → `NOT_MONDAY`; 이번 주 월요일 → `NOT_FINISHED`; 지난주 월요일 → null; 빈 값·형식 오류 → `INVALID`; 결과 문구 세 경우. `adjustment-schema.test.ts`: 차감 10,000 → 요청 `amount === -10000`; 가산 → `10000`; `0`·`-1`·`1.5`·`1e3` 거절; 사유 공백만 거절·501자 거절; 근거 주문 비우면 요청에 없음. `adjustmentState`·`canDeleteAdjustment`(MANUAL 미부착 true, MANUAL 부착 false, CARRY_OVER false).
- [ ] **Step 2: 구현 → 통과.**
- [ ] **Step 3: 목록과 생성.** 목록: 필터(판매자 ID, 상태 — URL 쿼리), 표(판매자 이름 + 상태가 `APPROVED`가 아니면 표시, 기간, 지급 예정일, 상태, 합계, 지급액). "정산 생성" → `GenerateDialog`: 날짜 입력(`type="date"`) 기본값 `lastFinishedWeekMonday(now)`, `validatePeriodStart` 오류 문구("월요일을 선택해 주세요" / "아직 끝나지 않은 주입니다" / "날짜를 선택해 주세요") — 오류면 제출 막기. `useIdempotentSubmit` → `POST /admin/settlements/generate`. 결과는 창 안에 `generateResultText`로 보인다(창을 닫지 않는다). 400 `SETTLEMENT_PERIOD_INVALID` → 서버 메시지 + "앞선 기간이 아직 생성되지 않았다면 그 기간부터 생성해 주세요". 성공 뒤 `adminSettlements.lists` 무효화.
  - 컴포넌트 테스트: `생성_버튼을_연타해도_요청은_한_번만_나간다`, `월요일이_아니면_요청하지_않는다`.
- [ ] **Step 4: 상세.** Task 10의 세 컴포넌트 + 판매자 정보 + 버튼: 확정(`GENERATED`, `ConfirmDialog` → `PATCH …/confirm`), 지급 완료(`CONFIRMED`, `PayDialog` — 메모 0~200자, 문구 `"지급액 {formatKrw(payableAmount)}을 지급 완료로 표시합니다(실제 이체는 따로 진행합니다)"`, 지급액 0원이면 "지급할 금액이 없는 정산입니다. 지급 완료로 닫습니다" → `PATCH …/pay`). 409 `SETTLEMENT_INVALID_STATE_TRANSITION` → 서버 메시지 + 다시 읽기. "이 판매자의 조정 보기" → `/admin/settlements/adjustments?sellerId=`. 라인의 주문번호 → `/admin/orders/{orderId}`.
- [ ] **Step 5: 조정.** 판매자 ID 입력 → `?sellerId=`. 목록: 종류, 금액(`formatSignedKrw`), 사유, 근거 주문, `StatusChip(kind="adjustment")`(반영됨이면 정산 상세 링크), 삭제 버튼(`canDeleteAdjustment`일 때만 — `ConfirmDialog` → `DELETE`; 409 `SETTLEMENT_ADJUSTMENT_APPLIED` → "이미 정산에 반영된 조정은 삭제할 수 없습니다" + 다시 읽기). 등록 양식: 구분 라디오, 금액, 사유, 근거 주문 ID, 안내(스펙 §9-3 문구) → `useIdempotentSubmit` → `POST`. 400 → 폼 상단. 성공 → `adminAdjustments(sellerId)` 무효화.
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 관리자 정산과 정산 조정 화면 추가`

---

### Task 13: E2E

스펙 §12.

**Files:**
- Create: `frontend/e2e/c4-return-change-of-mind.spec.ts`, `c4-return-defective.spec.ts`, `c4-settlement.spec.ts`
- Modify: `frontend/e2e/fixtures/api.ts`, `frontend/playwright.config.ts`(머리말 주석에 정산 시작일 환경 변수 설명)

**Interfaces:**
- Produces(픽스처): `deliverSellerOrder(sellerToken, sellerOrderId)`(발송 + 배송 완료 — 기존 헬퍼가 있으면 재사용), `requestReturnApi`, `approveReturnApi`, `receiveReturnApi`, 데모 판매자 로그인 정보(`seller@kairos.demo` + `KAIROS_DEMO_PASSWORD`).
- 실행 환경: 백엔드 `local,demo` + Mock PG, **`kairos.settlement.start-date`에 해당하는 환경 변수를 지난주 월요일로** 준다(변수 이름은 `application.yaml`에서 확인). 새 빈 임시 스키마(`kairos_e2e`)에서 **전체 스위트를 한 번에** 돌린다. `.env` 값을 출력하지 않는다. 끝나면 스키마·서버·Gradle을 정리한다.

- [ ] **Step 1: 픽스처를 추가한다.** 주문 생성 뒤 `POST /orders/quote`가 409를 내지 않는지 확인하는 단언을 `c3-coupon-checkout.spec.ts`의 주문 직후에 더한다(응답 관찰 — 주문 생성 응답 이후의 견적 응답 중 409가 0건).
- [ ] **Step 2: `c4-return-change-of-mind.spec.ts`**(금액은 `toHaveText` 정확 비교). 판매자 S의 상품 40,000원 + 20,000원을 한 주문으로(무료배송) → API로 발송·배송 완료 → 주문 상세의 20,000원 항목 "반품 요청" → 단순 변심 선택 → 결제 금액 `20,000원`, 반품비 `−3,000원`, 발송 배송비 회수 `−3,000원`, 환불 예정 금액 `14,000원`, "예상" 안내 문구 → 요청(버튼 연타) → 반품 내역에 1건 → 판매자: 반품 목록 "요청" 탭 → 상세 → 승인 → 수거 확인 창의 재고 복구 안내 → 확인 → "환불 금액 14,000원" → 구매자: 반품 상세 환불 금액 `14,000원` → 주문 상세 40,000원 항목 미리보기 환불 예정 `37,000원`(발송 배송비 회수 줄 없음) → 요청 → (API) 승인·수거 확인 → 주문 상세: 판매자 주문 칩 "전체 반품", 환불 금액 `51,000원`, "반품 배송비가 차감된 금액입니다".
- [ ] **Step 3: `c4-return-defective.spec.ts`.** 판매자 T의 30,000원 상품(배송비 3,000원 구매자 부담, 결제 33,000원) → 배송 완료 → 반품 요청 창에서 상품 불량 → 배송비 환불 `+3,000원`, 환불 예정 `33,000원`, "판매자 부담이라 차감이 없습니다" → 요청 → 주문 상세의 구매 확정 버튼이 비활성이고 이유가 보임 → 철회 → 구매 확정 버튼 활성 → 다시 요청 → 판매자 거절(사유 "불량 확인 불가") → 구매자: "반품 거절"과 사유, "반품 요청" 버튼 없음 → 관리자: 반품 목록에서 그 건 → 승인 → 대신 수거 확인 → "환불 금액 33,000원" → 구매자 주문 상세 환불 금액 `33,000원`.
- [ ] **Step 4: `c4-settlement.spec.ts`**(시연 데이터 사용). 관리자: 정산 → "정산 생성" → 기본 날짜가 지난주 월요일 → 생성(연타) → 결과에 "생성 1" → 목록에서 데모 판매자 정산 → 상세: 상품 매출 `95,000원`, 판매자 쿠폰 할인 `−5,000원`, 수수료 `−9,000원`, 배송비 `+6,000원`, 판매자 부담 반품비 `−6,000원`, 합계 `81,000원`, 지급액 `81,000원`, 라인 금액 `70,500원`·`13,500원`·`−3,000원` → 확정 → 지급 완료(메모 "E2E 지급") → 상태 "지급 완료" → 같은 기간 다시 생성 → "이미 생성된 기간입니다" → 조정: 데모 판매자 ID로 −10,000원 등록 → "다음 정산에 반영 예정" → 삭제 → 목록에서 사라짐 → `seller@kairos.demo` 로그인: 정산 목록의 지급액 `81,000원`·"지급 완료" → 상세의 라인 3줄.
- [ ] **Step 5: 새 빈 스키마에서 전체 E2E(F1~F4)를 한 번에 통과시킨다 → Commit** `test(frontend): C4 반품·정산 E2E 3개 추가`. 화면의 금액이 기대와 다르면 기대값을 고치지 말고 멈춰서 보고한다. 정리(스키마 삭제, 서버·Gradle 정지, 포트 8080·3100 확인).

---

### Task 14: 화면 캡처 대조

스펙 §12(화면 캡처 대조).

**Files:** 저장소 파일을 바꾸지 않는다(임시 Playwright 스크립트는 끝나면 지운다). 결함을 고칠 때만 해당 파일을 바꾼다.

- [ ] **Step 1: Task 13과 같은 방식으로 환경을 띄우고**(새 임시 스키마, 정산 시작일 = 지난주 월요일) 임시 스크립트로 아래 화면을 1280px 전체 페이지로 캡처해 스크래치패드의 `f4-shots/`에 저장한다: 구매자 — 반품 요청 창(변심·불량), 주문 상세(요청됨·환불 확인 중은 가능하면·완료·거절·기한 표시), 반품 내역, 반품 상세(완료·거절), 구매 확정 창; 판매자 — 반품 목록(탭), 반품 상세(요청·승인·완료), 수거 확인 창, 정산 목록, 정산 상세; 관리자 — 반품 목록·상세, 정산 목록, 생성 창과 결과, 정산 상세(생성·확정·지급 완료), 조정 화면(목록·등록 양식); F2·F3 회귀 확인용 — 구매자 주문 상세(반품 없는 일반 주문), 판매자 주문 상세, 마이페이지 메뉴.
- [ ] **Step 2: 캡처를 하나씩 직접 보고 시안(`design/*.html`)과 대조한다.** 볼 것: 글자 잘림·겹침·줄바꿈, 금액 정렬과 부호, 빈 구분점, 상단 제목, 버튼 위치, 긴 이름에서의 표 깨짐, 마이페이지 틀 안에서의 폭. 결함을 목록으로 적는다.
- [ ] **Step 3: 결함을 고치고(동작이 아니라 배치·문구만) 해당 화면을 다시 캡처해 확인한다.** `pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle` 통과. 고친 것이 있으면 전체 E2E를 새 스키마에서 한 번 더 돌린다.
- [ ] **Step 4: Commit**(고친 것이 있을 때) `fix(frontend): F4 화면 캡처 대조에서 찾은 결함 수정`. 보고서에 본 캡처 수, 찾은 결함, 고친 것, 못 고친 것을 적는다. 정리.

---

### Task 15: 문서와 최종 검증

스펙 §13.

**Files:**
- Modify: `CLAUDE.md`, `README.md`

- [ ] **Step 1: `CLAUDE.md`** — 문서 목록에 F4 스펙·계획 추가, "현재 단계" 갱신(F1~F4 화면 구현 완료, 다음 C5), 프론트 규칙: (1) 반품 환불 미리보기는 예상값이며 "예상"으로 표시한다, (2) 수거 확인은 응답을 못 받아도 자동 재전송하지 않는다, (3) 서버 강제 멱등 요청에 반품 요청·정산 생성·정산 조정 등록 추가, (4) 정산 금액은 서버 값 그대로(화면은 합산하지 않는다), 허용 계산에 조정 금액 부호 추가, (5) 날짜 계산은 `lib/datetime.ts`. 백엔드 규칙: `ReturnRefundCalculator`의 계산 본체는 하나이고 `plan`·`preview`가 함께 쓴다; `Order.checkReturnRequestable`이 반품 요청 자격의 단일 위치; demo 프로필 전용 시계와 정산 시더(운영에서는 시스템 시계 하나). 각 사실은 코드로 확인한 뒤 적는다.
- [ ] **Step 2: `README.md`** — 진행 단계에 F4 추가. 정산 화면을 새 환경에서 보려면 백엔드를 demo 프로필 + 정산 시작일 = 지난주 월요일로 띄운다는 것과, 지난주 월요일을 구하는 한 줄 명령을 적는다.
- [ ] **Step 3: 전체 검증** — `./gradlew test`, `./gradlew pitest`(전체 %와 `ReturnRefundCalculator` 클래스 결과), `cd frontend && pnpm check:api && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle`. 출력의 요약 줄을 기록한다. (전체 E2E는 Task 13·14에서 돌린 결과를 쓴다 — 그 뒤 화면 코드가 바뀌었으면 다시 돌린다.)
- [ ] **Step 4: Commit** `docs: F4 반품·정산 화면 규칙과 현재 단계 갱신` → Gradle 데몬·서버 정리.
