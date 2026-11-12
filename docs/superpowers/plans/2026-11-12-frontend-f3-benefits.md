# 프론트엔드 F3 혜택·후기 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 쿠폰 받기·주문서의 쿠폰·포인트 사용·포인트 내역·후기·좋아요 목록과 판매자·관리자의 쿠폰·후기·포인트 화면을 실제 백엔드에 붙이고, 그에 필요한 백엔드 보강 4건을 넣는다.

**Architecture:** 백엔드는 조회 응답 필드 추가와, 결과가 같은 판정 함수 추출 하나(`CouponTerms.meetsMinimum`)만 한다. 프론트는 F1·F2 구조(`app/`은 조립, 기능은 `features/`)에 `coupons`·`points`·`reviews`·`likes`·`mypage`와 판매자·관리자 하위 기능을 더한다. 주문서는 "선택 + 그 선택으로 받은 견적"만 상태로 갖고, 화면의 모든 금액·한도·예상 할인은 견적 응답 값이다.

**Tech Stack:** Kotlin 2.3 · Spring Boot 4.1 · QueryDSL · Testcontainers / Next.js 15 · React 19 · TanStack Query · react-hook-form + zod · openapi-fetch · Vitest · Playwright

**Spec:** `docs/superpowers/specs/2026-11-12-frontend-f3-benefits-design.md` (구현자는 자기 태스크가 가리키는 절을 반드시 읽는다. 상위 규칙: `CLAUDE.md`, F2 스펙 `docs/superpowers/specs/2026-11-12-frontend-f2-purchase-design.md`, C3 스펙 `docs/superpowers/specs/2026-11-10-c3-benefits-reviews-design.md`)

## Global Constraints

- 브랜치 `feature/frontend-f3-benefits`. 커밋 메시지는 한국어 Conventional Commits. `main`·`dev`에 직접 커밋하지 않는다. push·PR은 계획 밖(오케스트레이터가 한다).
- **SDD**: API 변경은 `docs/api/openapi.yaml` → 구현 → `OpenApiContractTest` 순서. OpenAPI를 고친 커밋에 `cd frontend && pnpm gen:api` 결과(`src/api/schema.d.ts`)를 함께 넣는다.
- 백엔드: 4계층 DDD와 모듈 규칙(`ArchitectureTest`). 다른 모듈은 호출하는 쪽 `application`의 포트 + 자기 `infrastructure` 구현으로만 부른다. Flyway 마이그레이션·인덱스 추가 없음. PG 호출을 트랜잭션 안으로 옮기는 변경 금지.
- **돈 계산 클래스**(`DiscountAllocator`, `CouponTerms`, `RefundCalculator`, `ReturnRefundCalculator`): Task 2의 `meetsMinimum` 추출이 **유일하게 허용된 변경**이다. 계산 결과가 바뀌면 안 된다 — 기존 `DiscountAllocatorTest`·`OrderServiceTest`의 금액 단언을 한 줄도 고치지 않고 통과해야 한다. 그 밖에 이 클래스들을 바꿔야 할 것 같으면 **멈추고 BLOCKED로 보고**한다.
- 백엔드 테스트: 이름은 한국어, 애플리케이션 서비스 테스트는 인메모리 Fake(`order/application/Fakes.kt` 등), API 테스트는 기존 `*ApiTest` 패턴(`IntegrationTestSupport`, `CommerceTestSupport`). 실행 `./gradlew test --tests '<클래스>'`(Docker 필요). 끝나면 Gradle 데몬·컨테이너를 남기지 않는다.
- 금액·포인트: Long/정수. 프론트에서 `/`, `%`는 `src/lib/money.ts`에서만(ESLint). 할인액·예상 할인·포인트 한도·결제 금액·적립 포인트를 프론트가 계산하지 않는다. F3에서 새로 허용되는 프론트 계산은 **포인트 입력이 10의 배수인지(`mod`)** 와 **정률 %→bp(×100)** 뿐이다.
- 프론트: 액세스 토큰은 메모리에만. 낙관적 업데이트 없음. 뮤테이션 자동 재시도 없음. 필터·탭·페이지는 URL 쿼리가 단일 진실. 모든 목록·상세는 로딩·빈·오류 세 상태. 에러 메시지는 `src/api/errors.ts` 표 또는 서버 메시지 그대로. 경로의 ID는 `lib/ids.ts`의 `parseId`로 읽는다. 일시는 `lib/datetime.ts`로만 다룬다(서버 값은 오프셋 없는 KST 문자열). 데스크톱 전용(1280px). `hot` 색은 쓰지 않는다.
- 서버가 `Idempotency-Key`를 강제하는 요청(쿠폰 받기, 쿠폰 정책 등록 2종, 후기 작성, 주문 생성)은 `useIdempotentSubmit`/`useIdempotentAction`으로 보낸다. 키 규칙은 F2 그대로(`src/api/idempotency.ts`의 `isAnswered`). 후기 수정·삭제, 답글, 정책 종료, 좋아요는 키 없이 처리 중 재호출을 막는다.
- 데모 전용 코드는 `src/features/demo`에만. F3는 데모 전용 코드를 추가하지 않는다. 어떤 번들에도 `KAIROS_ADMIN`·토스 시크릿 키가 없어야 한다(`pnpm check:bundle`).
- `design/`의 기존 시안 파일은 고치지 않는다(새 파일 추가, `index.html` 링크 추가, `assets/kairos.css`에 클래스 **추가**만 허용).
- **시안 게이트**: Task 7~13(화면 구현)은 Task 1의 시안을 사용자가 확인한 뒤에만 시작한다. 화면은 확인된 시안(`design/*.html`)의 구조·문구·클래스를 따른다. Task 2~6은 기다리지 않는다(Task 6의 `MyPageShell`은 구조만 만들고, 모양은 Task 7이 확인된 `user-mypage.html`에 맞춘다).
- 구매자 전용 페이지는 `RequireAuth`로 감싼다(`(store)/layout.tsx`에는 가드가 없다). 판매자 페이지는 기존 판매자 콘솔 가드, 관리자 페이지는 기존 관리자 가드를 그대로 쓴다.
- 프론트 테스트 이름은 한국어. 순수 로직은 `.ts` + `.test.ts`(Vitest), 화면 흐름은 E2E. 각 프론트 태스크의 검증: `cd frontend && pnpm lint && pnpm typecheck && pnpm test`.
- 의존성을 추가하지 않는다(필요하면 BLOCKED로 보고). claude.ai Artifact·Claude Design·Figma 토큰 사용 금지.

## Review Focus

스펙이 암시하지만 놓치기 쉬운 입력·상황. 각 줄의 테스트는 해당 태스크에 들어 있다.

1. 쿠폰·포인트를 바꾼 직후(새 견적 도착 전) 결제하기를 누름 → 옛 금액으로 주문이 나가지 않는다. 버튼이 막혀 있고, 제출 본문의 선택과 `expectedTotalAmount`는 같은 견적에서 온다(Task 8).
2. 견적의 `usable=true` 쿠폰은 실제로 고르면 반드시 성공하고 `usable=false` 쿠폰은 반드시 거절된다 → 화면과 결제가 어긋나지 않는다(Task 2 일치 테스트).
3. 선택한 쿠폰이 그사이 다른 탭에서 쓰였거나 만료됨(견적·주문이 `COUPON_NOT_AVAILABLE`) → 화면이 오류에 갇히지 않고 선택을 되돌리거나 비운 뒤 다시 견적한다. 되돌린 선택도 실패하면 전부 비우고, 그래도 실패하면 오류 상태를 보인다(무한 재시도 없음)(Task 8).
4. 포인트 입력에 `15`, `-10`, `1e3`, 공백, 한도 초과, 잔액 1,000P 미만 → 견적이 나가지 않고 칸 아래 안내가 뜬다(Task 8).
5. 후기 본문이 공백만 길게, 이모지 포함 10자, 사진 업로드 중 제출, 이미 다른 탭에서 작성함(409) → 각각 검증 실패·막힘·창 닫고 상태 갱신(Task 10).

---

## 파일 구조

**백엔드(수정)** — 경로는 `src/main/kotlin/com/dogyun/kairos/` 기준
- `docs/api/openapi.yaml`
- `order/domain/{CouponTerms,DiscountAllocator}.kt`(판정 함수 추출만)
- `order/application/{OrderService,OrderResults,BenefitApplier,OrderQueryService}.kt`, 신규 `order/application/ReviewStateReader.kt`
- `order/infrastructure/BenefitApplierImpl.kt`, 신규 `order/infrastructure/ReviewStateReaderImpl.kt`
- `order/presentation/`의 견적·주문 응답 DTO
- `benefit/application/{CouponService,CouponResults,CouponQueryService,BenefitProductReader}.kt`, `benefit/domain/CouponRepository.kt`, `benefit/infrastructure/{CouponRepositoryImpl,BenefitProductReaderImpl}.kt`, `benefit/presentation/`의 응답 DTO
- `review/application/{ReviewResults,ReviewQueryService}.kt`, 신규 `review/application/ReviewStateQueryService.kt`, `review/domain/ReviewRepository.kt`와 구현, `review/presentation/`의 응답 DTO

**프론트(신규, `frontend/src/`)**
```
features/coupons/   api.ts  discount-text.ts(+test)  expiry.ts(+test)  claim-error.ts(+test)
                    components/{CouponCard,ClaimableCouponList,ProductCouponSection,MyCouponsPage,MyCouponList}.tsx
features/points/    api.ts  transaction-text.ts(+test)
                    components/{MyPointsPage,PointSummaryCard,PointTransactionTable}.tsx
features/reviews/   api.ts  schemas.ts(+test)  review-state.ts(+test)  image-rules.ts(+test)
                    components/{StarRating,StarRatingInput,ReviewFormDialog,ReviewImagePicker,ReviewCard,
                                ProductReviewSection,MyReviewsPage,OrderItemReviewAction}.tsx
features/likes/     api.ts  components/MyLikesPage.tsx
features/mypage/    menu.ts  components/MyPageShell.tsx
features/checkout/  benefit-selection.ts(+test)  points-input.ts(+test)  quote-recovery.ts(+test)
                    components/{BenefitSection,CouponPickerDialog,PointsField}.tsx
features/seller/coupons/  api.ts  components/SellerCouponsPage.tsx
features/seller/reviews/  api.ts  reply-schema.ts(+test)  components/{SellerReviewsPage,ReplyEditor}.tsx
features/admin/coupons/   api.ts  components/AdminCouponsPage.tsx
features/admin/points/    api.ts  components/AdminMemberPointsPage.tsx
features/coupon-policies/ schema.ts(+test)  policy-status.ts(+test)  components/{PolicyTable,PolicyFormDialog}.tsx   (판매자·관리자 공유)
app/(store)/my/page.tsx                      → /my/orders 로 redirect
app/(store)/my/(menu)/layout.tsx             → RequireAuth + MyPageShell
app/(store)/my/(menu)/{coupons,points,reviews,likes}/page.tsx
app/seller/{coupons,reviews}/page.tsx
app/admin/coupons/page.tsx  admin/members/points/page.tsx
```
**프론트(이동)**: `app/(store)/my/orders/page.tsx`, `app/(store)/my/orders/[orderId]/page.tsx` → `app/(store)/my/(menu)/orders/…`(URL은 그대로). `…/[orderId]/pay`, `…/[orderId]/complete`는 제자리.

