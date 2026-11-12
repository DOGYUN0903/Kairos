# Kairos

마켓플레이스 커머스(판매자가 상품을 올리고 구매자가 타임딜·일반 상품을 사는 서비스). 모놀리식에서 최적화를 끝까지 해 본 뒤, 남는 한계를 근거로 MSA로 전환하는 학습용 개인 프로젝트다.

- 설계 문서(근거와 상세 규칙): `docs/superpowers/specs/2026-11-08-kairos-design.md`
- 커머스 로드맵: `docs/superpowers/specs/2026-11-09-commerce-roadmap-design.md`
- C1 스펙: `docs/superpowers/specs/2026-11-09-c1-seller-catalog-design.md`
- C2 스펙: `docs/superpowers/specs/2026-11-09-c2-purchase-design.md`
- C3 스펙: `docs/superpowers/specs/2026-11-10-c3-benefits-reviews-design.md`
- C4 스펙: `docs/superpowers/specs/2026-11-10-c4-returns-settlement-design.md`
- F1 스펙: `docs/superpowers/specs/2026-11-11-frontend-f1-foundation-design.md`
- F2 스펙: `docs/superpowers/specs/2026-11-12-frontend-f2-purchase-design.md`
- F3 스펙: `docs/superpowers/specs/2026-11-12-frontend-f3-benefits-design.md`
- F4 스펙: `docs/superpowers/specs/2026-11-12-frontend-f4-returns-settlement-design.md`
- 현재 구현 계획: `docs/superpowers/plans/2026-11-12-frontend-f4-returns-settlement.md` (이전: `docs/superpowers/plans/2026-11-12-frontend-f3-benefits.md`, `docs/superpowers/plans/2026-11-12-frontend-f2-purchase.md`, `docs/superpowers/plans/2026-11-11-frontend-f1-foundation.md`, `docs/superpowers/plans/2026-11-10-c4-returns-settlement.md`, `docs/superpowers/plans/2026-11-10-c3-benefits-reviews.md`, `docs/superpowers/plans/2026-11-09-c2-purchase.md`, `docs/superpowers/plans/2026-11-09-c1-seller-catalog.md`)
- API 계약: `docs/api/openapi.yaml`
- 현재 단계: Phase 0~2, C1~C4, F1~F4(C1~C4 화면)는 완료. 다음은 **C5 운영 데이터**(커머스 로드맵, `docs/superpowers/specs/2026-11-09-commerce-roadmap-design.md`)

## 스택
Kotlin 2.3, Spring Boot 4.1, JDK 21, Gradle Kotlin DSL, MySQL 8.4, Redis 7.4, Flyway, QueryDSL 7.7 (OpenFeign fork, KSP), JUnit5, Testcontainers, ArchUnit.

## 명령어
- 테스트: `./gradlew test` (Docker 실행 필요, Testcontainers 사용)
- 뮤테이션 테스트: `./gradlew pitest` (돈 계산 순수 클래스 대상, `check`/`test`와 별개, 1분 이내). 리포트는 `build/reports/pitest/index.html`(XML: `mutations.xml`)
- 로컬 실행: `.env.example`을 `.env`로 복사해 값을 채운 뒤 `docker compose up -d`, `./gradlew bootRun`
- 모니터링: `docker compose up -d` 후 Grafana(localhost:3000, 대시보드 "Kairos 타임딜"), Prometheus(localhost:9090). 메트릭은 관리 포트 8081, 로그는 `logs/kairos.log`(traceId 포함).
- 데모 데이터: `SPRING_PROFILES_ACTIVE=local,demo` + `KAIROS_DEMO_PASSWORD` (demo 프로필에서만 `DemoDataInitializer`·`POST /api/v1/demo/reseed`가 있다. prod와 demo 동시 활성은 기동 실패). 정산 화면에 데이터가 보이려면 `KAIROS_SETTLEMENT_STARTDATE`를 지난주 월요일로 넘긴다(명령은 README). `DemoSettlementSeeder`가 지난주에 구매 확정된 판매자 주문 3건을 실제 서비스로 만들며 Mock PG일 때만 동작한다.
- 설정값(DB, Redis, JWT 비밀키, 관리자 계정, `TOSS_SECRET_KEY`, `SWEETTRACKER_API_KEY`)은 `.env`로만 주입한다. `.env`는 커밋하지 않고, 키를 추가하면 `.env.example`도 함께 고친다.