**프론트(수정)**: `lib/{money,datetime,query-keys}.ts`, `api/errors.ts`, `components/common/StatusChip.tsx`, `components/layout/{StoreHeader,SideNav}.tsx`(또는 메뉴 정의 파일), `features/checkout/{api.ts,checkout-guard.ts}`, `features/checkout/components/{CheckoutPage,CheckoutSummary,OrderItems}.tsx`, `features/payment/pay-state.ts`, `features/orders/components/{OrderItemRow,ItemCancelDialog,ConfirmPurchaseButton,PaymentSummaryCard}.tsx`, `features/catalog/components/ProductDetailView.tsx`, `e2e/fixtures/api.ts`

**시안**: `design/user-checkout-benefit.html`, `user-mypage.html`, `user-review.html`, `user-product-detail-benefit.html`, `seller-benefit.html`, `design/index.html`(링크), `design/assets/kairos.css`(추가)

---

### Task 1: 시안 5개 (사용자 확인 게이트)

스펙 §4.

**Files:**
- Create: `design/user-checkout-benefit.html`, `design/user-mypage.html`, `design/user-review.html`, `design/user-product-detail-benefit.html`, `design/seller-benefit.html`
- Modify: `design/index.html`(링크 5개 추가), `design/assets/kairos.css`(클래스 추가만)

- [ ] **Step 1: 기존 시안의 구조와 토큰을 읽는다** — `design/user-checkout.html`, `user-product-detail.html`, `user-order-detail.html`, `seller-order.html`, `assets/kairos.css`. 새 파일 두 개(`*-benefit.html`)는 기존 파일을 복사해 시작한다.
- [ ] **Step 2: 다섯 파일을 스펙 §4 표의 "담는 것" 그대로 그린다.** 한 파일에 여러 상태는 F2 시안처럼 상태 전환(탭·버튼)으로 보인다. 반드시 들어갈 상태:
  - `user-checkout-benefit.html`: 쿠폰 미선택(쓸 수 있는 장수 표시) / 상품 쿠폰·장바구니 쿠폰·포인트 적용(C3 스펙 §6 숫자: 상품 70,000, 상품 쿠폰 −6,000, 장바구니 쿠폰 −5,000, 포인트 −10,000, 결제 49,000) / 쿠폰 선택 창(쓸 수 있음 + 쓸 수 없음과 이유 + "다른 상품에 적용됨" + 적용 안 함) / 포인트 입력 오류 / 0원 결제("주문하기", 포인트 입력 막힘)
  - `user-mypage.html`: 왼쪽 메뉴 5개, 쿠폰 탭 2개(받을 수 있는 쿠폰: 받기/받음, 내 쿠폰: 사용 가능·사용 완료·기간 만료, "곧 만료"), 포인트(잔액·만료 예정·내역 5유형), 좋아요(판매 종료 카드 포함), 빈 상태
  - `user-review.html`: 작성 창(별점, 글자 수, 사진 3장·업로드 중, 적립 안내), 수정 창, 내 후기 목록(답글 있음/없음, 수정됨), 주문 상세 항목 줄의 후기 상태 4가지
  - `user-product-detail-benefit.html`: 쿠폰 영역(로그인/비로그인, 받음), 후기 영역(요약, 목록, 사진, 판매자 답글, 빈 상태, 페이지)
  - `seller-benefit.html`: 쿠폰 정책 표(진행 중·종료·기간 종료), 등록 양식(정액/정률 전환, 안내 문구 2개), 종료 확인, 후기 목록(미답변 표시), 답글 입력·수정
- [ ] **Step 3: `design/index.html`에 링크를 더하고 브라우저로 다섯 파일을 열어 깨진 곳이 없는지 확인한다.**
- [ ] **Step 4: Commit** `docs(design): F3 혜택·후기 시안 5개 추가`
- [ ] **Step 5: 게이트** — 오케스트레이터가 사용자에게 시안을 보여 확인받는다. 확인 전에는 프론트 화면 태스크(Task 7 이후)를 시작하지 않는다. 백엔드(Task 2~5)와 프론트 공통 기반(Task 6)은 기다리지 않는다.

---

### Task 2: 견적에 "적용 가능한 내 쿠폰" (백엔드)

스펙 §5-1.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`(생성)
- Modify: `order/domain/{CouponTerms,DiscountAllocator}.kt`
- Modify: `order/application/{OrderService,OrderResults,BenefitApplier}.kt`, `order/infrastructure/BenefitApplierImpl.kt`, `order/presentation/`의 견적 응답 DTO
- Modify: `benefit/application/{CouponService,CouponResults}.kt`, `benefit/domain/CouponRepository.kt`, `benefit/infrastructure/CouponRepositoryImpl.kt`
- Modify: `src/test/kotlin/com/dogyun/kairos/order/application/Fakes.kt`(Fake `BenefitApplier`)
- Test: `order/domain/CouponTermsTest.kt`(없으면 신규), 수정 `order/application/OrderServiceTest.kt`, 신규 `order/presentation/OrderQuoteCouponsApiTest.kt`

**Interfaces:**
- Produces (OpenAPI):
  - `QuoteCoupon { couponId: int64, name: string, issuerType: PLATFORM|SELLER, discountType: FIXED|RATE, discountValue: int64, maxDiscount?: int64|null, minOrderAmount?: int64|null, expiresAt: LocalDateTime, usable: boolean, unusableReason?: MIN_AMOUNT_NOT_MET|NOTHING_TO_DISCOUNT|null, expectedDiscount?: int64|null }` — required: `couponId, name, issuerType, discountType, discountValue, expiresAt, usable`
  - `QuoteLine.availableCoupons: QuoteCoupon[]`(required), `OrderQuote.cartCoupons: QuoteCoupon[]`(required)
- Produces (Kotlin):
  - `CouponTerms.meetsMinimum(targetAmount: Long): Boolean` — `(minOrderAmount ?: 0) <= targetAmount`
  - `BenefitApplier.listUsable(memberId: Long): List<UsableCoupon>` · `data class UsableCoupon(val terms: ReservedCoupon, val name: String, val expiresAt: LocalDateTime)`
  - `CouponService.listUsableCoupons(memberId: Long): List<UsableCouponResult>`(회원의 `AVAILABLE`·만료 전 쿠폰 전부와 정책 조건·이름·만료일), `CouponRepository.findAvailableByMember(memberId: Long, now: LocalDateTime): List<Coupon>`
  - `OrderResults.kt`: `data class QuoteCoupon(...)`(위 필드, 문자열 enum 이름), `QuoteLine.availableCoupons`, `OrderQuote.cartCoupons`

- [ ] **Step 1: 실패하는 테스트 — `CouponTermsTest`**
  - `최소_주문_금액과_같으면_충족한다` — `minOrderAmount=30000`, `meetsMinimum(30000) == true`
  - `1원_모자라면_충족하지_않는다` — `meetsMinimum(29999) == false`
  - `최소_주문_금액이_없으면_항상_충족한다` — `minOrderAmount=null`, `meetsMinimum(0) == true`
- [ ] **Step 2: 실패 확인 → `meetsMinimum` 구현 → `DiscountAllocator.allocate`의 두 비교(`(terms.minOrderAmount ?: 0) > amounts[i]`, `(cartCoupon.minOrderAmount ?: 0) > target`)를 `!terms.meetsMinimum(...)`로 바꾼다.** 다른 줄은 건드리지 않는다.
- [ ] **Step 3: `./gradlew test --tests '*CouponTermsTest' --tests '*DiscountAllocatorTest' --tests '*OrderServiceTest' --tests '*RefundCalculatorTest'` 통과(기존 테스트 무수정) → Commit** `refactor(backend): 쿠폰 최소 금액 판정을 CouponTerms로 추출`
- [ ] **Step 4: OpenAPI에 `QuoteCoupon`과 두 필드를 추가한다.** `quoteOrder` 설명에 "`availableCoupons`·`cartCoupons`는 내 사용 가능한 쿠폰이며 `usable`·`expectedDiscount`는 주문 생성과 같은 판정이다. 상품 쿠폰의 `usable`은 그 항목에 그 쿠폰만 썼을 때의 판정이다(한 쿠폰을 두 항목에 쓰거나 한 항목에 두 장 쓰는 것은 별도로 `ORDER_INVALID`). 장바구니 쿠폰은 요청에 담긴 상품 쿠폰을 적용한 뒤 금액으로 판정한다"를 적는다. (`OpenApiContractTest`는 경로만 비교한다 — 필드는 API 테스트의 단언으로 확인한다.)
- [ ] **Step 5: 실패하는 테스트 — `OrderServiceTest`에 추가**(Fake `BenefitApplier.listUsable`이 준비한 쿠폰을 돌려준다). C3 스펙 §6 주문(b1 30,000×1 판매자 B, c1 20,000×2 판매자 C):
  - `견적은_항목마다_대상이_맞는_상품_쿠폰과_예상_할인을_준다` — 플랫폼 정액 2,000(`ALL_PRODUCTS`), 판매자 C 정률 10% 최대 5,000(`SELLER`) 보유 → b1 목록 = [플랫폼 쿠폰 `expectedDiscount=2000`], c1 목록 = [판매자 쿠폰 `4000`, 플랫폼 쿠폰 `2000`](할인 큰 순), 전부 `usable=true`
  - `장바구니_쿠폰은_상품_쿠폰_적용_뒤_금액으로_판정한다` — 장바구니 10% 최대 5,000 최소 50,000: 상품 쿠폰 없이 `usable=true, expectedDiscount=5000`; 위 두 상품 쿠폰을 요청에 담아도(합계 64,000) `usable=true, expectedDiscount=5000`
  - `상품_쿠폰으로_합계가_최소_금액_아래로_내려가면_장바구니_쿠폰은_사용_불가다` — 최소 69,000 장바구니 쿠폰: 상품 쿠폰 없으면(대상 70,000) `usable=true`, b1에 2,000 쿠폰을 담으면(대상 68,000) `usable=false, unusableReason=MIN_AMOUNT_NOT_MET, expectedDiscount=null`. 경계도 고정: 최소 68,000 쿠폰은 같은 요청에서 `usable=true`(대상 = 최소 금액)
  - `최소_금액_미달_상품_쿠폰은_사용_불가로_목록에_남는다` — 최소 35,000 상품 쿠폰: b1(30,000) 목록에 `usable=false, MIN_AMOUNT_NOT_MET`, c1(40,000) 목록에 `usable=true`
  - `상품_쿠폰이_전액을_덮으면_장바구니_쿠폰은_할인할_금액이_없다` — 항목 전부에 lineAmount 이상 정액 쿠폰을 담은 요청: 모든 장바구니 쿠폰 `usable=false, NOTHING_TO_DISCOUNT`
  - `대상이_다른_쿠폰과_종류가_다른_쿠폰은_목록에_없다` — 다른 판매자의 `SELLER` 쿠폰, 다른 상품의 `PRODUCT` 쿠폰은 어느 항목에도 없고, 장바구니 쿠폰은 `availableCoupons`에 없고, 상품 쿠폰은 `cartCoupons`에 없다
  - `정렬은_사용_가능_먼저_예상_할인_큰_순_만료_빠른_순이다`
  - **일치 테스트(Review Focus 2)** `usable인_쿠폰은_고르면_성공하고_아닌_쿠폰은_거절된다` — 판정의 뜻을 그대로 검증한다. (가) 상품 쿠폰: 쿠폰·포인트 없는 견적의 각 항목 `availableCoupons` 전부에 대해 **`{그 sku → 그 쿠폰}` 하나만**(장바구니 쿠폰 없음, 포인트 0) 담아 견적한다 — `usable=true`면 성공하고 그 줄의 `productCouponDiscount == expectedDiscount`, `usable=false`면 `COUPON_MIN_AMOUNT_NOT_MET`. (나) 장바구니 쿠폰: 상품 쿠폰 선택 P(없음 / b1에 2,000 / 전액 덮음 세 가지)로 받은 견적의 `cartCoupons` 전부에 대해 **같은 P에 `cartCouponId`만 더해**(포인트 0) 견적한다 — `usable=true`면 성공하고 `cartCouponDiscount == expectedDiscount`, `usable=false`면 `COUPON_MIN_AMOUNT_NOT_MET`(이유 `MIN_AMOUNT_NOT_MET`) 또는 `COUPON_NOT_APPLICABLE`(이유 `NOTHING_TO_DISCOUNT`)
- [ ] **Step 6: 실패 확인 → 구현.** `OrderService.quote`가 `benefitApplier.listUsable(memberId)`를 한 번 부르고, `ReservedCoupon` → `CouponTerms` 변환은 기존 변환(`allocate`가 쓰는 것)을 재사용한다. 상품 쿠폰: `kind == PRODUCT && appliesTo(productId, sellerId)`인 것만 항목 목록에, `usable = meetsMinimum(lineAmount)`, `expectedDiscount = discountFor(lineAmount)`(usable일 때만). 장바구니 쿠폰: `target = order.itemsAmount − allocation.productCouponDiscount`; `target <= 0`이면 `NOTHING_TO_DISCOUNT`, 아니면 `meetsMinimum(target)`·`discountFor(target)`. 이 조립은 `OrderService` 안의 private 함수나 `order/application`의 작은 클래스에 둔다(도메인 계산 클래스에 넣지 않는다). benefit 쪽: `findAvailableByMember`(상태 `AVAILABLE`, `expires_at > now`, 회원 일치)와 `listUsableCoupons`(기존 `termsOf` 재사용 + 정책 이름·쿠폰 만료일).
- [ ] **Step 7: 실패하는 테스트 — `OrderQuoteCouponsApiTest`**(실제 DB)
  - `견적_응답에_내_쿠폰_목록이_있다` — 쿠폰 정책 생성·발급 후 `POST /orders/quote`: `data.items[0].availableCoupons[0].couponId`, `usable`, `expectedDiscount`, `data.cartCoupons` 존재
  - `남의_쿠폰_사용한_쿠폰_만료된_쿠폰은_목록에_없다`
  - `견적은_쿠폰_상태를_바꾸지_않는다` — 견적 전후 `GET /coupons/me` 응답 본문 동일
  - `쿠폰이_없으면_빈_배열이다`
- [ ] **Step 8: `./gradlew test --tests '*OrderServiceTest' --tests '*OrderQuote*' --tests '*OpenApiContractTest' --tests '*ArchitectureTest'` 통과 → `cd frontend && pnpm gen:api && pnpm check:api` → Commit** `feat(backend): 견적 응답에 적용 가능한 내 쿠폰 목록 추가`

---

### Task 3: 주문 항목의 후기 상태 (백엔드)

스펙 §5-2.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`(생성)
- Create: `order/application/ReviewStateReader.kt`, `order/infrastructure/ReviewStateReaderImpl.kt`, `review/application/ReviewStateQueryService.kt`
- Modify: `order/application/{OrderQueryService,OrderResults}.kt`, `order/presentation/`의 주문 응답 DTO, `review/domain/ReviewRepository.kt`와 그 구현, `review/application/ReviewService.kt`(기한 판정을 공유 함수로 바꾸는 한 줄)
- Modify: `src/test/kotlin/com/dogyun/kairos/order/application/Fakes.kt`
- Test: 신규 `review/application/ReviewStateQueryServiceTest.kt`, 수정 `order/application/OrderQueryServiceTest.kt`, 신규 `order/presentation/OrderItemReviewStateApiTest.kt`