## 아키텍처: 4계층 DDD
모듈(= 바운디드 컨텍스트 = 미래의 서비스): `member`, `deal`, `order`, `cart`, `payment`, `seller`, `catalog`, `file`, `benefit`, `review`, `settlement`, `common`

`demo` 패키지는 바운디드 컨텍스트가 아니라 demo 프로필 전용 시더·엔드포인트이며 다른 모듈의 application만 호출한다(`ArchitectureTest` demoUsesOnlyApplication). 시계는 운영에서 시스템 시계 하나(`ClockConfig`, `@Profile("!demo")`)이고, demo 프로필에서만 `DemoShiftClock`(스레드별 오프셋)이 대신 들어가 정산 시더가 지난주 시각으로 주문을 만든다.

```
com.dogyun.kairos.{module}/
  presentation/    Controller, 요청/응답 DTO
  application/     Application Service, Command/Result DTO, 다른 모듈 호출용 인터페이스
  domain/          애그리거트, 값 객체, 도메인 서비스, Repository 인터페이스
  infrastructure/  JPA 엔티티, Repository 구현, 외부 클라이언트, 다른 모듈 호출 구현
```

- 의존 방향: `presentation → application → domain ← infrastructure`
- `domain`은 순수 Kotlin. Spring, JPA, Jackson을 import하지 않는다.
- 도메인 모델과 JPA 엔티티를 분리한다. JPA 엔티티는 `infrastructure`에만 둔다.
- 다른 모듈 호출: 호출하는 쪽 `application`에 인터페이스를 두고, 자기 `infrastructure`에서 상대 모듈의 `application` 서비스를 호출해 구현한다. 다른 모듈의 `domain`, `presentation`, `infrastructure`는 import하지 않는다. 모듈 간 DTO에 도메인 타입을 노출하지 않는다.
- 애그리거트끼리는 ID로만 참조한다. 상태 변경은 도메인 메서드로만 한다(public setter 금지).
- 위 규칙은 `ArchitectureTest`(ArchUnit)가 강제한다. 테스트를 우회하거나 규칙을 완화하지 않는다.

## JPA 규칙
- 모든 엔티티는 `BaseEntity`(`created_at`, `created_by`, `modified_at`, `modified_by`, `status`)를 상속한다.
- 예외: `idempotency_key` 테이블은 JPA 엔티티 없이 `JdbcTemplate`으로만 다룬다. 멱등성 필터 안에서 주문 트랜잭션과 무관하게 기록해야 해서 `BaseEntity` 규칙의 예외로 사용자가 승인했다.
- 예외: 읽기 전용 뷰 엔티티 `BrandView`/`SellerView`(`catalog.infrastructure`)는 `@Immutable`이며 `BaseEntity`를 상속하지 않는다(ADR 0002).
- `review`는 `review_image`를 `@OneToMany`로 가진다. 후기와 이미지는 한 애그리거트이고 이미지 순서·교체를 후기 안에서만 다루기 때문에 양방향 규칙의 예외로 허용한다.
- `settlement`는 `settlement_line`을 `@OneToMany`로 가진다. 정산과 라인은 생성 시점에 한 번 만들어지는 한 애그리거트이고 라인만 따로 바뀌지 않기 때문에 양방향 규칙의 예외로 허용한다.
- SKU는 소프트 딜리트 대신 `sku_status = DISCONTINUED`로 단종 처리한다(C1 스펙 13장).
- 소프트 딜리트만 쓴다(`status = DELETED`). 물리 삭제 금지. 엔티티에 `@SQLRestriction("status = 'ACTIVE'")`를 붙인다.
- 도메인 상태 컬럼은 `status`가 아니라 `order_status`, `seller_order_status`, `order_item_status`, `deal_status`, `payment_status`, `seller_status`, `product_status`, `sku_status`, `policy_status`, `coupon_status`, `return_status`, `settlement_status`처럼 이름을 짓는다. 좋아요는 상태 컬럼이 아니라 `liked` 플래그다.
- 연관관계는 같은 애그리거트 안에서만, `LAZY`, 기본 `@ManyToOne` 단방향. 양방향은 꼭 필요할 때만 쓰고 이유를 주석으로 남긴다. `@ManyToMany`, `@OneToOne` 금지.
- 동적 쿼리·복잡한 조회는 QueryDSL로 작성하고, `{module}.infrastructure`의 Repository 구현에서만 사용한다. 단순 조회는 Spring Data 메서드 쿼리를 써도 된다.
- 스키마는 Flyway로만 관리한다(`ddl-auto: validate`, `open-in-view: false`). 이미 적용된 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다.

## API 규칙
- **SDD**: API를 바꾸려면 `docs/api/openapi.yaml`을 먼저 고치고 구현한다. 스펙과 구현은 계약 테스트로 맞춘다.
- 성공 응답은 `ApiResponse(message, data, timestamp)`, 에러는 `ErrorResponse(code, message, timestamp)`, 목록은 `?page&size` + `PageResponse`.
- API 일시는 오프셋 없는 LocalDateTime(KST 기준, 예: 2026-12-09T10:00:00). 서버 기본 시간대는 Asia/Seoul로 고정한다.
- application 계층은 Spring `Page`가 아니라 `PageResult`를 반환한다.
- 장바구니 `availability`는 판매자 상태도 반영한다: 판매자가 주문을 받을 수 없으면 `PAUSED`(견적·주문과 같은 기준). 항목 취소 미리보기(`GET /orders/{orderId}/items/{orderItemId}/cancel-preview`)는 `RefundCalculator`를 읽기 전용으로 호출한다.
- 인증: 액세스 토큰 Bearer(30분), 리프레시 토큰은 본문과 httpOnly 쿠키 `kairos_refresh`(Path=/api/v1/auth, SameSite=Strict) 둘 다. 쿠키 기반 reissue·logout은 `X-Requested-With: kairos-web` 헤더를 요구한다. `GET /auth/me`가 역할·이름을 준다(프론트는 JWT를 해석하지 않는다).
- 에러 코드는 `ErrorCode` enum과 OpenAPI의 `ErrorResponse.code`가 일치해야 한다.