**Interfaces:**
- Produces (OpenAPI): `OrderItemResult.review`: `OrderItemReview`에 `nullable: true`, required 아님(기존 `returnRequest`와 같은 선언 방식 — 판매자·관리자 응답에는 `"review": null`로 나간다. 구매자 주문 상세에서만 채움). `OrderItemReview { status: NOT_ELIGIBLE|WRITABLE|WRITTEN|EXPIRED|DELETED (required), reviewId?: int64|null, writableUntil?: LocalDateTime|null }`
- Produces (Kotlin):
  - `order/application/ReviewStateReader.kt`: `interface ReviewStateReader { fun statesOf(candidates: List<ReviewCandidate>): Map<Long, ItemReviewState> }` · `data class ReviewCandidate(val orderItemId: Long, val confirmedAt: LocalDateTime)` · `data class ItemReviewState(val status: String, val reviewId: Long?, val writableUntil: LocalDateTime?)`
  - `review/application/ReviewStateQueryService.statesOf(candidates: List<Pair<Long, LocalDateTime>>): Map<Long, ReviewStateResult>` — 상태 `WRITABLE`/`WRITTEN`/`EXPIRED`/`DELETED`만 돌려준다(`NOT_ELIGIBLE`은 order가 정한다)
  - `ReviewRepository.findStatesByOrderItemIds(ids: Collection<Long>): Map<Long, ReviewRowState>` — 소프트 삭제된 행 포함, **쿼리 한 번** · `data class ReviewRowState(val reviewId: Long, val deleted: Boolean)`
  - 기한 판정 공유: `review/domain`에 `fun reviewWritableUntil(confirmedAt: LocalDateTime, deadline: Duration): LocalDateTime` 하나를 두고 `ReviewService.write`와 `ReviewStateQueryService`가 같이 쓴다. 판정은 기존과 같게 `writableUntil.isBefore(now)`면 기한 지남.

- [ ] **Step 1: OpenAPI에 `OrderItemReview`와 필드를 추가한다.**
- [ ] **Step 2: 실패하는 테스트 — `ReviewStateQueryServiceTest`**(Fake 저장소, 고정 `Clock`, 기한 90일)
  - `후기가_없고_기한_안이면_WRITABLE이고_writableUntil은_확정_시각에_기한을_더한_값이다`
  - `writableUntil_시각까지는_WRITABLE이고_1초_뒤는_EXPIRED다` — 경계 두 시각
  - `후기가_있으면_WRITTEN이고_reviewId가_있다`(기한이 지났어도 `WRITTEN`)
  - `삭제한_후기는_DELETED다`
  - `후보가_여러_개여도_저장소는_한_번만_조회한다` — Fake 호출 횟수 1
  - `작성_API의_판정과_같다` — `ReviewServiceTest` 쪽에 추가: `WRITABLE` 경계 시각에 `write` 성공, 1초 뒤 `REVIEW_NOT_ELIGIBLE`
- [ ] **Step 3: 실패 확인 → 구현**(저장소 조회는 소프트 삭제 필터를 우회해야 한다 — 기존 `existsAnyByOrderItemId`가 쓰는 방식과 같게).
- [ ] **Step 4: 실패하는 테스트 — `OrderQueryServiceTest`에 추가**(Fake `ReviewStateReader`)
  - `구매_확정된_항목은_후기_상태를_reader에서_받는다`
  - `확정_전_판매자_주문의_항목과_취소된_항목은_NOT_ELIGIBLE이고_reader에_묻지_않는다` — 후보 목록에 없음
  - `주문_한_건의_후기_상태는_한_번에_조회한다` — 판매자 주문 2개·항목 3개에서 Fake 호출 1회
  - `판매자와_관리자_주문_조회의_review는_null이다` — `getForSeller`·`getForAdmin` 결과의 `review == null`, Fake 호출 0회
- [ ] **Step 5: 구현.** `OrderQueryService.getMine`만 후보(판매자 주문 `CONFIRMED` + 항목 `ORDERED` + `confirmedAt` 있음)를 모아 `reviewStateReader.statesOf`를 한 번 부른다. `ReviewStateReaderImpl`은 `ReviewStateQueryService`를 부른다(`ReviewService`를 주입하지 않는다 — 빈 순환).
- [ ] **Step 6: 실패하는 테스트 — `OrderItemReviewStateApiTest`**(실제 DB, 기존 `CommerceTestSupport`의 주문·발송·확정 헬퍼)
  - `확정_전에는_NOT_ELIGIBLE_확정_후에는_WRITABLE_작성_후에는_WRITTEN_삭제_후에는_DELETED다` — 한 주문으로 네 단계를 차례로 확인(`reviewId`는 작성 응답의 값과 같다)
- [ ] **Step 7: `./gradlew test --tests '*ReviewState*' --tests '*ReviewServiceTest' --tests '*OrderQuery*' --tests '*OrderItemReviewState*' --tests '*OpenApiContractTest' --tests '*ArchitectureTest'` 통과 → `pnpm gen:api && pnpm check:api` → Commit** `feat(backend): 구매자 주문 상세에 항목별 후기 상태 추가`

---

### Task 4: 후기 이미지 파일 ID, 쿠폰 정책 대상 상품 이름 (백엔드)

스펙 §5-3, §5-4.

**Files:**
- Modify: `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts`(생성)
- Modify: `review/application/{ReviewResults,ReviewQueryService}.kt`, `review/presentation/`의 응답 DTO
- Modify: `benefit/application/{CouponResults,CouponQueryService,CouponPolicyService,BenefitProductReader}.kt`, `benefit/infrastructure/BenefitProductReaderImpl.kt`, `benefit/presentation/`의 응답 DTO, 필요하면 `catalog/application`의 상품 이름 일괄 조회
- Test: 수정 `review/presentation/`의 후기 조회 API 테스트, `benefit/presentation/`의 쿠폰 정책 API 테스트, 해당 서비스 테스트의 Fake

**Interfaces:**
- Produces (OpenAPI): `ReviewResult.images: ReviewImage[]`(required) · `ReviewImage { fileId: int64, url: string }`(둘 다 required, `imageUrls`는 그대로 둔다, 순서는 `imageUrls`와 같다) · `CouponPolicyResult.targetProductName?: string|null`
- Produces (Kotlin): `ReviewResult.images: List<ReviewImageResult>` · `BenefitProductReader.findProductNames(productIds: Collection<Long>): Map<Long, String>`(없는·삭제된 상품은 빠진다) · `CouponPolicyResult.targetProductName: String?`

- [ ] **Step 1: OpenAPI에 두 필드를 추가한다.**
- [ ] **Step 2: 실패하는 테스트**
  - 후기: `후기_응답에_이미지_파일_ID가_순서대로_있다` — 사진 2장 후기의 `images[0].fileId`·`url`이 올린 파일과 같고 `images.map{url} == imageUrls`; `사진_없는_후기의_images는_빈_배열이다`; `받은_fileId로_수정하면_사진이_유지된다` — `GET /reviews/me`의 `images[].fileId`를 그대로 `PATCH`에 넣어 200, 다시 조회해 같은 사진
  - 쿠폰: `상품_하나_대상_정책에는_대상_상품_이름이_있다`(구매자 `GET /coupons/policies?productId=`, 판매자·관리자 목록 세 곳), `다른_대상_유형은_targetProductName이_null이다`, `정책_목록_한_페이지의_상품_이름은_한_번에_조회한다`(서비스 테스트, Fake 호출 1회), `삭제된_상품은_null이다`
- [ ] **Step 3: 실패 확인 → 구현 → `./gradlew test --tests '*Review*' --tests '*Coupon*' --tests '*OpenApiContractTest' --tests '*ArchitectureTest'` 통과 → `pnpm gen:api && pnpm check:api` → Commit** `feat(backend): 후기 이미지 파일 ID와 쿠폰 정책 대상 상품 이름 추가`

---

### Task 5: 백엔드 보강 마무리 점검

- [ ] **Step 1: `./gradlew test` 전체 통과**(실패·건너뜀 0). 실패하면 원인을 고치고 다시 돌린다.
- [ ] **Step 2: `./gradlew pitest`** — `build.gradle.kts`의 임계값(mutation 79, coverage 99)을 지킨다. `CouponTerms.meetsMinimum`의 살아남은 변이가 있으면 Task 2 Step 1의 경계 테스트를 보강한다.
- [ ] **Step 3: `git diff dev -- src/main/kotlin/com/dogyun/kairos/order/domain/`를 읽어 `CouponTerms`·`DiscountAllocator`의 변경이 `meetsMinimum` 추출뿐인지 확인한다.** 테스트 디렉터리의 `DiscountAllocatorTest`·`RefundCalculatorTest`는 diff가 없어야 한다.
- [ ] **Step 4: Gradle 데몬 정리(`./gradlew --stop`), 남은 테스트 컨테이너 확인.** 고친 것이 있으면 Commit `test(backend): F3 백엔드 보강 점검`.

---

### Task 6: 프론트 공통 기반

스펙 §6, §11.

**Files:**
- Modify: `lib/{money,datetime,query-keys}.ts`(+테스트), `api/errors.ts`(+테스트), `components/common/StatusChip.tsx`(+테스트), `components/layout/StoreHeader.tsx`, 판매자·관리자 사이드 메뉴 정의
- Create: `features/mypage/{menu.ts,components/MyPageShell.tsx}`, `features/coupons/{discount-text.ts,expiry.ts}`(+테스트), `app/(store)/my/page.tsx`, `app/(store)/my/(menu)/layout.tsx`
- Move: `app/(store)/my/orders/page.tsx`, `app/(store)/my/orders/[orderId]/page.tsx` → `app/(store)/my/(menu)/orders/…`

**Interfaces:**
- Produces:
  - `money.ts`: `percentToBp(percent: number): number`(1~100의 정수만, 아니면 throw — `percent * 100`), 기존 `formatBp` 재사용
  - `datetime.ts`: `hasPassedKst(kstLocalDateTime: string, nowMs: number): boolean`(`secondsUntilKst(...) <= 0`), `fromDateTimeInput(value: string): string | null`(`"2026-11-12T10:00"` → `"2026-11-12T10:00:00"`, 형식이 아니면 null), `formatDateTimeMinute(s: string): string`(`"11월 12일 10:00"` — 문자열만 다룬다), `formatMonthDay(s: string): string`(`"11월 12일"`)
  - `features/coupons/discount-text.ts`: `type DiscountTerms = { discountType: "FIXED" | "RATE"; discountValue: number; maxDiscount?: number | null; minOrderAmount?: number | null }` · `discountLabel(t): string` · `conditionLabel(t): string | null`
  - `features/coupons/expiry.ts`: `isExpiringSoon(expiresAt: string, nowMs: number): boolean`(남은 시간 0 초과 7일 이하)
  - `query-keys.ts`: `coupons: { policiesRoot, policies(productId?: number), mine(params?) , mineRoot }`, `points: { root, summary, transactions(params?) }`, `reviews: { productRoot(productId), product(productId, page), mine(params?), mineRoot }`, `likes: { root, list(params?) }`, `sellerCoupons: { lists, list(params?) }`, `sellerReviews: { lists, list(params?) }`, `adminCoupons: { lists, list(params?) }`, `adminMemberPoints: (memberId: number, params?)`; `checkout.quote(items, benefit?)`로 인자 추가 — `benefit`이 없으면 **넷째 원소를 넣지 않는다**(`["checkout","quote",items]`가 `[…, items, benefit]`의 접두사로 남아 기존 무효화가 계속 맞는다. 테스트로 고정)
  - `features/mypage/menu.ts`: `MY_MENU: { href: string; label: string }[]` = 주문 내역 `/my/orders`, 쿠폰 `/my/coupons`, 포인트 `/my/points`, 내 후기 `/my/reviews`, 좋아요 `/my/likes` · `activeMenuHref(pathname: string): string | null`(`/my/orders/12` → `/my/orders`)
  - `MyPageShell`: 왼쪽 메뉴(현재 항목 `aria-current="page"`) + 오른쪽 내용

- [ ] **Step 1: 실패하는 테스트**
  - `money.test.ts`: `percentToBp(10) === 1000`, `percentToBp(1) === 100`, `percentToBp(100) === 10000`, `0`·`101`·`10.5`·`NaN`은 throw; `formatBp(1000)`은 `"10%"`, `formatBp(1050)`은 기존 규칙대로(기존 테스트가 있으면 추가하지 않는다)
  - `datetime.test.ts`: `hasPassedKst("2026-11-12T10:00:00", <그 시각의 ms>) === true`, 1초 전은 false(브라우저 시간대와 무관 — KST 고정); `fromDateTimeInput("2026-11-12T10:00") === "2026-11-12T10:00:00"`, `"2026-11-12"`·`""`는 null; `formatDateTimeMinute("2026-11-12T10:05:30") === "11월 12일 10:05"`, `formatMonthDay("2026-01-05T00:00:00") === "1월 5일"`
  - `discount-text.test.ts`: 정액 3,000 → `"3,000원 할인"`; 정률 1000bp 최대 5,000 → `"10% 할인 (최대 5,000원)"`; 정률 최대 없음 → `"10% 할인"`; 최소 30,000 → `conditionLabel === "30,000원 이상 구매 시"`; 최소 없음·0 → `null`
  - `expiry.test.ts`: 정확히 7일 남음 true, 7일 1초 false, 이미 지남 false
  - `menu.test.ts`: `activeMenuHref("/my/orders/12") === "/my/orders"`, `"/my/coupons"`, `"/my/orders/12/pay"`은 `"/my/orders"`, `"/cart"`는 null
  - `errors.test.ts`: `REVIEW_INVALID`·`COUPON_POLICY_INVALID`는 `form`, `REVIEW_NOT_FOUND`는 `toast`(표에 없으면 서버 메시지), `COUPON_SOLD_OUT` 메시지 `"쿠폰이 모두 소진되었습니다."`, `COUPON_POLICY_ENDED` 메시지 `"종료된 쿠폰입니다."`
  - `StatusChip.test.tsx`: 쿠폰 `AVAILABLE` "사용 가능"·`USED` "사용 완료"·`EXPIRED` "기간 만료", 정책 `ACTIVE` "진행 중"·`ENDED` "종료"·`PERIOD_OVER` "기간 종료", 답글 `UNANSWERED` "미답변"
- [ ] **Step 2: 실패 확인 → 구현 → 통과.**
- [ ] **Step 3: 라우트 이동과 틀.** 두 `page.tsx`를 `(menu)` 그룹으로 옮기고 각 파일의 `RequireAuth`는 `(menu)/layout.tsx`로 올린다(layout: `<Suspense>` → `RequireAuth` → `MyPageShell` — `RequireAuth`가 `useSearchParams`를 쓰므로 Suspense 경계가 필요하다. 각 page의 기존 `<Suspense>`는 그대로 둔다). `OrderListPage`·`OrderDetailPage`의 바깥 여백·너비 컨테이너가 틀 안에서 겹치면 그 컴포넌트를 고친다. `app/(store)/my/page.tsx`는 `redirect("/my/orders")`. `pay`·`complete` 페이지는 옮기지 않는다. `pnpm build`로 `/my/orders`, `/my/orders/[orderId]`, `/my/orders/[orderId]/pay`, `/my/orders/[orderId]/complete`가 모두 빌드되는지 확인한다(라우트 충돌 없음).
- [ ] **Step 4: 헤더·사이드 메뉴.** `StoreHeader`의 "마이" 링크(`/my/orders`) 문구를 "마이페이지"로 바꾼다(href 유지. 기존 E2E는 이 링크를 문구로 찾지 않는다). 판매자 사이드 메뉴에 "쿠폰 관리"(`/seller/coupons`)·"후기 관리"(`/seller/reviews`), 관리자 사이드 메뉴에 "쿠폰 관리"(`/admin/coupons`)·"회원 포인트"(`/admin/members/points`).
- [ ] **Step 5: `pnpm lint && pnpm typecheck && pnpm test && pnpm build` → Commit** `feat(frontend): F3 공통 기반(마이페이지 틀, 할인 문구, 쿼리 키) 추가`

---

### Task 7: 쿠폰 받기와 내 쿠폰

스펙 §8-1, §8-3. 시안 `design/user-product-detail-benefit.html`, `user-mypage.html`.

**Files:**
- Create: `features/coupons/{api.ts,claim-error.ts}`(+테스트), `features/coupons/components/*`, `app/(store)/my/(menu)/coupons/page.tsx`
- Modify: `features/catalog/components/ProductDetailView.tsx`