## 프론트엔드
위치 `frontend/`. Next.js 15(App Router) · React 19 · TypeScript strict · TanStack Query · react-hook-form + zod · Tailwind 3.4(shadcn/ui) · openapi-typescript/openapi-fetch. 데스크톱 전용(1280px 이상).
- 명령(`frontend/`에서): `pnpm dev`, `build`(check:api 포함), `lint`, `typecheck`, `test`(vitest), `e2e`(Playwright), `gen:api`, `check:api`, `check:bundle`. 포트: E2E 기본 `:3100`(Grafana가 `:3000`), 백엔드 `:8080`.
- 로컬 실행: 백엔드를 띄우고 `pnpm dev`. 프론트는 `BACKEND_URL`(기본 `http://localhost:8080`)로 `/api`를 rewrite 한다.
- OpenAPI가 바뀌면 `pnpm gen:api`로 `src/api/schema.d.ts`를 재생성해 **같은 커밋**에 넣는다(`check:api`가 build·CI를 막는다).
- API 클라이언트(`src/api/client.ts`): 액세스 토큰은 메모리에만 둔다(storage 금지). 리프레시는 httpOnly 쿠키 + `X-Requested-With: kairos-web`. 401이면 reissue를 한 번만(단일 비행) 시도하고 `/auth/*`는 절대 재시도하지 않는다. 에러 메시지는 `src/api/errors.ts` 표로만 만든다.
- 금액은 정수만. 계산·표시는 `src/lib/money.ts`로만 하고 나눗셈(`/`, `%`)은 거기서만 쓴다(ESLint가 막는다). 나눗셈·나머지가 필요하면 `money.ts`의 `floorDiv`/`mod`를 쓴다(시간 계산 포함).
- 주문서는 견적이 유일한 진실이다. 선택(상품 쿠폰 줄별·장바구니 쿠폰·포인트)과 그 선택으로 계산된 견적은 함께 다니고(`useQuote`는 `{ quote, selection }`을 돌려준다), 프론트는 할인·한도·예상 할인을 계산하지 않는다. 재견적 중이거나 포인트 입력이 확정되지 않았으면 결제를 막고, 견적은 캐시에서 주지 않는다(`staleTime: 0`).
- `Idempotency-Key`를 보내는 곳은 세 갈래다. 폼 제출(주문 생성, 항목 취소 — 구매자·판매자·관리자 공용 `ItemCancelDialog`, F1 생성 POST: 회원가입, 판매자 신청, 브랜드·상품 등록, 변경 요청, 관리자 카테고리 생성)은 `useIdempotentSubmit`, 폼 없는 요청(장바구니 담기)은 `useIdempotentAction`, 결제 승인은 `features/payment/confirm-key-store`의 `paymentKey`별 키로 마운트당 한 번만 보낸다(새로고침·뒤로 가기에도 같은 키). 발송 처리·가격 변경처럼 키 없이 이중 제출 가드로만 `useIdempotentSubmit`을 쓰는 곳도 있다. 상태 전이 엔드포인트(승인·반려·중지·해제, 좋아요, 순서 변경)는 키 없이 이중 제출 가드 + 서버 상태 검사(409)에 기댄다. 서버가 키를 강제하는 경로는 `IdempotencyProperties.paths`뿐이다(구매 흐름 경로, 쿠폰 받기, 쿠폰 정책 등록(판매자·관리자), 후기 작성, 반품 요청, 정산 생성, 정산 조정 등록 포함). `useIdempotentSubmit`의 선택 옵션 `fingerprint`로 폼 값 밖의 요청 내용(주문서의 쿠폰·포인트·기대 금액)이 바뀌면 새 키를 쓴다.
- 키 규칙(`src/api/idempotency.ts`의 `isAnswered`): 답이 없던 시도(네트워크 오류, 5xx, 409 `IDEMPOTENCY_IN_PROGRESS`)만 같은 키로 재시도하고, 서버가 답한 뒤(성공·4xx)의 다음 제출은 새 키를 쓴다. `submit`은 실패하면 던지고 오류 처리는 `onError`에서 한다.
- 결제 방식: `NEXT_PUBLIC_PG_PROVIDER`(`mock` 기본 | `toss`)는 백엔드 `KAIROS_PG_PROVIDER`와 반드시 같은 값이어야 한다. 프론트 `mock` + 백엔드 `toss`면 승인이 거절되고 주문이 취소된다. 프론트 `toss` + 백엔드 `mock`이면 Mock PG가 접두사 없는 키를 전부 승인하므로 토스에서 실제 승인 없이 주문이 결제 완료가 된다(로컬에서만 가능한 조합). `toss`는 "API 개별 연동" 클라이언트 키를 `NEXT_PUBLIC_TOSS_CLIENT_KEY`에 둔다. 시크릿 키(`TOSS_SECRET_KEY`)는 백엔드 `.env`에만 두고 프론트 파일·번들에 넣지 않는다. 모의 결제 실패 선택지는 `features/demo`에만 있어 데모 빌드에서만 보인다.
- 합계·배송비·환불액은 서버 값만 표시한다. 프론트가 계산해야 하면 `money.ts` 함수로만 한다. 허용된 계산은 포인트 10P 단위 검사(`mod`), %→bp 변환(`percentToBp`), 별 채움 비율(`ratingFillPercent`), 정산 조정 금액의 부호 붙이기(`signedAmount`)뿐이다.
- 반품 환불 미리보기(`GET …/return-preview`)는 예상값이며 화면에 "예상"으로 표시한다. 실제 환불액은 수거 확인 때 서버가 확정한다. 환불 없이 끝난 반품(거절·철회·중단)은 "환불 없음"으로 보인다.
- 수거 확인(환불을 일으키는 PATCH)은 `features/returns/useReceiveReturn.ts` 한 곳에서만 보낸다. 응답을 못 받아도 자동으로 다시 보내지 않고, 보낸 뒤에는 서버 상태가 바뀐 것을 확인할 때까지 버튼을 잠근다.
- 정산 금액(계산 내역·라인·합계·지급액)은 서버 값 그대로 표시하고 화면에서 합산하지 않는다. 음수는 `formatKrwMinus`/`formatSignedKrw`로 U+2212(−)를 붙인다.
- 날짜·기간 계산(지난주 월요일, 정산 기간의 마지막 날, 반품 기한 경과 등)은 `src/lib/datetime.ts`에서만 한다. 정산 `periodEnd`는 다음 주 월요일(미포함)이므로 화면에는 `periodLastDay`로 일요일을 보인다.
- 목록 화면은 한 가지 패턴을 쓴다: 다시 읽기가 실패하면 오류 안내 + 재시도를 보이되 같은 조건의 기존 줄은 유지하고, 조건이 바뀐 동안의 이전 데이터(`isPlaceholderData`)는 로딩으로 취급한다.
- 마이페이지 메뉴 라우트는 `(store)/my/(menu)` 라우트 그룹에 두고, 그 레이아웃이 Suspense → RequireAuth → MyPageShell을 적용한다. 결제·완료 페이지는 그룹 밖에 둔다. 후기의 "수정됨" 표시는 구매자 수정 시각이 없어 일부러 보이지 않는다.
- `sessionStorage`는 토큰이 아닌 값에만 쓴다(결제 승인 키 `confirm-key-store`, 주문번호→주문 ID `order-ref-store`). 액세스 토큰 금지 규칙은 그대로다.
- 로그인 `next` 파라미터는 `safeNext`(`features/auth/redirect.ts`)를 거친다(오픈 리다이렉트 방지). 가드는 클라이언트 레이아웃이며 `middleware`는 없다.
- 데모 기능은 `features/demo`에만 두고 `NEXT_PUBLIC_DEMO_MODE`(빌드 타임) 리터럴 비교로 감싼다. 데모 백엔드는 demo 프로필에서만 동작하고 prod+demo는 기동 실패다. 번들에 실제 관리자 자격증명을 절대 넣지 않으며, `check:bundle`이 운영 번들에 데모 코드·계정이 없고 데모 빌드에는 있음을 확인한다.
- `app/`은 조립만, 기능은 `features/`. 시안은 `design/`에 기록으로 두고 고치지 않는다. 핵심 화면은 시안 → 사용자 확인 → 구현, CRUD 화면은 시안의 디자인 토큰을 따른다.
- 테스트 이름은 한국어. 단위 테스트는 순수 유틸·훅, 화면 흐름은 E2E(로컬 `pnpm e2e`, CI 제외).