**Interfaces:**
- Consumes: Task 6의 `discountLabel`·`conditionLabel`·`isExpiringSoon`·쿼리 키; `useIdempotentAction`
- Produces:
  - `useClaimablePolicies(productId: number | undefined, enabled: boolean)` — `GET /coupons/policies`(`productId` 있으면 쿼리로), `size` 50
  - `useMyCoupons(params: { status: "AVAILABLE" | "USED" | "EXPIRED"; page: number })`
  - `claimCoupon(policyId: number, key: string): Promise<ClaimCouponResponse>`(실패는 던진다)
  - `claimOutcome(e: unknown): { message: string; refetch: boolean }` — `COUPON_ALREADY_ISSUED` → `"이미 받은 쿠폰입니다."`, `COUPON_SOLD_OUT` → `"쿠폰이 모두 소진되었습니다."`, `COUPON_POLICY_ENDED` → `"종료된 쿠폰입니다."`(셋 다 `refetch: true`), 그 외 `resolveError` 메시지·`refetch: false`
  - `parseCouponTab(sp): "claimable" | "mine"`(기본 `claimable`), `parseCouponStatus(sp): "AVAILABLE" | "USED" | "EXPIRED"`(기본 `AVAILABLE`, 모르는 값은 기본)

- [ ] **Step 1: 실패하는 테스트 — `claim-error.test.ts`**(위 네 경우) **와 탭·상태 파싱 테스트**(모르는 값 → 기본)
- [ ] **Step 2: 실패 확인 → 구현 → 통과.**
- [ ] **Step 3: 화면.** 먼저 `MyPageShell`의 모양을 확인된 시안 `user-mypage.html`에 맞춘다. `CouponCard`(이름, `discountLabel`, `conditionLabel`, 발급 주체·종류 표시, 기간, 상품 하나 대상이면 `targetProductName ?? "상품 #" + targetProductId`). `ClaimableCouponList`: 받기 버튼은 `useIdempotentAction` — 성공 토스트 `"쿠폰을 받았습니다. {formatMonthDay(expiresAt)}까지 사용할 수 있습니다."`, 정책 목록(`coupons.policiesRoot`)·내 쿠폰(`coupons.mineRoot`) 무효화. 실패는 `claimOutcome` → 토스트(+`refetch`면 정책 목록 무효화). `claimed`면 "받음" 비활성. 받는 중인 카드의 버튼은 비활성.
- [ ] **Step 4: 상품 상세.** `ProductCouponSection`: 비로그인이면 API를 부르지 않고 "로그인하고 쿠폰 받기" 링크(`/login?next=<현재 경로>`, 기존 `safeNext` 규칙). 로그인이면 목록, 0개면 영역을 그리지 않는다. 시안의 기준 개수를 넘으면 "쿠폰 모두 보기" 창.
- [ ] **Step 5: `/my/coupons`.** 탭 `?tab=`, 내 쿠폰 필터 `?status=`·`?page=`. 내 쿠폰 카드: `StatusChip`, 만료일, `isExpiringSoon`이면 "곧 만료", `USED`면 사용일 + "주문 보기"(`/my/orders/{usedOrderId}`). 안내 한 줄 "사용할 수 있는 쿠폰은 주문서에서 자동으로 보입니다". 빈 상태·오류 상태.
- [ ] **Step 6: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 쿠폰 받기와 내 쿠폰 화면 추가`

---

### Task 8: 주문서의 쿠폰·포인트, 0원 결제, 주문 상세·취소 안내

스펙 §7. 시안 `design/user-checkout-benefit.html`.

**Files:**
- Create: `features/checkout/{benefit-selection.ts,points-input.ts,quote-recovery.ts}`(+테스트), `features/checkout/components/{BenefitSection,CouponPickerDialog,PointsField}.tsx`
- Create: `features/points/api.ts`(`usePointSummary`만 — Task 9가 이어서 채운다)
- Modify: `features/checkout/{api.ts,checkout-guard.ts}`(+테스트), `features/checkout/components/{CheckoutPage,CheckoutSummary,OrderItems}.tsx`, `features/payment/pay-state.ts`(+테스트), `features/orders/api.ts`(취소 무효화), `features/orders/components/{CancelItemDialog,OrderDetailPage,PaymentSummaryCard}.tsx`(+테스트)

**Interfaces:**
- Consumes: Task 2의 `QuoteCoupon`·`availableCoupons`·`cartCoupons`; Task 6의 쿼리 키·`discountLabel`; `points` 요약은 이 태스크가 `features/points/api.ts`의 `usePointSummary()`를 먼저 만든다(Task 9가 이어서 쓴다)
- Produces:
  - `benefit-selection.ts`:
    ```ts
    export type BenefitSelection = { productCoupons: Record<number, number>; cartCouponId: number | null; points: number }; // skuId → couponId
    export const EMPTY_SELECTION: BenefitSelection;
    export function selectProductCoupon(sel: BenefitSelection, skuId: number, couponId: number | null): BenefitSelection; // 같은 쿠폰이 다른 skuId에 있으면 거기서 뺀다
    export function selectCartCoupon(sel: BenefitSelection, couponId: number | null): BenefitSelection;
    export function setPoints(sel: BenefitSelection, points: number): BenefitSelection;
    export function clearCoupons(sel: BenefitSelection): BenefitSelection; // 포인트는 유지
    export function isSameSelection(a: BenefitSelection, b: BenefitSelection): boolean;
    export function toBenefitBody(sel: BenefitSelection): { productCoupons: { skuId: number; couponId: number }[]; cartCouponId: number | null; pointsToUse: number }; // productCoupons는 skuId 오름차순(쿼리 키·본문 안정)
    export function couponAppliedElsewhere(sel: BenefitSelection, skuId: number, couponId: number): boolean;
    ```
  - `points-input.ts`: `validatePoints(raw: string, maxUsable: number): { ok: true; points: number } | { ok: false; reason: "NOT_NUMBER" | "NOT_UNIT" | "OVER_MAX" }`(빈 문자열은 `{ ok: true, points: 0 }`; `/^\d+$/`만 숫자) · `pointsMessage(reason, maxUsable): string` — `"숫자만 입력해 주세요"`, `"10P 단위로 입력해 주세요"`, `"최대 {n}P까지 사용할 수 있습니다"` · `pointsDisabledReason(balance: number, maxUsable: number): string | null` — `maxUsable === 0`일 때 잔액이 0이면 `"보유 포인트가 없습니다"`, 아니면 `"이 주문에는 포인트를 사용할 수 없습니다 (1,000P 이상 보유 시, 상품 금액의 30%까지)"`
  - `quote-recovery.ts`: `recoverSelection(code: string, current: BenefitSelection, lastGood: BenefitSelection): { next: BenefitSelection; notice: string } | null` — 포인트 코드(`POINT_EXCEEDS_LIMIT`·`POINT_INSUFFICIENT`·`POINT_INVALID_AMOUNT`)이고 `current.points > 0` → `setPoints(current, 0)`, notice `"쿠폰이 바뀌어 포인트 사용을 초기화했습니다."`; 쿠폰 코드(`COUPON_MIN_AMOUNT_NOT_MET`·`COUPON_NOT_APPLICABLE`·`COUPON_NOT_AVAILABLE`·`COUPON_NOT_FOUND`) → `lastGood`가 `current`와 다르면 `lastGood`, 같으면 `EMPTY_SELECTION`(이미 `EMPTY_SELECTION`이면 null), notice `"쿠폰을 사용할 수 없어 선택을 되돌렸습니다."`; 그 밖의 코드와 더 물러설 곳이 없으면 null(오류 상태를 보인다)
  - `checkout/api.ts`: `useQuote(items, selection: BenefitSelection, enabled)` — 본문에 `toBenefitBody(selection)`, 쿼리 키 `queryKeys.checkout.quote(items, toBenefitBody(selection))`. `keepPreviousData` 유지(선택이 바뀌는 동안 `isPlaceholderData`가 true라 기존 `canPay`가 결제를 막는다)
  - `checkout-guard.ts`: 기존 `canPay` 입력에 `pointsSettled: boolean`을 더한다(포인트 입력 칸의 문자열이 검증을 통과하고 그 값이 `selection.points`와 같을 때만 true — 입력 지연 중이거나 `15`처럼 잘못된 값이 칸에 있으면 결제를 막는다) · `submitSnapshot(s: { quote: OrderQuote | undefined; quoteSelection: BenefitSelection | null; selection: BenefitSelection; payable: boolean }): { expectedTotalAmount: number; benefit: ReturnType<typeof toBenefitBody> } | null` — `payable`이 false거나 견적이 없거나 `quoteSelection`(그 견적을 만든 선택)이 현재 `selection`과 다르면 null, 아니면 **같은 견적에서** 금액과 선택을 꺼낸다 · `afterOrderHref(orderId, orderStatus, totalAmount)` — `PAID` → 완료, `totalAmount === 0`이고 `PAID` 아님 → `/my/orders/{id}`, 그 외 기존 결제 페이지 · `payButtonLabel(totalAmount: number): string` — 0이면 `"주문하기"`, 아니면 `"{formatKrw} 결제하기"` · `orderErrorAction(code: string): "CLEAR_COUPONS" | "CLEAR_POINTS" | null`
  - `pay-state.ts`: `canPay`는 `order.totalAmount > 0`도 요구한다

- [ ] **Step 1: 실패하는 테스트 — `benefit-selection.test.ts`**
  - `다른_상품에_고른_쿠폰을_고르면_이쪽으로_옮긴다` — `{1: 10}`에서 `selectProductCoupon(sel, 2, 10)` → `{2: 10}`
  - `null을_고르면_그_상품의_쿠폰이_빠진다`, `장바구니_쿠폰을_고르고_해제한다`, `clearCoupons는_포인트를_남긴다`
  - `toBenefitBody는_skuId_오름차순이다` — `{5: 1, 2: 3}` → `[{skuId:2,couponId:3},{skuId:5,couponId:1}]`
  - `입력_객체를_바꾸지_않는다`(불변), `isSameSelection`은 키 순서와 무관
- [ ] **Step 2: `points-input.test.ts`** (Review Focus 4) — `""` → 0; `"1000"`(max 5000) ok; `"15"` → `NOT_UNIT`; `"-10"`·`"1e3"`·`" 100"`·`"10.0"`·`"abc"` → `NOT_NUMBER`; `"5010"`(max 5000) → `OVER_MAX`; `"5000"` ok; `"0"` ok; `"  "`(공백만) → `NOT_NUMBER`; `pointsDisabledReason(0, 0)`, `(900, 0)`, `(5000, 0)`, `(5000, 1000) === null`
- [ ] **Step 3: `quote-recovery.test.ts`** (Review Focus 3)
  - `포인트_오류면_포인트만_0으로_되돌린다`(쿠폰 유지)
  - `쿠폰_오류면_직전_성공_선택으로_되돌린다`
  - `직전_선택과_같으면_전부_비운다`
  - `이미_빈_선택이면_null이다`(오류 상태로)
  - `포인트가_0인데_포인트_오류면_null이다`, `모르는_코드는_null이다`
- [ ] **Step 4: `checkout-guard.test.ts`·`pay-state.test.ts`에 추가** — `afterOrderHref(7, "PAID", 0)` 완료, `(7, "PENDING_PAYMENT", 0)` → `/my/orders/7`, `(7, "PENDING_PAYMENT", 49000)` 결제 페이지(기존 단언 유지); `payButtonLabel(0) === "주문하기"`, `payButtonLabel(49000) === "49,000원 결제하기"`; `orderErrorAction("COUPON_NOT_AVAILABLE") === "CLEAR_COUPONS"`(쿠폰 4코드), `("POINT_INSUFFICIENT") === "CLEAR_POINTS"`(포인트 3코드), `("ORDER_PRICE_CHANGED") === null`; `pay-state`의 `canPay`는 `totalAmount: 0`인 결제 대기 주문에 false; 주문서 `canPay`는 `pointsSettled: false`면 false; **(Review Focus 1)** `submitSnapshot`: 견적의 선택과 현재 선택이 다르면 null, `payable=false`면 null, 같으면 `expectedTotalAmount === quote.totalAmount`이고 `benefit`이 `toBenefitBody(quoteSelection)`과 같다
- [ ] **Step 5: 실패 확인 → 순수 함수 구현 → 통과.**
- [ ] **Step 6: 화면 — 선택과 견적.** `CheckoutBody`가 `selection`(useState)과 `lastGoodRef`(견적이 placeholder가 아닌 성공일 때 그 견적을 만든 선택으로 갱신)를 갖는다. `useQuote(items, selection, !orderCreated)`. 견적 오류 시 effect: `recoverSelection(code, selection, lastGoodRef.current)` → 있으면 `setSelection(next)` + 토스트(notice, 쿠폰 오류는 서버 메시지를 함께), 없으면 기존 오류 상태. **같은 오류 객체에 대해 한 번만** 복구를 시도한다(무한 반복 방지 — 오류 참조를 ref에 기억). 복구할 때마다 `queryKeys.checkout.quotes`를 무효화한다(캐시에 남은 옛 견적이 죽은 쿠폰을 쓸 수 있다고 보이지 않게). 복구 중에는 화면을 오류 상태로 바꾸지 말고 마지막 정상 견적을 그대로 둔 채 결제만 막는다(`recoverSelection`이 null일 때만 오류 상태). 기존 `ORDER_PRICE_CHANGED` 처리의 견적 무효화도 `queryKeys.checkout.quotes`로 바꾼다.
- [ ] **Step 7: 화면 — 영역.** `OrderItems`의 각 줄에 `availableCoupons.length > 0`이면 "쿠폰 선택 (n장 사용 가능)"/적용 중이면 쿠폰 이름과 `−{productCouponDiscount}`(견적의 그 줄 값) + "변경". `BenefitSection`: 장바구니 쿠폰 줄(`cartCoupons`가 비면 "사용 가능한 쿠폰이 없습니다"), `PointsField`. `CouponPickerDialog`(라디오 목록): 쓸 수 있는 쿠폰은 `discountLabel`·`conditionLabel`·만료일·`−{expectedDiscount}`; 쓸 수 없는 쿠폰은 흐리게 + 이유(`MIN_AMOUNT_NOT_MET` → `conditionLabel` 문구 + " 사용 가능", `NOTHING_TO_DISCOUNT` → "할인할 금액이 없습니다"), 선택 불가; `couponAppliedElsewhere`면 "다른 상품에 적용됨" 표시(고를 수 있다); 맨 위 "적용 안 함". `PointsField`: `usePointSummary()` 잔액 "보유 {n}P", 입력은 로컬 문자열 → `validatePoints(raw, quote.maxUsablePoints)` → ok면 지연 뒤 `setPoints`, 아니면 칸 아래 `pointsMessage`(견적 요청 없음), "최대 사용"은 `quote.maxUsablePoints`를 넣는다, `pointsDisabledReason`이 있으면 입력·버튼 비활성 + 이유. 견적이 복구로 포인트를 0으로 되돌리면 입력 칸도 비운다.
- [ ] **Step 8: 화면 — 요약과 제출.** `CheckoutSummary`: 상품 금액, 상품 쿠폰 할인, 장바구니 쿠폰 할인, 포인트 사용(셋은 0이 아닐 때만, 음수 표기), 배송비, 결제 금액 — 전부 견적 값. 버튼 문구 `payButtonLabel(quote.totalAmount)`. 제출 본문: `items`, `shippingAddress`, 그리고 `submitSnapshot(...)`의 `expectedTotalAmount`·`benefit`(Review Focus 1 — null이면 기존 `QuoteNotReadyError` 경로). 기존 `shownRef`가 견적·`payable`·그 견적의 선택을 **같은 렌더에서** 함께 담게 한다. `PointsField`는 칸의 문자열 상태(`pointsSettled`)를 위로 알린다. 성공: `afterOrderHref(orderId, orderStatus, totalAmount)`로 `router.replace`, 무효화에 `coupons.mineRoot`·`points.root` 추가. 오류: `orderErrorAction` → `CLEAR_COUPONS`면 `clearCoupons` + 토스트 "쿠폰을 사용할 수 없습니다. 다시 선택해 주세요.", `CLEAR_POINTS`면 `setPoints(…, 0)` + `points.root` 무효화 + 서버 메시지 토스트(두 경우 모두 `checkout.quotes` 무효화); `ORDER_PRICE_CHANGED`는 F2 동작 유지(선택 유지). 자동 재제출 없음.
- [ ] **Step 9: 주문 상세·취소 안내**(스펙 §7-5). `PaymentSummaryCard`: 상품 쿠폰 할인, 장바구니 쿠폰 할인, 포인트 사용, 되돌려받은 포인트 줄(0이 아닐 때만, 시안 순서). 공용 `ItemCancelDialog`는 고치지 않는다. 이미 있는 `notice` prop을 채우는 구매자용 래퍼 `CancelItemDialog`에 문구를 더한다(`OrderDetailPage`가 항목의 `pointsAllocated`·`productCouponId`와 주문의 `cartCouponId`를 넘긴다). 문구는 순수 함수 `benefitNotices({ pointsAllocated, hasProductCoupon, hasCartCoupon }): string[]`로 만든다: `pointsAllocated > 0` → "사용한 포인트 {n}P가 함께 돌아옵니다", `hasProductCoupon` → "사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다", `hasCartCoupon` → "장바구니 쿠폰은 주문 전체를 취소해야 돌아옵니다". 취소 성공 무효화(`features/orders/api.ts`)에 `coupons.mineRoot`·`points.root`. 테스트: `benefitNotices`의 세 문구 노출 조건, 요약 줄의 0 숨김.
- [ ] **Step 10: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 주문서에 쿠폰·포인트 사용과 0원 결제 추가`