## 단계 전환 원칙
- 구조는 **단일 인스턴스 모놀리식 → ALB 다중 인스턴스 → MSA** 순서로 진화한다.
- 다음 단계로 넘어갈 때는 반드시 **측정한 근거(현재 구조의 한계 수치)**가 있어야 한다. 넘어가고 싶어서 넘어가지 않는다. 근거가 약하면 현재 단계의 최적화를 더 한다.
- 각 단계 리포트는 "현재 구조의 한계와 근거 수치 → 다음 단계 필요성 판단"으로 끝나고, 전환 결정은 ADR로 남긴다.

## Phase 규율 (의도적으로 하지 않는 것)
- 핫딜(C6) 전까지 일반 구매 재고 차감은 조건부 UPDATE(`stock >= 수량`)를 쓴다. 비관적 락·Redis 비교는 Phase 3-1에서 한다. PK/UNIQUE 외 인덱스는 계속 추가하지 않는다.
- PG·택배 호출은 DB 트랜잭션 밖에서 하고, 불확실한 결과는 정리 작업이 PG 조회로 맞춘다(ADR 0003). Saga는 Phase 4.
- 주문 상태를 바꾸는 트랜잭션은 주문 행을 `findByIdForUpdate`/`findByOrderNoForUpdate`로 먼저 잠근다(ADR 0003). 판매자 주문 엔드포인트는 주문 ID를 트랜잭션 밖에서 일반 조회(`findOrderIdBySellerOrderId`)로 찾고 잠금은 트랜잭션 안에서 건다. 락 전에 읽으면 REPEATABLE READ에서 낡은 스냅샷이 생기기 때문이다. 스케줄러 작업은 이 서비스들을 감싸는 `@Transactional`을 붙이지 않는다.
- PG 결제 되돌리기(`voidMismatch`, `voidClosedOrder`)는 `OrderPaymentService` 한 곳에만 둔다. PG 취소 결과가 불확실하면 결제는 `UNKNOWN`, 항목은 `CANCEL_REQUESTED`로 두고 대사 작업이 맞춘다.
- 쿠폰 수량·사용 상태·좋아요·상품 집계(별점·후기 수·좋아요 수)는 조건부 UPDATE의 영향 행 수로 판정한다. 집계·좋아요 카운터는 native 증감 쿼리로만 바꾼다(해당 컬럼은 `updatable=false`). 행 락은 `member_point`(FOR UPDATE)와 주문 행뿐이다.
- 쓰기를 결정하는 포인트 조회(잔액·로트)는 `member_point` 락을 잡은 뒤 FOR UPDATE 현재 읽기로 한다(스냅샷 읽기 금지). 락 순서는 주문 생성 `member_point → coupon → sku`, 취소·만료·확정·결제 실패·승인 거절 `orders → member_point → coupon → sku`(benefit 되돌림이 재고 복구보다 먼저), 후기 `member_point(적립) → product(별점 집계)`(ADR 0004).
- 반품 환불은 `OrderReturnService`가 C2 취소와 같은 tx1 → PG → tx2 패턴으로 한다. 락 순서는 `orders → member_point → coupon → sku → product`(ADR 0005). PG 불확실이면 `RETURN_REFUNDING`으로 두고 대사가 맞춘다. `ReturnRefundCalculator`의 계산 본체(`compute`)는 하나이고 `plan`(승인된 반품)과 `preview`(요청 전 예상)가 함께 쓴다. 반품 요청 자격은 `Order.checkReturnRequestable` 한 곳에서만 판정하며 요청과 미리보기가 같이 쓴다.
- 정산은 생성 시점 스냅샷이며 생성 뒤 불변이다. 금액은 저장된 스냅샷(수수료율, `line_amount`, 쿠폰 할인, 배송비, 반품 부담액)에서만 계산하고 상품·정책·판매자의 현재 값은 읽지 않는다. 판매자별 한 트랜잭션으로 만들고, UNIQUE 세 개(판매자·기간, 판매자 주문, `settlement_run` 기간)로 멱등을 보장하며, 주문 테이블에는 쓰지 않는다. 음수 정산은 지급 0과 `CARRY_OVER` 조정으로 다음 정산에 넘기고(소프트 딜리트 금지), 기간은 오래된 것부터 엄격히 생성한다. Spring Batch는 Phase 3-2 측정 뒤에 검토한다.
- `sweepByKeyset`·`TransactionRunner`는 `common.application`에 두고 구현은 `common.infrastructure`에 둔다(다른 모듈 application이 order.application을 import하지 않도록).
- 최소 주문 금액 판정은 `CouponTerms.meetsMinimum` 한 곳에서만 한다(`DiscountAllocator`와 견적의 `usable`이 같이 쓴다). 구매자 주문 상세의 항목별 후기 상태는 `review` 모듈이 계산하고 `order`는 `ReviewStateReader` 포트로 읽으며, 후기 작성과 같은 기한 함수를 쓴다.
- 최적화, Kafka, Gateway, Resilience4j는 해당 Phase 전에 미리 넣지 않는다.

## 코드 스타일
- Lombok 사용 안 함. 값 객체는 Kotlin `value class`/`data class`로 만든다.
- 애플리케이션 서비스 테스트는 Mock 라이브러리 대신 인메모리 Fake를 쓴다.
- 돈 계산 클래스(`DiscountAllocator`, `CouponTerms`, `RefundCalculator`, `ReturnRefundCalculator`, `SettlementCalculator`, `Settlement`)를 바꾸면 `./gradlew pitest`를 돌려 임계값(`build.gradle.kts`의 `mutationThreshold`/`coverageThreshold`)을 지킨다.
- 테스트 이름은 한국어 백틱 또는 언더스코어 문장으로 쓴다(예: `제한_초과_주문은_거절된다`).
- 로깅은 kotlin-logging(`private val log = KotlinLogging.logger {}`, 람다 메시지)을 쓴다.
- 로그 레벨: error=지금 사람이 봐야 하는 것(금액 불일치, 데이터 손상 가능, 복구 불가), warn=자동 복구·재시도·대사로 해결될 것, info=작업 시작/종료 건수와 중요한 상태 전이, debug=개발자용 상세.
- 레벨은 `application.yaml`(기본 INFO)과 `application-{local,prod,test}.yaml`로 정하고, 환경변수 `LOGGING_LEVEL_COM_DOGYUN_KAIROS=DEBUG`로 덮어쓴다.