---

### Task 9: 포인트 화면과 구매 확정 안내

스펙 §8-4, §8-5(구매 확정 문구).

**Files:**
- Create: `features/points/{transaction-text.ts}`(+테스트), `features/points/components/*`, `app/(store)/my/(menu)/points/page.tsx`
- Modify: `features/points/api.ts`(Task 8이 만든 파일에 내역 조회 추가), `features/orders/components/ConfirmPurchaseButton.tsx`(+테스트)

**Interfaces:**
- Produces: `usePointTransactions(page: number)` · `transactionLabel(type): string` — `EARN_PURCHASE` "구매 확정 적립", `EARN_REVIEW` "후기 작성 적립", `USE` "주문 사용", `REFUND` "주문 취소 환급", `EXPIRE` "기간 만료", 모르는 값은 그대로 · `transactionOrderHref(t: { refType: string; refId: number }): string | null` — `ORDER`만 `/my/orders/{refId}` · `signedPoints(amount: number): string` — `214` → `"+214P"`, `-10000` → `"−10,000P"` · `PointTransactionTable`(관리자 화면이 재사용: prop `rows`, `showOrderLink: boolean`)

- [ ] **Step 1: 실패하는 테스트 — `transaction-text.test.ts`**(위 값 전부, `ORDER_ITEM`·`PURCHASE_CONFIRM`은 링크 null, `signedPoints(0) === "0P"`)
- [ ] **Step 2: 구현 → 화면.** `/my/points`: `PointSummaryCard`(잔액, `expiringWithin30Days > 0`일 때만 "30일 안에 만료 예정 {n}P", 사용 안내 문구), 내역 표(일시 `formatDateTime`, 내용, 변동 — 양수·음수 색 구분, 잔액), `?page=`, 빈 상태 "포인트 내역이 없습니다".
- [ ] **Step 3: 구매 확정.** `ConfirmPurchaseButton`의 확인 창에 "구매 확정 시 결제 금액의 1%가 포인트로 적립됩니다" 추가(숫자 계산 없음). 성공 토스트에 "포인트 내역 보기" 링크(`/my/points`), 성공 무효화에 `points.root`. 컴포넌트 테스트에 문구 단언 추가.
- [ ] **Step 4: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 포인트 내역 화면과 구매 확정 적립 안내 추가`

---

### Task 10: 후기 (작성·수정·삭제, 주문 상세 상태, 상품 상세 후기)

스펙 §8-2, §8-5, §8-6. 시안 `design/user-review.html`, `user-product-detail-benefit.html`.

**Files:**
- Create: `features/reviews/{api.ts,schemas.ts,review-state.ts,image-rules.ts}`(+테스트), `features/reviews/components/*`, `app/(store)/my/(menu)/reviews/page.tsx`
- Modify: `features/orders/components/OrderItemRow.tsx`(구매자 상세에서만 후기 영역), `features/catalog/components/ProductDetailView.tsx`

**Interfaces:**
- Consumes: Task 3의 `OrderItemResult.review`, Task 4의 `ReviewResult.images`; `features/files/api.ts`의 `uploadImage`; `useIdempotentSubmit`
- Produces:
  - `schemas.ts`: `reviewFormSchema`(zod) — `rating` 1~5 정수 필수(미선택 메시지 "별점을 선택해 주세요"), `content`는 **앞뒤 공백을 뺀 길이** 10~1000(메시지 "10자 이상 입력해 주세요"), `imageFileIds` 최대 3개 · `contentLength(raw: string): number`(trim 뒤 `length` — 서버와 같은 기준)
  - `image-rules.ts`: `canAddImage(count: number): boolean`(3 미만). 형식·크기 검사는 새로 만들지 않고 `components/common/ImageUploader.tsx`의 `validateImageFile`·`MAX_IMAGE_BYTES`·`ACCEPTED_IMAGE_TYPES`를 쓴다(내보내지 않았으면 export만 추가)
  - `review-state.ts`: `reviewAction(review: OrderItemReview | null | undefined): { kind: "none" } | { kind: "write"; until: string } | { kind: "view"; reviewId: number } | { kind: "text"; text: string }` — `WRITABLE` → write(`writableUntil`), `WRITTEN` → view, `EXPIRED` → `"후기 작성 기한이 지났습니다"`, `DELETED` → `"삭제한 후기는 다시 작성할 수 없습니다"`, `NOT_ELIGIBLE`·null·undefined → none
  - `api.ts`: `useProductReviews(productId, page)`(인증 불필요), `useMyReviews(page)`, `writeReview(orderId, orderItemId, body, key)`, `editReview(reviewId, body)`, `deleteReview(reviewId)`
  - `rewardNotice(rewardPoints: number): string` — `"후기를 등록했습니다. {n}P가 적립되었습니다."`(0이면 `"후기를 등록했습니다."`)
  - `ReviewFormDialog` props: `mode: "create" | "edit"`, `product: { name; optionDisplayName?; thumbnailUrl? }`, `initial?: { rating; content; images: { fileId; url }[] }`, `onSubmitted()`

- [ ] **Step 1: 실패하는 테스트** (Review Focus 5)
  - `schemas.test.ts`: 공백 20자 실패; `"  아주 좋아요 추천합니다  "`(trim 12자) 통과; trim 9자 실패·10자 통과; 1000자 통과·1001자 실패; 이모지 포함 문자열은 `contentLength`가 JS `length` 기준임을 고정(서버가 Kotlin `String.length` — 같은 UTF-16 단위); 별점 0·6·2.5 실패; 사진 4장 실패
  - `image-rules.test.ts`: `canAddImage(2)` true, `(3)` false(형식·크기는 기존 `ImageUploader.test.tsx`가 이미 다룬다 — 없는 경계만 그 파일에 추가)
  - `ReviewFormDialog.test.tsx`(컴포넌트): `사진을_올리는_동안_제출_버튼이_막힌다`(업로드 Promise를 붙잡아 둔 상태에서 버튼 `disabled`, 끝나면 풀림), `이미_작성한_후기면_창을_닫고_주문_상세를_다시_읽는다`(`writeReview`가 409 `REVIEW_ALREADY_WRITTEN` → `onSubmitted`가 아닌 닫힘 콜백 호출 + `orders.detail` 무효화)
  - `review-state.test.ts`: 다섯 상태 + null + undefined
- [ ] **Step 2: 실패 확인 → 구현 → 통과.**
- [ ] **Step 3: 별점 컴포넌트.** `StarRating`(표시 전용, `aria-label="5점 만점에 {n}점"`), `StarRatingInput`(라디오 그룹 — `role="radiogroup"`, 방향키로 이동, 각 별 `aria-label="{n}점"`). 컴포넌트 테스트: 방향키로 값이 바뀐다, 선택 값의 `aria-checked`.
- [ ] **Step 4: `ReviewFormDialog`.** 사진: `ReviewImagePicker` — 파일 선택 시 `validateImageFile`로 먼저 거르고(안내 "jpg, png, webp만 올릴 수 있습니다" / "5MB 이하만 올릴 수 있습니다") `uploadImage`; 올리는 동안 제출 버튼 비활성과 "사진을 올리는 중입니다"; 미리보기와 삭제 버튼(`aria-label="사진 삭제"`); 3장이면 추가 버튼 숨김. 글자 수 표시 `{contentLength}/1000`. 적립 안내(작성: "글 후기 100P, 사진 후기 500P. 작성 후 사진을 추가해도 추가 적립은 없습니다" / 수정: "수정해도 추가 적립은 없습니다").
  - 작성: `useIdempotentSubmit` → `writeReview`. 성공 → `rewardNotice(rewardPoints)` 토스트, 무효화 `orders.detail(orderId)`·`reviews.mineRoot`·`points.root`·`reviews.productRoot(productId)`·`catalog.product(productId)`. 오류: `REVIEW_ALREADY_WRITTEN`·`REVIEW_NOT_ELIGIBLE` → 창 닫기 + 서버 메시지 토스트 + `orders.detail` 무효화; `REVIEW_INVALID`·`INVALID_REQUEST`·`FILE_NOT_OWNED` → 폼 상단.
  - 수정: `editReview`(처리 중 재제출 막기), 기존 사진은 `initial.images`의 `fileId`를 유지. 성공 → "후기를 수정했습니다", 무효화 `reviews.mineRoot`·`reviews.productRoot`·`catalog.product`.
- [ ] **Step 5: 주문 상세.** `OrderItemReviewAction`(`reviewAction` 결과로 그림): write → "후기 작성" 버튼 + `"{formatDateTimeMinute(until)}까지 작성 가능"`; view → "내 후기 보기"(`/my/reviews`); text → 문구. 구매자 주문 상세의 `OrderItemRow`에만 붙인다(판매자·관리자 화면이 같은 컴포넌트를 쓰면 prop으로 끈다).
- [ ] **Step 6: `/my/reviews`.** `ReviewCard`(상품 링크, 별점, 본문, 사진, 작성일 — `modifiedAt !== createdAt`이면 "수정됨", `rewardPoints`, 판매자 답글), "수정"(`ReviewFormDialog` edit), "삭제"(`ConfirmDialog` — "삭제하면 이 상품에 후기를 다시 쓸 수 없고, 적립된 포인트는 회수되지 않습니다" → `deleteReview`; `REVIEW_NOT_FOUND`는 조용히 목록 무효화). 삭제 성공 무효화: `reviews.mineRoot`·`reviews.productRoot`·`catalog.product`·`orders.root`. 빈 상태 + 주문 내역 링크. `?page=`.
- [ ] **Step 7: 상품 상세 후기 영역.** `ProductReviewSection`: 요약(`reviewCount === 0`이면 "아직 후기가 없습니다", 아니면 `averageRating`(서버 값 그대로 한 자리)·`StarRating`·"후기 {n}개"), 목록(`ReviewCard`의 읽기 전용 형태 — `writerName`, 작성일, 사진은 누르면 크게 보기, 답글 상자), 페이지는 `?reviewPage=`(`router.replace(..., { scroll: false })`). 로딩·오류 상태는 영역 안에서만.
- [ ] **Step 8: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 후기 작성·수정·삭제와 상품 후기 영역 추가`

---

### Task 11: 좋아요 목록

스펙 §8-7.

**Files:**
- Create: `features/likes/{api.ts,components/MyLikesPage.tsx}`, `app/(store)/my/(menu)/likes/page.tsx`
- Modify: `features/catalog/components/LikeButton.tsx`(+테스트), `features/catalog/api.ts`

**Interfaces:**
- Produces: `useMyLikes(page: number)` · `isOnSale(productStatus: string): boolean`(`ON_SALE`만 true — `features/likes/api.ts` 옆 순수 함수, 테스트 포함)

- [ ] **Step 1: 테스트 — `isOnSale`**(`ON_SALE` true; `PAUSED`·`ENDED`·`PENDING_REVIEW`·`REJECTED`·모르는 값 false)
- [ ] **Step 2: 화면.** F1 `ProductCard`의 모양을 따르되 `LikedProduct`에 없는 필드(정가·할인율·평점)는 그리지 않는다 — `ProductCard`가 선택 prop으로 받을 수 있으면 재사용, 아니면 같은 클래스의 얇은 카드. 판매 중이 아니면 흐리게 + "판매 종료". 하트는 F1 `LikeButton` — `likeCount` prop을 선택으로 바꾸고(없으면 숫자를 그리지 않는다. 기존 호출부는 그대로), `useToggleLike`의 성공 무효화에 `likes.root`를 더한다. `LikeButton.test.tsx`에 `likeCount가_없으면_숫자를_그리지_않는다` 추가. 기존 `LikeButton`의 색 클래스는 건드리지 않는다. 빈 상태 "좋아요한 상품이 없습니다" + 상품 목록 링크. `?page=`.
- [ ] **Step 3: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 좋아요 목록 화면 추가`

---

### Task 12: 판매자 쿠폰 관리·후기 관리

스펙 §9. 시안 `design/seller-benefit.html`.

**Files:**
- Create: `features/coupon-policies/{schema.ts,policy-status.ts}`(+테스트), `features/coupon-policies/components/{PolicyTable,PolicyFormDialog}.tsx`, `features/seller/coupons/*`, `features/seller/reviews/*`, `app/seller/{coupons,reviews}/page.tsx`

**Interfaces:**
- Consumes: Task 6의 `percentToBp`·`fromDateTimeInput`·`hasPassedKst`·`discountLabel`; Task 4의 `targetProductName`
- Produces:
  - `policy-status.ts`: `policyDisplayStatus(p: { status: "ACTIVE" | "ENDED"; validUntil: string }, nowMs: number): "ACTIVE" | "ENDED" | "PERIOD_OVER"` · `quantityLabel(issued: number, total: number | null | undefined): string` — `"12 / 100"`, 무제한 `"12 / 제한 없음"` · `targetLabel(p): string` — `ALL_PRODUCTS` "전 상품", `SELLER` "내 상품 전체", `PRODUCT` → `targetProductName ?? "상품 #{id}"`, `NONE` "주문 전체"
  - `schema.ts`: `policyFormSchema(scope: "seller" | "admin")`(zod) → `toPolicyRequest(values, scope): CreateCouponPolicyRequest`
    - 공통: `name` trim 1~50; `discountType`; 정액 `discountValue` 양의 정수; 정률 `percent` 1~100 정수 → `discountValue = percentToBp(percent)`, `maxDiscount` 필수 양의 정수(정액이면 보내지 않는다); `minOrderAmount`·`totalQuantity`·`validityDays`는 비우면 `null`, 있으면 양의 정수; `validFrom`·`validUntil`은 `fromDateTimeInput` 통과 + `validUntil > validFrom`(문자열 비교 — 같은 형식의 KST 문자열)
    - seller: `couponType` 항상 `PRODUCT`; `targetType` `SELLER` | `PRODUCT`; `PRODUCT`면 `targetProductId` 필수
    - admin: `couponType` `PRODUCT` | `CART`; `CART`면 `targetType = NONE`·`targetProductId = null`; `PRODUCT`면 `ALL_PRODUCTS` | `PRODUCT`(+`targetProductId` 필수 양의 정수)
  - `PolicyTable` props: `rows: CouponPolicyResult[]`, `onEnd(policyId)`, `endingId: number | null` · `PolicyFormDialog` props: `scope`, `productPicker?: ReactNode`(판매자만), `onSubmit(body, key)`, `notice: string[]`
  - `features/seller/reviews/reply-schema.ts`: `replySchema` — trim 1~500

- [ ] **Step 1: 실패하는 테스트**
  - `schema.test.ts`: 정률인데 최대 할인 없음 실패; 정률 `percent=10` → 요청 `discountValue === 1000`; `percent=0`·`101`·`10.5` 실패; 정액은 `maxDiscount`가 요청에 없다; 종료 ≤ 시작 실패; 빈 선택 값은 `null`; seller `PRODUCT`에 상품 없음 실패; seller 요청의 `couponType === "PRODUCT"`; admin `CART` → `targetType === "NONE"`; admin `PRODUCT`+`ALL_PRODUCTS`; 이름 51자 실패
  - `policy-status.test.ts`: `ACTIVE`+기간 지남 → `PERIOD_OVER`; `ENDED`는 기간과 무관하게 `ENDED`; `quantityLabel`·`targetLabel` 각 경우(이름 없으면 `"상품 #123"`)
  - `reply-schema.test.ts`: 공백만 실패, 500 통과, 501 실패
- [ ] **Step 2: 실패 확인 → 구현 → 통과.**
- [ ] **Step 3: 쿠폰 관리 화면.** `GET /seller/coupons/policies?page=`. 표: 이름, `targetLabel`, `discountLabel`, 최소 주문 금액, `quantityLabel`, 기간, `StatusChip(policyDisplayStatus)`, "종료" 버튼(`ACTIVE`일 때만 — `ConfirmDialog` "종료하면 새로 받을 수 없습니다. 이미 받은 고객은 계속 사용할 수 있습니다." → `PATCH …/end`). "쿠폰 등록" → `PolicyFormDialog`(`useIdempotentSubmit` → `POST /seller/coupons/policies`; `COUPON_POLICY_INVALID` → 폼 상단). 상품 선택은 기존 판매자 상품 목록 API(`features/seller/products`)에서 판매 중인 내 상품을 고르는 선택 상자. 안내 문구 두 줄(스펙 §9-1). 성공 → `sellerCoupons.lists` 무효화.
- [ ] **Step 4: 후기 관리 화면.** `GET /seller/reviews?page=`. 줄: 상품, `StarRating`, `writerName`, 본문, 사진, 작성일, 답글 없으면 `StatusChip("UNANSWERED")`. 펴면 `ReplyEditor`(기존 답글이 있으면 채워서 "답글 수정", 없으면 "답글 등록") → `PATCH /seller/reviews/{id}/reply`(처리 중 재제출 막기) → `sellerReviews.lists` 무효화. `REVIEW_NOT_FOUND`(구매자가 그사이 삭제)는 서버 메시지 토스트 + 목록 무효화.
- [ ] **Step 5: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 판매자 쿠폰 관리와 후기 관리 화면 추가`

---

### Task 13: 관리자 쿠폰 관리·회원 포인트 조회

스펙 §10.

**Files:**
- Create: `features/admin/coupons/*`, `features/admin/points/*`, `app/admin/coupons/page.tsx`, `app/admin/members/points/page.tsx`

**Interfaces:**
- Consumes: Task 12의 `PolicyTable`·`PolicyFormDialog`·`policyFormSchema("admin")`; Task 9의 `PointTransactionTable`; `parseId`
- Produces: `parseMemberIdParam(sp: URLSearchParams): number | null`(`parseId` 사용)

- [ ] **Step 1: 테스트 — `parseMemberIdParam`**(`"12"` → 12; `"0"`·`"-1"`·`"abc"`·`"1.5"`·없음 → null)
- [ ] **Step 2: 쿠폰 관리.** 판매자 화면과 같은 구성, API만 `/admin/coupons/policies`. 양식 `scope="admin"`(종류 선택, 상품 ID 직접 입력), 안내 "플랫폼 쿠폰 할인은 플랫폼이 부담합니다." + 수정 불가 안내. 표에 종류(상품 쿠폰/장바구니 쿠폰) 열 추가(`PolicyTable`의 선택 prop).
- [ ] **Step 3: 회원 포인트.** 회원 ID 입력 폼 → `?memberId=`로 `router.push`. 값이 있으면 `GET /admin/members/{memberId}/points?page=`: 잔액·만료 예정, `PointTransactionTable`(`showOrderLink=false`). 잘못된 ID는 조회하지 않고 "회원 번호를 숫자로 입력해 주세요". 내역이 비면 "내역이 없습니다. 없는 회원 번호도 이렇게 보입니다."
- [ ] **Step 4: `pnpm lint && pnpm typecheck && pnpm test` → Commit** `feat(frontend): 관리자 쿠폰 관리와 회원 포인트 조회 화면 추가`

---

### Task 14: E2E

스펙 §13.

**Files:**
- Create: `frontend/e2e/c3-coupon-checkout.spec.ts`, `c3-points.spec.ts`, `c3-review.spec.ts`
- Modify: `frontend/e2e/fixtures/api.ts`, 필요하면 기존 spec(헤더 링크 문구)

**Interfaces:**
- Produces(픽스처, 전부 API 직접 호출): `createSellerCouponPolicy(sellerToken, body)`, `createAdminCouponPolicy(adminToken, body)`, `claimCoupon(buyerToken, policyId)`, 기존 `placePaidOrderApi`(주문·모의 결제) 재사용, `shipDeliverConfirm(sellerToken, buyerToken, order)`(발송 → `PATCH /seller/orders/{id}/deliver` → 구매 확정), `writeReview(buyerToken, orderId, orderItemId, { withPhoto })`, `uploadTestImage(token)`(기존 `e2e/assets` 이미지), `getPointBalance(buyerToken)`. 관리자 토큰은 기존 `admin.spec.ts`가 쓰는 방식 그대로.

- [ ] **Step 1: 픽스처를 추가하고, 기존 E2E를 먼저 돌려 라우트 이동(Task 6) 뒤에도 전부 통과하는지 확인한다.** 실행 방법은 `frontend/scripts/e2e-server.sh`와 `playwright.config.ts`의 주석을 따른다(백엔드 `local,demo`, Mock PG, 데모가 아닌 빌드, `:3100`). DB는 임시 스키마를 쓴다(기존 `kairos` DB의 데모 계정 비밀번호를 알 수 없다 — 세션 전용 환경 변수로 띄우고 끝나면 스키마를 지운다).
- [ ] **Step 2: `c3-coupon-checkout.spec.ts`**(금액은 전부 `toHaveText` 정확 비교). 숫자 고정: 판매자 B 상품 b1 30,000원, 판매자 C 상품 c1 20,000원(수량 2). 쿠폰: 플랫폼 상품 쿠폰 정액 2,000(전 상품), 판매자 C 상품 쿠폰 10% 최대 5,000, 플랫폼 장바구니 쿠폰 10% 최대 5,000 최소 50,000, 플랫폼 상품 쿠폰 정액 1,000 최소 35,000(전 상품 — b1에는 쓸 수 없음). 전부 수량 무제한.
  - 상품 상세에서 받기: 버튼 연타 후 내 쿠폰에 한 장, 새로고침하면 "받음"
  - 주문서: b1 쿠폰 창에서 최소 35,000 쿠폰이 비활성 + "35,000원 이상 구매 시 사용 가능"
  - b1에 2,000 쿠폰, c1에 판매자 쿠폰, 장바구니 쿠폰 선택 → 상품 쿠폰 할인 `−6,000원`, 장바구니 쿠폰 할인 `−5,000원`, 결제 금액 `59,000원`(무료배송)
  - c1 창에서 b1에 쓴 2,000 쿠폰이 "다른 상품에 적용됨"
  - 결제 → 주문 상세 결제 요약에 같은 할인 → `/my/coupons?tab=mine&status=USED`에 세 장
  - c1 항목 취소 창의 쿠폰 안내 문구 → 취소 → 내 쿠폰 "사용 가능"에 판매자 C 쿠폰이 돌아옴
  - **0원 결제**: 판매자 D 상품 60,000원 1개 + 플랫폼 상품 쿠폰 정액 60,000(상품 하나 대상) → 버튼 "주문하기" → 결제창(모의 결제 창)이 뜨지 않고 주문 완료 화면, 주문 상세 결제 금액 `0원`
- [ ] **Step 3: `c3-points.spec.ts`.** 준비(API): 구매자가 60,000원 주문 → 확정(600P) → 사진 후기(500P) → 잔액 1,100P. 화면: 30,000원 상품 주문서에서 "보유 1,100P", "최대 사용" → 입력 칸 `1100`, 포인트 사용 `−1,100원`, 결제 금액이 서버 견적과 일치(배송비 3,000 포함 `31,900원`) → 입력을 `15`로 바꾸면 "10P 단위로 입력해 주세요"가 뜨고 견적 요청이 나가지 않고(요청 수를 세는 라우트 관찰로 확인) **결제하기 버튼이 비활성**이 됨 → `1100`으로 되돌려 결제 → `/my/points`에 "주문 사용 −1,100P" → 항목 취소 → "주문 취소 환급 +1,100P", 잔액 1,100P.
- [ ] **Step 4: `c3-review.spec.ts`.** 주문 → (API)발송·배송 완료 → 화면에서 구매 확정(확인 창에 1% 안내 문구) → "후기 작성" + "까지 작성 가능" → 별점 5, 본문 9자면 제출 불가·10자면 가능, 사진 1장 → "500P가 적립되었습니다" → 주문 상세가 "내 후기 보기" → 상품 상세 "후기 1개"와 평균 `5.0` → 판매자 로그인, 후기 관리에서 "미답변" → 답글 등록 → 상품 상세에 답글 → 구매자 `/my/reviews`에서 수정(본문만 바꿈, 사진 1장 유지 확인) → 삭제 → 주문 상세 "삭제한 후기는 다시 작성할 수 없습니다", 상품 상세 "아직 후기가 없습니다". 이어서 좋아요: 상품 상세에서 좋아요 → `/my/likes`에 카드 → 카드 하트로 취소 → 빈 상태.
- [ ] **Step 5: 양식 경로 확인** — `c3-coupon-checkout.spec.ts`의 판매자 C 쿠폰(10% 최대 5,000)만은 API가 아니라 **판매자 쿠폰 관리 화면의 등록 양식**으로 만든다(%→bp 변환이 실제로 맞는지 화면에서 한 번 검증: 표에 "10% 할인 (최대 5,000원)").
- [ ] **Step 6: 전체 E2E(F1·F2·F3) 통과 → Commit** `test(frontend): C3 혜택·후기 E2E 3개 추가`. 임시 스키마 삭제, 서버·Gradle 정리.

---

### Task 15: 문서와 최종 검증

스펙 §14.

**Files:**
- Modify: `CLAUDE.md`, `README.md`(필요하면)

- [ ] **Step 1: `CLAUDE.md`** — 문서 목록에 F3 스펙·계획 추가, "현재 단계" 갱신, 프론트엔드 절에 F3 규칙 추가: (1) 주문서는 견적이 유일한 진실 — 할인·한도·예상 할인을 프론트가 계산하지 않는다, (2) 서버 강제 멱등 요청 목록에 쿠폰 받기·쿠폰 정책 등록·후기 작성, (3) 프론트 계산 허용 목록에 포인트 10의 배수 검사와 %→bp, (4) 마이페이지 라우트 그룹 구조. 백엔드 절에 `CouponTerms.meetsMinimum`이 최소 금액 판정의 단일 위치임을 한 줄.
- [ ] **Step 2: 전체 검증** — `./gradlew test`, `cd frontend && pnpm check:api && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle`. 출력의 통과 수를 기록한다.
- [ ] **Step 3: 수동 점검**(스펙 §13) — 시안 5개와 실제 화면 대조, 데모 빌드에서 관리자(쿠폰 등록) → 구매자(받기·주문·확정·후기) → 판매자(답글) 흐름 한 번. 결과를 보고서에 적는다(하지 못한 항목은 "미실시"와 이유 — 오케스트레이터가 PR 본문에 옮긴다).
- [ ] **Step 4: Commit** `docs: F3 혜택·후기 화면 규칙과 현재 단계 갱신` → Gradle 데몬·미리보기 서버 정리.