## 커밋과 git
- 커밋 메시지는 한국어 Conventional Commits(`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`).
- 의존성은 필요해진 시점에 추가하고, 커밋 본문에 추가 이유를 쓴다.
- 이 저장소는 **개인 GitHub 계정 전용**이다. 로컬 git 설정(`user.email`: kimdk0903@naver.com)과 remote(`github-personal` SSH 별칭)를 유지하고, 전역 git 설정은 바꾸지 않는다.
- **Git Flow**: `main` ← `dev` ← `feature/*`.
  - 기능 작업은 `dev`에서 `feature/<작업명>` 브랜치를 만들어 진행하고, 끝나면 `dev`로 병합한다.
  - `main`과 `dev`에 직접 커밋하지 않는다. `dev` → `main` 병합은 사용자가 결정한다.
  - push와 병합은 사용자가 요청할 때만 한다.

## 결정 기록
- 아키텍처 결정은 `docs/adr/`에 ADR로 남긴다. 특히 규칙의 예외(예: Phase 1에서 주문 생성 시 재고와 주문 두 애그리거트를 한 트랜잭션에서 변경)는 ADR과 코드 주석으로 표시한다.
- ADR 0002: 상품 검색에서 `catalog`가 brand·seller 테이블을 읽기 전용 뷰 엔티티로 조인하는 예외(`docs/adr/0002-catalog-search-cross-module-read-join.md`).
- ADR 0003: C2 주문 생성·승인·취소를 한 DB 트랜잭션으로 묶고 외부 호출은 트랜잭션 밖에서 하며, 주문 행 락으로 직렬화한다(`docs/adr/0003-c2-order-transaction-and-external-calls.md`).
- ADR 0004: C3 주문 생성·취소가 order·catalog·benefit을 한 트랜잭션에서 바꾸는 확장과 락 순서, 0원 주문의 PG 없는 승인(`docs/adr/0004-c3-benefit-transaction-and-lock-order.md`).
- ADR 0005: C4 반품 환불 트랜잭션(C2 취소와 같은 2-tx 패턴)과 정산 불변성(`docs/adr/0005-c4-return-refund-and-settlement-immutability.md`).
- `idempotency_key` 테이블의 JPA 예외는 위 JPA 규칙 참고.
