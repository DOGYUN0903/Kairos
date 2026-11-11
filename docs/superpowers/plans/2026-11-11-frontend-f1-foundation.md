# 프론트엔드 F1 기반·C1 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `frontend/`에 Next.js 프론트엔드 기반(스택·인증·OpenAPI 타입 연결·역할별 레이아웃·테스트·CI)을 세우고, 구매자·판매자·관리자가 C1(판매자·브랜드·상품·검수·카테고리) 기능 전부를 실제 백엔드로 화면에서 끝까지 쓸 수 있게 한다. 그 전에 핵심 화면 3개의 시안을 그려 사용자 확인을 받고, 백엔드에는 쿠키 인증·`/auth/me`·목록 필드·심사 큐 정렬·demo 시더만 최소로 더한다.

**Architecture:** 백엔드 변경은 OpenAPI 먼저(SDD) → 구현 → `OpenApiContractTest`. 프론트는 모노레포 `frontend/`에 두고 백엔드와는 `docs/api/openapi.yaml`만 공유한다. 브라우저는 Next.js `rewrites`로 같은 출처(`/api/v1/*`, `/files/*`)를 통해 백엔드를 부르므로 CORS가 없고, 리프레시 토큰은 `Path=/api/v1/auth` httpOnly 쿠키, 액세스 토큰은 메모리다. `openapi-typescript` 생성물(`src/api/schema.d.ts`)을 커밋하고 `check:api`가 드리프트를 빌드 실패로 만든다. 데이터는 전부 클라이언트 컴포넌트 + TanStack Query, 가드는 클라이언트 레이아웃, 금액은 `lib/money.ts`의 정수 계산만, 모든 POST는 `Idempotency-Key`와 이중 제출 잠금. 데모 편의 기능은 `features/demo`에 격리하고 `NEXT_PUBLIC_DEMO_MODE`로 트리 셰이킹한다.

**Tech Stack:** 백엔드 Kotlin 2.3·Spring Boot 4.1(기존). 프론트 Node 22 LTS(`.nvmrc`), pnpm 9(`packageManager`), Next.js 15(App Router)·React 19·TypeScript 5(strict), Tailwind CSS 3.4 + shadcn/ui, TanStack Query 5, React Hook Form 7 + Zod 3, `openapi-typescript` 7 + `openapi-fetch` 0.13, Vitest 2 + Testing Library, Playwright 1.4x, ESLint 9(flat config). 새 백엔드 의존성 없음.

**Spec:** `docs/superpowers/specs/2026-11-11-frontend-f1-foundation-design.md`(실행자는 반드시 함께 읽는다. 특히 §4 시각 기준, §5 디렉터리, §7 인증, §8 가드, §9 화면별 API·상태, §10 에러 표, §11 멱등성, §12 금액, §13 상태·접근성, §14 데모, §16 테스트, §19 완료 기준). 상위: C1 스펙 `2026-11-09-c1-seller-catalog-design.md`, 로드맵 `2026-11-09-commerce-roadmap-design.md`. 시안: `design/`.

## Global Constraints

- **사용자 게이트**: Task 1(시안 3개)은 사용자가 `design/login.html`·`seller-dashboard.html`·`admin-dashboard.html`을 확인해야 끝난다. 게이트에 묶이는 것은 **시안을 화면으로 옮기는 Task 7(로그인·가입 화면)·Task 10(판매자 대시보드)·Task 13(관리자 대시보드)과 그 뒤 Task뿐**이다. Task 2~6(백엔드 변경, 프론트 스캐폴딩, API 계층)은 시안과 독립이므로 사용자 확인을 기다리는 동안 진행해도 된다. 단, 사용자의 시안 수정 요청이 백엔드 필드(목록 열·대시보드 수치)에 영향을 주면 Task 3의 OpenAPI를 먼저 고치고 Task 5의 `pnpm gen:api`를 다시 돌린다.
- 저장소: 프론트는 `frontend/`(모노레포). 백엔드 Gradle과 독립, 공유하는 것은 `docs/api/openapi.yaml`뿐. `compose.yaml`에 프론트 서비스를 넣지 않는다. 루트 `.gitignore`의 `.env.*`/`!.env.example` 규칙이 `frontend/`에도 적용되며 `frontend/.gitignore`에 `node_modules/`, `.next/`, `out/`, `playwright-report/`, `test-results/`를 둔다.
- 백엔드 규칙(CLAUDE.md): SDD(OpenAPI 먼저, `OpenApiContractTest`·`ErrorCodeContractTest`·`ArchitectureTest` 매 커밋 통과), 4계층 DDD, Lombok·Mock 금지(Fake), 테스트 이름 한국어, 새 에러 코드 없음(기존 `INVALID_REQUEST`·`AUTH_INVALID_TOKEN`·`UNAUTHORIZED` 재사용), 인덱스 추가 금지, Flyway 변경 없음. OpenAPI `info.version: 0.6.0`.
- 쿠키: 이름 `kairos_refresh`, `HttpOnly; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600`(= `kairos.jwt.refresh-ttl` 14d 초 단위), `Secure`는 `kairos.auth.cookie-secure`(기본 `false`). 쿠키 기반 reissue·logout은 헤더 `X-Requested-With: kairos-web`을 요구하며, 없으면 쿠키를 무시한다. 본문 `refreshToken`은 그대로 유지(perf k6·Swagger).
- 프론트 토큰: 액세스 토큰은 메모리(모듈 스코프 변수)에만, `localStorage`·`sessionStorage`·쿠키에 쓰지 않는다. 프론트는 JWT를 디코딩하지 않는다(`GET /auth/me`로 역할·이름).
- 경로: 프론트 `BACKEND_URL`(서버 전용 env, 기본 `http://localhost:8080`)로 `/api/v1/:path*`·`/files/:path*`를 rewrite. `NEXT_PUBLIC_API_BASE` 같은 브라우저 노출 백엔드 주소를 두지 않는다. Next `middleware.ts`를 만들지 않는다.
- 시각 토큰(Tailwind 테마, `design/assets/kairos.css`와 동일 값): `bg #FFFFFF`, `surface #F3F5F8`, `surface-2 #EEF0F4`, `border #E3E6EC`, `navy #1B2A4A`, `navy-hover #15213B`, `text-2 #5B6577`, `text-3 #8A93A3`, `discount #D2401F`, `success #1F7A4D`, `warning #9A6700`, `danger #B42318`, `hot #E04A22`(핫딜 자리 외 사용 금지), `hot-hover #C9401C`. radius `4px`/`8px`, 글꼴 Noto Sans KR(400/500/700/900), 본문 14px, 컨테이너 1280px, `body { min-width: 1280px }`(데스크톱 전용), 최소 터치 영역 44px, `:focus-visible` 2px navy 아웃라인. 칩 색: `pending #FFF6DD/#7A5600`, `onsale #E6F4EC/#1F7A4D`, `rejected #FBECEA/#B42318`, `paused surface-2/text-2`, `ended surface-2/text-3`, `info #E8EDF7/navy`. 콘솔 사이드바 232px.
- 금액: 원 단위 정수. `src/lib/money.ts` 밖에서 `/`·`%` 연산자 사용 금지(ESLint `no-restricted-syntax`). 서버가 준 `discountRate` 등은 재계산하지 않는다. `Number.isSafeInteger` 아니면 개발 `throw`, 운영 "금액 오류" 표시.
- 일시: KST `LocalDateTime` 문자열(`YYYY-MM-DDTHH:mm:ss`)을 `Date`로 바꾸지 않고 문자열 포맷만 한다.
- 데모: `NEXT_PUBLIC_DEMO_MODE=true`일 때만 `features/demo`가 번들에 들어간다. 데모 계정 `buyer@kairos.demo`·`seller@kairos.demo`·`admin@kairos.demo`, 비밀번호는 백엔드 `KAIROS_DEMO_PASSWORD` / 프론트 `NEXT_PUBLIC_DEMO_PASSWORD`. 실제 관리자(`KAIROS_ADMIN_EMAIL/PASSWORD`)는 어떤 번들·코드에도 들어가지 않는다. `prod`+`demo` 동시 활성이면 백엔드 기동 실패.
- TanStack Query 재시도: GET 1회, 뮤테이션 0회. 낙관적 업데이트 없음. 성공 시 `query-keys.ts`의 키로 invalidate.
- 프론트 명령(`frontend/package.json` scripts, 이름 고정): `dev`, `build`, `start`, `lint`, `typecheck`, `test`, `e2e`, `gen:api`, `check:api`, `check:bundle`. `build`는 `check:api`를 먼저 돈다(`"build": "pnpm check:api && next build"`).
- 테스트 이름은 백엔드처럼 한국어 문장(Vitest `it('…한다')`, Playwright `test('…')`). 단위 테스트는 pure util·훅·클라이언트 미들웨어에만, 화면 흐름은 E2E 3개로만 증명한다(스펙 §16).
- 커밋: 한국어 Conventional Commits, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 백엔드 테스트 `./gradlew test`(Docker 필요), 프론트는 `cd frontend && pnpm lint && pnpm typecheck && pnpm test`. push·병합은 사용자가 요청할 때만.

## Review Focus

1. **액세스 토큰이 만료된 상태에서 로그아웃을 누른 사용자**: 서버가 401만 돌려주고 쿠키·Redis 토큰이 남으면 다음 새로고침의 부트스트랩 reissue가 성공해 "로그아웃했는데 다시 로그인돼 있는" 상태가 된다. logout은 액세스 토큰 없이도 쿠키(+헤더)로 회원을 찾아 Redis 토큰을 지우고 쿠키를 만료시켜야 한다. → Task 2 `액세스_토큰_없이_쿠키와_헤더만으로_logout하면_쿠키가_만료되고_이후_쿠키_reissue는_401`.
2. **같은 화면에서 동시에 뜬 여러 401**(목록·대기 수·`seller/me`가 한 번에 만료): reissue가 한 번만 호출되고 모두 같은 새 토큰으로 재시도돼야 한다. 회전 토큰이라 두 번 호출하면 두 번째가 401이 되어 로그아웃된다. → Task 6 `동시_401_세_건은_reissue를_한_번만_호출하고_모두_같은_토큰으로_재시도한다`.
3. **옵션 축을 바꾼 변경 요청의 SKU 영향**: 축 값 ID를 유지하면 "유지", 새 이름은 "신규(재고 0)", 빠진 값의 조합은 "단종"이어야 하고, 축이 0개 → 1개가 되는 경우 기존 단일 SKU는 단종·새 조합은 신규다. 계산이 틀리면 판매자가 모르는 사이 재고가 0이 된다. → Task 11 `skuImpact` 단위 테스트 `축_하나를_없애면_남은_축의_값마다_신규_1개_기존_조합은_전부_단종`.
4. **정가·판매가 입력에 `0`, 음수, 소수, 1e3, 공백**: Zod 스키마가 OpenAPI 제약(정가 ≥ 1, 판매가 1..정가)을 서버와 같게 잡고, 할인율 힌트가 `NaN%`를 보이지 않아야 한다. → Task 11 `productSchema` 단위 테스트 `판매가가_정가보다_크면_salePrice_필드_에러`, `소수_입력은_거부한다`; Task 6 `discountRate(39000, 27300)은_30`, `정가_0이면_throw`.
5. **`SUSPENDED` 판매자가 콘솔 분기가 놓친 경로로 등록 요청을 보내 409 `SELLER_NOT_ACTIVE`를 받는 경우**: 폼 에러가 아니라 콘솔 상단 정지 배너로 보여야 하고, 등록·수정·브랜드 신청 버튼은 처음부터 비활성이어야 한다. → Task 6 `SELLER_NOT_ACTIVE는_kind_banner`, Task 10 `SellerGate` 단위 테스트 `SUSPENDED면_등록_버튼이_disabled이고_툴팁이_정지_중`.

## 계획자 판단(스펙 §20 "빌더에게 맡기는 것"과 스펙이 비워 둔 것)

- **OpenAPI 버전 0.6.0**, 태그 `Demo` 추가. `/auth/me`는 `Auth` 태그. `components/parameters`에 `QueueSort`(`sort`, enum `LATEST|OLDEST`, 기본 `LATEST`)를 두고 관리자 큐 **4개**(`/admin/sellers`, `/admin/brands`, `/admin/products`, `/admin/change-requests`)에 붙인다. 스펙 §7-3 (8)은 "변경 요청 큐가 이미 가진 것과 같은 형태"라고 했지만 변경 요청 큐에도 정렬 파라미터가 없다(확인함). 백엔드 enum은 `common/domain/ListOrder.kt`(`enum class ListOrder { LATEST, OLDEST }`), 네 Repository `findPage`에 `order: ListOrder` 파라미터(`id.desc()`/`id.asc()`).
- **logout 재정의**: `POST /auth/logout`을 `permitAll`로 바꾸고, 인증 principal이 있으면 그 회원, 없으면 쿠키 `kairos_refresh`(+`X-Requested-With`)를 파싱한 회원의 Redis 토큰을 지운다. 둘 다 없으면 아무것도 지우지 않되 쿠키 만료 헤더는 항상 내려준다(204). 이유는 Review Focus 1. `/auth/*`는 프론트가 재시도하지 않으므로 서버가 받아 줘야 한다.
- **쿠키 발급 위치**: `AuthController`가 `ResponseCookie`를 만든다(서블릿 타입은 presentation 계층에만). `kairos.auth.cookie-secure`는 `common/security/AuthCookieProperties.kt`(`@ConfigurationProperties("kairos.auth")`, `cookieSecure: Boolean = false`). 쿠키 TTL은 `JwtProvider.refreshTtl`을 그대로 쓴다(값 두 벌 금지).
- **`GET /auth/me`**: `MemberQueryService.getMe(memberId): MemberMeResult(memberId, email, name, role)`(회원 없으면 `AUTH_INVALID_TOKEN`), 응답 `MeResponse`. 스펙 §7-3 (5)의 `{memberId, email, name, role}` 그대로.
- **관리자 큐 행의 요청 일시·판매자명**(관리자 대시보드 시안의 "오래된 대기 건" 표가 종류·이름·판매자·요청일을 보이므로): `SellerDetail`·`BrandDetail`에 `createdAt`, `AdminProductSummary`에 `createdAt`·`sellerName`, `ChangeRequestSummary`에 `requestedAt`·`sellerName`을 Task 3에서 함께 추가한다. 판매자명은 catalog의 기존 포트 `SellerReader`에 `getSellerNames`를 더해 `catalog.infrastructure → seller.application` 경로로 페이지당 한 번 묶어 조회한다(ADR 0002 뷰 엔티티는 검색 전용이라 쓰지 않는다).
- **`SellerProductSummary` 확장 구현**: `stockTotal: Int`(`product.skus.filter { skuStatus == ACTIVE }.sumOf { stock }`, 도메인이 이미 SKU를 들고 있어 추가 쿼리 없음), `createdAt: LocalDateTime`(도메인 `Product`에 `val createdAt: LocalDateTime? = null`을 추가하고 `ProductRepositoryImpl.toDomain`이 `BaseEntity.createdAt`을 넣는다; `save`는 무시), `hasPendingChangeRequest: Boolean`(`ProductChangeRequestRepository.findPendingProductIds(productIds: Collection<Long>): Set<Long>` 한 번으로 N+1 없이). OpenAPI에서는 `AdminProductSummary`는 건드리지 않는다.
- **demo 패키지 위치**: `com.dogyun.kairos.demo`는 바운디드 컨텍스트가 아니라 `common`처럼 `ArchitectureRules.modules` 밖에 둔다(여러 모듈의 application 서비스를 직접 호출해야 하므로, 모듈 간 "infrastructure에서만 application 호출" 규칙을 포트 9개로 흉내 내는 것은 과하다). 계층 규칙(application → infrastructure 금지 등)은 패키지 이름으로 그대로 적용되므로 `demo/application/DemoSeeder.kt`, `demo/presentation/DemoController.kt`, `demo/infrastructure/DemoDataInitializer.kt`·`DemoProfileGuard.kt`로 나눈다. `ArchitectureTest`에 "demo는 다른 모듈의 `domain`·`presentation`·`infrastructure`를 import하지 않는다" 규칙 한 개를 추가한다(`ArchitectureRules.demoUsesOnlyApplication()`).
- **`OpenApiContractTest`와 demo 경로**: 계약 테스트는 `test` 프로필로 돌고 `demo` 컨트롤러 빈이 없다. 스펙 쪽에서 `tags`에 `Demo`가 있는 operation을 **활성 프로필에 `demo`가 없으면 제외**하도록 테스트를 고친다(스펙 operation의 태그를 읽는 분기 한 줄). `DemoApiTest`는 `@ActiveProfiles("test", "demo")`로 별도 컨텍스트에서 reseed를 검증한다.
- **demo 시더 멱등 기준**: 계정은 이메일 존재 여부, 판매자는 `seller@kairos.demo` 회원의 `seller/me` 존재, 브랜드는 그 판매자의 브랜드명(`"카이로스 데모"`, `"KAIROS DEMO"`), 카테고리는 대분류 이름 6개(`상의`, `아우터`, `바지`, `신발`, `가방`, `액세서리`, 시안 `cat-nav`와 같음) + 소분류 각 2개, 상품은 데모 브랜드의 **`ON_SALE` 상태** 상품 수가 20 미만이면 부족분만 등록·승인(이름 `"데모 상품 NN"`, 종료·일시 중지된 데모 상품은 세지 않는다 — `DemoApiTest`의 "판매 종료 뒤 reseed하면 1개" 기준), 검수 대기 건은 `PENDING_REVIEW` 데모 상품이 0이면 3개, 대기 브랜드 1개, 대기 판매자 신청 1명(`seller2@kairos.demo`, 비밀번호 같음). 이미지는 `src/main/resources/demo/*.webp` 4장을 `FileService.uploadImage`로 올린다(판매자 회원 소유).
- **demo 프로필 설정**: `application-demo.yaml`에 `kairos.demo.password: ${KAIROS_DEMO_PASSWORD}`만 둔다. `KairosApiApplication`의 `@ConfigurationPropertiesScan`은 **패키지 기준**으로 `@ConfigurationProperties` 클래스를 전부 빈으로 등록하므로(참조 여부와 무관) `DemoProperties(password: String)`가 그대로 있으면 demo가 아닌 프로필에서 `kairos.demo.password` 바인딩 실패로 기동이 깨진다. 따라서 `DemoProperties`에 `@ConfigurationProperties("kairos.demo")`와 함께 **`@Profile("demo")`를 붙인다**(스캐너가 `@Conditional`을 평가해 demo 밖에서는 등록하지 않는다). 별도 `@EnableConfigurationProperties`는 두지 않는다. demo 밖 기동은 기존 `KairosApiApplicationTests`(`test` 프로필 컨텍스트 로드)가 그대로 증명한다. `DemoProperties`는 `DemoSeeder`(application)가 쓰므로 `demo/application`에 둔다(application → infrastructure 의존은 `ArchitectureTest`가 막는다). `.env.example`에 `KAIROS_DEMO_PASSWORD=` 주석 추가.
- **데모 관리자 계정 생성**: `SignUpService.signUp`은 `Role.USER`만 만들고 demo는 `MemberRepository`(domain)를 쓸 수 없다. `SignUpService.signUpAdmin(command: SignUpCommand): Long`을 추가한다(컨트롤러에 노출하지 않음, `AdminInitializer`와 같은 `Member.register(..., Role.ADMIN, ...)`). 단위 테스트 `signUpAdmin은_ADMIN_역할로_가입한다`(Fake 저장소).
- **Tailwind 3.4 + `tailwind.config.ts`**(스펙 §5 파일 목록 그대로). shadcn `components.json`은 `style: new-york`, `baseColor: neutral`, CSS 변수 모드. `globals.css`의 shadcn 변수(`--primary` 등)를 navy 토큰으로 덮는다. 이미지는 `next/image` 대신 `<img>`(최적화·SEO 비목표, 백엔드 절대 URL 그대로).
- **파일 분할**: `features/<역할>/api.ts`가 커지면 `features/seller/products/api.ts`처럼 한 단계 더 나눈다(스펙 §5). 이 계획은 처음부터 `features/seller/{apply,brands,products}/`, `features/admin/{review,categories,sellers}/`로 나눈다.
- **쿼리 키**: `src/lib/query-keys.ts`의 `queryKeys` 객체 — `auth.me`, `catalog.categories`, `catalog.products(params)`, `catalog.product(id)`, `catalog.brand(id)`, `seller.me`, `seller.brands`, `seller.products(params)`, `seller.product(id)`, `admin.queue(kind, params)`, `admin.queueCount(kind)`, `admin.changeRequest(id)`, `admin.product(id)`, `admin.sellers(params)`.
- **ImageUploader 순서 변경**: 드래그 라이브러리 없이 "위로/아래로" 버튼(키보드 접근성이 공짜).
- **E2E 픽스처**: `e2e/fixtures/api.ts`가 Playwright `request`로 백엔드를 직접 호출해 데이터를 만든다(가입·로그인·입점·승인·브랜드·상품). 화면으로 하는 것은 각 spec이 증명하려는 단계뿐이다. 관리자 토큰은 `admin@kairos.demo`.
- **번들 검사**: `scripts/check-bundle.sh`가 `NEXT_PUBLIC_DEMO_MODE` 없이 `next build`한 `.next/static`과 `.next/server`에서 `kairos.demo`·`DemoBar`·`reseed` 문자열을 grep해 하나라도 있으면 실패. 반대로 `NEXT_PUBLIC_DEMO_MODE=true` 빌드에서는 `DemoBar`가 있어야 통과(트리 셰이킹이 아니라 코드 자체가 빠진 게 아님을 확인).
- **대시보드 수치**: 판매자 대시보드(상태별 4칸)·관리자 대시보드(대기 4칸)는 스펙 §9-3의 "`?status=…&size=1`의 `totalElements`" 방식만 쓴다. 집계 API는 만들지 않는다.
- 시안(Task 1)의 세부 레이아웃은 작성자가 정하되 `design/assets/kairos.css`만 쓰고 새 색을 만들지 않는다. 시안 파일은 그 뒤 고치지 않는다(기록).

---

## 파일 구조

```
design/login.html, seller-dashboard.html, admin-dashboard.html, index.html(링크 추가)      (Task 1)
docs/api/openapi.yaml                                                                      (Task 2·3·4)
src/main/resources/application.yaml(kairos.auth), application-demo.yaml, .env.example(KAIROS_DEMO_PASSWORD), demo/*.webp
src/main/kotlin/com/dogyun/kairos/
  common/security/AuthCookieProperties.kt, SecurityConfig.kt(logout permitAll, /api/v1/demo/** ADMIN)
  common/domain/ListOrder.kt
  member/presentation/AuthController.kt(쿠키·me·logout), AuthResponses.kt(MeResponse), AuthRequests.kt(ReissueRequest 선택)
  member/application/AuthService.kt(logoutByRefreshToken), MemberQueryService.kt(getMe), MemberResults.kt(MemberMeResult)
  catalog/application/ProductResults.kt·ProductQueryService.kt, catalog/domain/Product.kt·ProductRepository.kt·ProductChangeRequestRepository.kt,
  catalog/infrastructure/ProductRepositoryImpl.kt·ProductChangeRequestRepositoryImpl.kt, catalog/presentation/Admin{Product,ChangeRequest}Controller.kt
  seller/domain/{Seller,Brand}Repository.kt, seller/infrastructure/{Seller,Brand}RepositoryImpl.kt, seller/presentation/Admin{Seller,Brand}Controller.kt,
  seller/application/SellerQueryService.kt·BrandAdminService.kt, catalog/application/ProductAdminService.kt·ChangeRequestAdminService.kt
  demo/application/{DemoSeeder,DemoProperties}.kt, demo/presentation/DemoController.kt, demo/infrastructure/{DemoDataInitializer,DemoProfileGuard}.kt
  member/application/SignUpService.kt(signUpAdmin), seller/application/SellerQueryService.kt(getCompanyNames), catalog/application/SellerReader.kt·catalog/infrastructure/SellerReaderImpl.kt(getSellerNames)
src/test/kotlin/com/dogyun/kairos/ member/presentation/AuthApiTest.kt, catalog/presentation/SellerProductApiTest.kt, AdminQueueSortApiTest.kt,
  demo/{DemoApiTest,DemoProfileGuardTest}.kt, OpenApiContractTest.kt(Demo 태그 분기), ArchitectureRules.kt·ArchitectureTest.kt(demo 규칙)
.github/workflows/frontend.yml                                                              (Task 15)
frontend/  (스펙 §5 구조 그대로. 아래는 Task가 만드는 순서)
  Task 5: package.json .nvmrc next.config.ts tsconfig.json tailwind.config.ts postcss.config.js components.json eslint.config.mjs
          vitest.config.ts .env.example .gitignore Dockerfile scripts/{check-api-types.sh,check-bundle.sh} src/app/{layout,globals.css,page}.tsx src/api/schema.d.ts src/components/ui/*
  Task 6: src/api/{client,errors,idempotency}.ts src/lib/{money,datetime,query-keys}.ts src/hooks/useIdempotentSubmit.ts + *.test.ts
  Task 7: src/auth/{AuthProvider,useAuth,token-store}.ts(x) src/app/providers.tsx src/app/(store)/{layout,login/page,signup/page}.tsx
          src/app/{forbidden/page,not-found,error}.tsx src/app/seller/layout.tsx src/app/admin/layout.tsx
          src/components/layout/{StoreHeader,CategoryNav,Footer,ConsoleShell,SideNav}.tsx src/components/common/{StatusChip,Money,EmptyState,ErrorState,PageSkeleton,Pagination,ConfirmDialog}.tsx
          src/auth/guards/{RequireAuth,RequireRole}.tsx src/features/auth/{api,schemas}.ts
  Task 8: src/features/demo/{DemoBar,api}.tsx src/app/layout.tsx(조건 삽입)
  Task 9: src/app/(store)/{page,products/page,products/[productId]/page,brands/[brandId]/page}.tsx src/features/catalog/{api,search-params,sku-match}.ts components/{CategoryTree,FilterPanel,SortBar,ProductCard,ProductGrid,ProductGallery,OptionSelector,LikeButton}.tsx
  Task 10: src/app/seller/{page,apply/page,brands/page}.tsx src/features/seller/{gate/SellerGate,apply/{api,schemas,ApplyForm},brands/{api,schemas,BrandCard,BrandDialog},dashboard/{api,StatusCards}}.tsx
  Task 11: src/app/seller/products/{page,new/page}.tsx src/features/seller/products/{api,schemas,sku-preview,sku-impact}.ts components/{ProductTable,StatusSummary,PriceDialog,ProductForm,OptionAxesEditor,SkuPreviewTable} src/components/common/ImageUploader.tsx
  Task 12: src/app/seller/products/[productId]/{page,edit/page}.tsx src/features/seller/products/components/{SkuEditTable,ChangeRequestCard,ProductStatusActions}.tsx
  Task 13: src/app/admin/{page,review/page}.tsx src/features/admin/review/{api,components/{QueueTabs,QueueTable,SellerDetailCard,BrandDetailCard,ProductDetailCard,ChangeRequestDiff,ApproveSellerDialog,RejectDialog}} src/features/admin/dashboard/
  Task 14: src/app/admin/{categories,sellers}/page.tsx src/features/admin/{categories,sellers}/
  Task 15: e2e/{fixtures/api.ts,auth.spec.ts,c1-seller-onboarding.spec.ts,c1-buyer-browse.spec.ts} playwright.config.ts README.md
  Task 16: CLAUDE.md
```

---

### Task 1: 핵심 화면 3개 시안(로그인·가입, 판매자 대시보드, 관리자 대시보드) — **사용자 게이트**

**Files:**
- Create: `design/login.html`, `design/seller-dashboard.html`, `design/admin-dashboard.html`
- Modify: `design/index.html`(화면 목록에 링크 3개 추가)

**Interfaces:**
- Consumes: `design/assets/kairos.css`(토큰·`.console`·`.side-nav`·`.site-header`·`.chip-*`·`.card`·`.notice`·`.btn`·`.field`), 기존 시안의 마크업 관례(`aria-current="page"`, `role="switch"`, `.sr-only` 라벨, `.ph` 이미지 자리).
- Produces: Task 7·10·13이 따를 화면 구조. 이 세 파일은 이후 수정하지 않는다.

- [ ] **Step 1: `design/login.html`** — `.site-header`(검색·유틸 내비 없이 로고만) 아래 가운데 정렬 카드(폭 420px). 상단 탭 2개 `role="tablist"`("로그인" `aria-selected="true"`, "회원가입"). 로그인 폼: 이메일·비밀번호(`.field` + `label`), 폼 상단 오류 자리(`.notice notice-warn`, 문구 "이메일 또는 비밀번호가 올바르지 않습니다."), 제출 `btn btn-primary btn-block btn-lg`. 같은 파일에 가입 폼도 그린다(`hidden` 속성으로 가려 둔 두 번째 패널): 이메일·비밀번호(힌트 "8자 이상")·이름, 이메일 필드 아래 필드 오류 예시("이미 가입된 이메일입니다." `.reason` 스타일을 `.field-error { font-size:12px; color: var(--danger) }`로 페이지 내 `<style>`에 정의). 하단 "비회원도 상품을 둘러볼 수 있습니다 → 상품 보기" 링크.
- [ ] **Step 2: `design/seller-dashboard.html`** — `.console` 레이아웃, 사이드 메뉴 "대시보드"(`aria-current="page"`)·"상품 관리"·"브랜드"·"정산 준비 중". `console__content`에 **상태별 4가지 진입을 한 파일에 섹션으로** 그린다(각 섹션 `<section id="state-apply|pending|rejected|approved|suspended">`, 상단에 섹션 사이를 오가는 앵커 목록 "상태별 보기"): ① 미신청 — 입점 안내 카드 + "입점 신청" 버튼, 사이드 메뉴는 "대시보드"만; ② `PENDING` — 신청 내용 읽기 전용 표(회사명·사업자등록번호·대표자·연락처·정산 계좌) + `.notice` "심사 중입니다. 내용을 고치면 다시 심사합니다." + "수정"; ③ `REJECTED` — `.notice-warn` 반려 사유 + 수정 폼(필드는 §9-2 입점 신청과 같음); ④ `APPROVED` — 요약 카드 4칸(`.summary` 스타일 재사용: 검수 대기/판매 중/일시 중지/반려·종료, 각 숫자 + "보기" 링크) + "최근 등록 상품" 표 5행(이미지·상품명·상태 칩·등록일) + 바로 가기 버튼("상품 등록", "브랜드 신청"); ⑤ `SUSPENDED` — 상단 `.notice-warn` "판매자 정지 중: [사유]" 배너 + ④와 같은 카드지만 "상품 등록"·"브랜드 신청" 버튼 `disabled` + `title="정지 중"`.
- [ ] **Step 3: `design/admin-dashboard.html`** — `.console` 레이아웃, 사이드 메뉴 "대시보드"(`aria-current="page"`)·"검수"(`.count` 배지)·"카테고리"·"판매자"·"핫딜 준비 중". 본문: 대기 건수 카드 4칸(판매자 2·브랜드 4·상품 12·변경 요청 5, 각 "검수 열기" 링크 `admin-review.html`), 그 아래 2열: "오래된 대기 건 5개" 표(종류·이름·판매자·요청일·"열기")와 "바로 가기" 카드(카테고리 관리·판매자 관리·검수). 통계 그래프는 두지 않는다(스펙 §9-3 "그 이상은 두지 않는다").
- [ ] **Step 4: `design/index.html`** — `.screens`에 `<a href="login.html">`(`사용자` · 로그인·가입), `seller-dashboard.html`(`판매자` · 대시보드 · "미신청/심사 중/반려/승인/정지 5가지 진입"), `admin-dashboard.html`(`관리자` · 대시보드) 추가.
- [ ] **Step 5: 확인** — 브라우저로 세 파일을 열어 1280px에서 가로 스크롤 없음, 모든 `input`에 `label`, 버튼 44px, 핫딜 색 미사용(`grep -c "hot" design/login.html design/seller-dashboard.html design/admin-dashboard.html`이 사이드 메뉴 "핫딜 준비 중" 항목 외 0)을 확인한다.
- [ ] **Step 6: 커밋** `design: 로그인·판매자 대시보드·관리자 대시보드 시안 추가`
- [ ] **Step 7: 사용자 게이트 — 여기서 멈춘다.** 사용자에게 세 파일 경로를 보여 주고 확인을 받는다. 수정 요청이 있으면 반영해 `design: 시안 수정(…)`으로 커밋하고 다시 확인받는다. **확인 전에는 Task 2를 시작하지 않는다.**

### Task 2: 백엔드 인증 — 리프레시 쿠키(login·reissue), 쿠키 기반 reissue·logout, `GET /auth/me`

**Files:**
- Create: `src/main/kotlin/com/dogyun/kairos/common/security/AuthCookieProperties.kt`, `member/application/MemberResults.kt`
- Modify: `docs/api/openapi.yaml`, `src/main/resources/application.yaml`, `common/security/SecurityConfig.kt`, `member/presentation/{AuthController,AuthRequests,AuthResponses}.kt`, `member/application/{AuthService,MemberQueryService}.kt`
- Test: `src/test/kotlin/com/dogyun/kairos/member/presentation/AuthApiTest.kt`

**Interfaces:**
- Produces(OpenAPI): `info.version: 0.6.0`. `POST /auth/login`·`POST /auth/reissue` 200 응답에 `headers: Set-Cookie`(description에 Global Constraints의 쿠키 속성 그대로). `reissue`의 `requestBody.required: false`, `ReissueRequest.refreshToken` 선택, description "본문이 없으면 쿠키 `kairos_refresh`를 쓴다(헤더 `X-Requested-With: kairos-web` 필수). 둘 다 없으면 400 `INVALID_REQUEST`". `reissue`·`logout`에 `parameters: X-Requested-With`(header, optional) 추가. `logout`은 `security: []`, description "액세스 토큰 또는 쿠키(+헤더)로 회원을 찾아 리프레시 토큰을 지우고 쿠키를 만료시킨다. 둘 다 없어도 204". `GET /auth/me`(tags `Auth`, operationId `me`, 200 `MeResponse{memberId:int64, email, name, role: enum[USER, ADMIN]}`, 401 `Unauthorized`).
- Produces(backend): `AuthCookieProperties(cookieSecure: Boolean = false)`(`kairos.auth.cookie-secure`, `application.yaml`에 `kairos.auth.cookie-secure: false`). `AuthController`: `REFRESH_COOKIE = "kairos_refresh"`, `REQUESTED_WITH = "kairos-web"`, `private fun refreshCookie(token: String?): ResponseCookie`(token null이면 `maxAge(0)`), login/reissue가 `ResponseEntity`에 `HttpHeaders.SET_COOKIE` 추가, `reissue(@RequestBody(required=false) request: ReissueRequest?, @CookieValue(REFRESH_COOKIE, required=false) cookie: String?, @RequestHeader("X-Requested-With", required=false) requestedWith: String?)`: `token = request?.refreshToken ?: cookie.takeIf { requestedWith == REQUESTED_WITH }` 없으면 `INVALID_REQUEST`. `logout(@AuthenticationPrincipal loginMember: LoginMember?, cookie, requestedWith)`: principal 있으면 `authService.logout(id)`, 아니면 쿠키(+헤더)가 있으면 `authService.logoutByRefreshToken(cookie)`, 항상 만료 쿠키 헤더 + 204. `me(@AuthenticationPrincipal loginMember): ApiResponse<MeResponse>`.
  `AuthService.logoutByRefreshToken(refreshToken: String)`: `jwtProvider.parseRefreshToken`이 null이면 무시, 아니면 `refreshTokenStore.delete(memberId)`(저장된 토큰과 달라도 지운다 — 로그아웃 의도는 명확).
  `MemberQueryService.getMe(memberId: Long): MemberMeResult`(`data class MemberMeResult(memberId: Long, email: String, name: String, role: Role)`), 없으면 `AUTH_INVALID_TOKEN`. `MeResponse(memberId, email, name, role: String)`.
  `SecurityConfig`: `/api/v1/auth/logout`을 `permitAll` 목록에 추가. `ReissueRequest(val refreshToken: String?)`(`@NotBlank` 제거).

- [ ] **Step 1: OpenAPI** — 위 변경. `./gradlew test --tests '*OpenApiContractTest'` → FAIL("스펙에만 있음: GET /api/v1/auth/me").
- [ ] **Step 2: 실패 테스트** — `AuthApiTest` 추가(기존 헬퍼 재사용, `MockMvc`의 `cookie`·`header`): `로그인_응답은_kairos_refresh_쿠키를_HttpOnly_SameSite_Strict_Path_auth_MaxAge_14일로_내려준다`(`Set-Cookie` 헤더 문자열에 `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age=1209600` 포함, `Secure` 미포함), `본문_없이_쿠키와_X_Requested_With로_reissue하면_새_토큰과_새_쿠키를_받고_옛_쿠키는_401`, `헤더_없는_쿠키_reissue는_400_INVALID_REQUEST`, `본문_refreshToken은_헤더_없이도_계속_동작한다`, `액세스_토큰_없이_쿠키와_헤더만으로_logout하면_쿠키가_만료되고_이후_쿠키_reissue는_401`(`Max-Age=0`), `액세스_토큰으로_logout해도_쿠키가_만료된다`, `토큰도_쿠키도_없는_logout은_204`, `auth_me는_memberId_email_name_role을_준다`(가입 이름 "홍길동", role `USER`), `비로그인_auth_me는_401`.
- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*AuthApiTest'` FAIL.
- [ ] **Step 4: 구현** — Interfaces대로. `JwtProvider.refreshTtl.seconds`로 `maxAge`.
- [ ] **Step 5: PASS** — `./gradlew test --tests '*AuthApiTest' --tests '*OpenApiContractTest' --tests '*CommerceAuthorizationApiTest'`, 이어 `./gradlew test` 전체.
- [ ] **Step 6: 커밋** `feat: 리프레시 토큰 쿠키 발급·쿠키 기반 재발급과 로그아웃, GET /auth/me`

### Task 3: 백엔드 — `SellerProductSummary` 확장(`stockTotal`·`createdAt`·`hasPendingChangeRequest`), 관리자 큐 행의 `createdAt`·`sellerName`, 관리자 심사 큐 `sort=LATEST|OLDEST`

**Files:**
- Create: `common/domain/ListOrder.kt`
- Modify: `docs/api/openapi.yaml`, `catalog/domain/{Product,ProductChangeRequest,ProductRepository,ProductChangeRequestRepository}.kt`, `catalog/infrastructure/{ProductRepositoryImpl,ProductChangeRequestRepositoryImpl,SellerReaderImpl}.kt`, `catalog/application/{ProductResults,ProductQueryService,ProductAdminService,ChangeRequestAdminService,SellerReader}.kt`, `catalog/presentation/{AdminProductController,AdminChangeRequestController}.kt`, `seller/domain/{Seller,Brand,SellerRepository,BrandRepository}.kt`, `seller/infrastructure/{SellerRepositoryImpl,BrandRepositoryImpl}.kt`, `seller/application/{SellerResults,BrandResults,SellerQueryService,BrandQueryService,BrandAdminService}.kt`, `seller/presentation/{AdminSellerController,AdminBrandController}.kt`, 테스트 Fake(`catalog/application/Fakes.kt`·`seller/application/Fakes.kt`가 있으면 시그니처 맞춤 — `SellerReader` Fake에 `getSellerNames` 추가)
- Test: `catalog/presentation/SellerProductApiTest.kt`(추가), `src/test/kotlin/com/dogyun/kairos/AdminQueueSortApiTest.kt`(새 파일, `CommerceTestSupport` 상속), `src/test/kotlin/com/dogyun/kairos/AdminQueueRowApiTest.kt`(새 파일, `CommerceTestSupport` 상속)

**Interfaces:**
- Produces(OpenAPI): `SellerProductSummary`에 `stockTotal: integer int32`(description "ACTIVE SKU 재고 합"), `createdAt: string(LocalDateTime)`, `hasPendingChangeRequest: boolean`을 `required`로 추가. **관리자 큐 행(관리자 대시보드 시안 "오래된 대기 건" 표의 종류·이름·판매자·요청일)**: `SellerDetail`에 `createdAt`(신청 일시), `BrandDetail`에 `createdAt`(신청 일시), `AdminProductSummary`에 `createdAt`(등록 일시)·`sellerName`(판매자 회사명), `ChangeRequestSummary`에 `requestedAt`(요청 일시)·`sellerName`을 모두 `required`로 추가(전부 `string`, description "KST LocalDateTime, 오프셋 없음"). `SellerDetail`·`BrandDetail`은 `/seller/me`·`/seller/brands`에도 쓰이므로 그 응답에도 같은 필드가 실린다(추가만 있고 제거 없음). `components/parameters/QueueSort`: `name: sort, in: query, required: false, schema: {type: string, enum: [LATEST, OLDEST], default: LATEST}`. 네 큐 `GET /admin/sellers|brands|products|change-requests`에 `- $ref: '#/components/parameters/QueueSort'` 추가, summary의 "최신순"을 "`sort` 기본 최신순"으로.
- Produces(backend): `enum class ListOrder { LATEST, OLDEST }`. 도메인 `Product`·`ProductChangeRequest`·`Seller`·`Brand` 생성자 끝에 `val createdAt: LocalDateTime? = null`(읽기 전용, 각 `RepositoryImpl.toDomain`이 `BaseEntity.createdAt`을 넣고 `save`/`toEntity`는 무시한다. 결과 DTO로 바꿀 때 `requireNotNull(createdAt)` — 저장소에서 온 애그리거트는 항상 값이 있다). `ProductChangeRequestRepository.findPendingProductIds(productIds: Collection<Long>): Set<Long>`, `findPage(status, order: ListOrder, page, size)`. `ProductRepository.findPageByStatus(status, order, page, size)`, `SellerRepository.findPage(status, order, page, size)`, `BrandRepository.findPage(status, order, page, size)`(`OLDEST`면 `id.asc()`). 네 application `listForAdmin(status, order, page, size)`, 네 컨트롤러 `@RequestParam(defaultValue = "LATEST") sort: ListOrder`. `SellerProductSummary(…, stockTotal: Int, createdAt: LocalDateTime, hasPendingChangeRequest: Boolean)`, `toSellerProductSummary(product, brandName, thumbnailUrl, hasPendingChangeRequest)`. `SellerDetail(…, createdAt: LocalDateTime)`, `BrandDetail(…, createdAt: LocalDateTime)`, `AdminProductSummary(…, createdAt: LocalDateTime, sellerName: String)`, `ChangeRequestSummary(…, requestedAt: LocalDateTime, sellerName: String)`.
  **판매자명 조회(모듈 경계)**: `seller/application/SellerQueryService.getCompanyNames(sellerIds: Collection<Long>): Map<Long, String>`(`SellerRepository.findAllByIds` 한 번, 기존 `findSettlementSnapshots`와 같은 모양) → catalog 포트 `SellerReader.getSellerNames(sellerIds: Collection<Long>): Map<Long, String>`(`catalog/application`) → `catalog/infrastructure/SellerReaderImpl`이 `SellerQueryService.getCompanyNames`로 구현(기존 `getBrandNames`와 같은 경로, ADR 0002의 뷰 엔티티는 쓰지 않는다). `ProductAdminService.listForAdmin`·`ChangeRequestAdminService.listForAdmin`은 페이지 한 장의 `sellerId` 집합으로 **한 번** 호출해 채운다(행당 조회 금지). 없는 판매자는 `"(삭제된 판매자)"`.

- [ ] **Step 1: OpenAPI** — 위 변경(경로 추가 없음, `OpenApiContractTest` 그대로 통과).
- [ ] **Step 2: 실패 테스트** — `SellerProductApiTest` 추가: `판매자_상품_목록은_ACTIVE_SKU_재고_합과_등록일과_대기_변경_요청_여부를_준다`(SKU 4개 중 두 개를 재고 3·4로 바꾸면 `stockTotal 7`, `onSale=false`인 SKU도 ACTIVE면 합산, 변경 요청 등록 후 `hasPendingChangeRequest true`, 철회(`DELETE …/change-requests/{id}`) 후 `false`, `createdAt`이 `yyyy-MM-ddTHH:mm:ss` 형식). `AdminQueueSortApiTest`: `관리자_큐_네_개는_sort_OLDEST면_id_오름차순이고_기본은_내림차순`(판매자 2·브랜드 2·상품 2·변경 요청 2를 순서대로 만들고 각 큐 `?status=PENDING|PENDING_REVIEW&sort=OLDEST`의 첫 행 id < 둘째 행 id, `sort` 없이 호출하면 반대), `sort_값이_잘못되면_400`. `AdminQueueRowApiTest`: `관리자_큐_네_개의_행은_요청_일시를_준다`(네 큐 각 첫 행의 `createdAt`/`requestedAt`이 `yyyy-MM-ddTHH:mm:ss` 형식이고 생성 직전·직후 KST 시각 사이), `상품_큐와_변경_요청_큐의_행은_판매자_회사명을_준다`(판매자 2명이 각각 상품·변경 요청을 만들면 각 행 `sellerName`이 그 판매자의 `companyName`), `seller_me와_seller_brands도_createdAt을_준다`.
- [ ] **Step 3: 실패 확인**, **Step 4: 구현**(QueryDSL `orderBy`를 `order`로 분기, `findPendingProductIds`는 `status = PENDING and productId in (…)` 한 쿼리, `getCompanyNames`는 `id in (…)` 한 쿼리). `ArchitectureTest`가 catalog → seller.application 호출이 `catalog.infrastructure`에서만 일어나는지 확인한다.
- [ ] **Step 5: PASS** — `./gradlew test --tests 'com.dogyun.kairos.catalog.*' --tests 'com.dogyun.kairos.seller.*' --tests '*AdminQueueSortApiTest' --tests '*AdminQueueRowApiTest' --tests '*ArchitectureTest'`, 이어 전체.
- [ ] **Step 6: 커밋** `feat: 판매자 상품 목록 재고 합·등록일·변경 요청 여부, 관리자 큐 행 요청 일시·판매자명, 심사 큐 정렬 옵션`

### Task 4: 백엔드 — demo 프로필 시더·`POST /api/v1/demo/reseed`·`prod`+`demo` 기동 차단

**Files:**
- Create: `demo/application/{DemoSeeder,DemoProperties}.kt`, `demo/presentation/DemoController.kt`, `demo/infrastructure/{DemoDataInitializer,DemoProfileGuard}.kt`, `src/main/resources/application-demo.yaml`, `src/main/resources/demo/{demo-1,demo-2,demo-3,demo-4}.webp`(각 ≤ 50KB, 단색+텍스트면 충분), `src/test/kotlin/com/dogyun/kairos/demo/{DemoApiTest,DemoProfileGuardTest}.kt`
- Modify: `docs/api/openapi.yaml`, `common/security/SecurityConfig.kt`, `member/application/SignUpService.kt`(`signUpAdmin`), `.env.example`, `src/test/kotlin/com/dogyun/kairos/{OpenApiContractTest,ArchitectureRules,ArchitectureTest}.kt`, `member/application/SignUpServiceTest.kt`(있으면 추가, 없으면 생성)

**Interfaces:**
- Consumes: `SignUpService.signUp(SignUpCommand)`(구매자·판매자·대기 판매자), `SignUpService.signUpAdmin(SignUpCommand)`(데모 관리자, 이 Task에서 추가), `MemberRepository`는 쓰지 않음(application만), `SellerService.apply`, `SellerAdminService.approve(sellerId, commissionRateBp, reviewerId)`, `SellerQueryService.getMine`, `BrandService`·`BrandAdminService.approve`, `BrandQueryService`, `CategoryService.create`·`CategoryQueryService`, `FileService.uploadImage`, `ProductService.register`, `ProductAdminService.approve`, `ProductQueryService.listMine`. 각 서비스의 실제 Command 타입은 구현 시 해당 `*Commands.kt`를 읽는다.
- Produces(OpenAPI): `tags`에 `Demo`(description "demo 프로필에서만 존재한다. 운영에는 없다"). `POST /api/v1/demo/reseed`(tags `[Demo]`, operationId `reseedDemo`, summary "데모 데이터 보충 (ADMIN, demo 프로필)", 200 `ApiResponse{data: DemoReseedResult{members, sellers, brands, categories, products, pendingReviews: int32 — 이번 호출로 새로 만든 수}}`, 401, 403).
- Produces(backend): `@Profile("demo")` 전용 — `DemoProperties(password: String)`(`demo/application`, `@ConfigurationProperties("kairos.demo") @Profile("demo")`, 계획자 판단 절 참고), `DemoSeeder.seed(): DemoReseedResult`(`@Transactional` 없음 — 각 모듈 서비스가 자기 트랜잭션; 멱등 기준은 계획자 판단 절), `DemoDataInitializer: ApplicationRunner`(`@Profile("demo")`, 시작 시 `seed()` + info 로그 건수), `DemoController`(`@RestController @Profile("demo") @RequestMapping("/api/v1/demo")`, `POST /reseed`), `DemoProfileGuard`(`@Component @Profile("demo")`, 생성자에서 `Environment.activeProfiles`에 `"prod"`가 있으면 `IllegalStateException("prod 프로필에서는 demo 프로필을 켤 수 없습니다")`). `SecurityConfig`: `.requestMatchers("/api/v1/demo/**").hasRole(ADMIN)`. 데모 계정 상수 `DemoAccounts`(`BUYER = "buyer@kairos.demo"`, `SELLER`, `SELLER_PENDING = "seller2@kairos.demo"`, `ADMIN = "admin@kairos.demo"`)는 `demo/application`에만 둔다.
  `OpenApiContractTest.specOperations()`: `environment.activeProfiles`에 `demo`가 없으면 `tags`에 `Demo`가 포함된 operation을 제외. `ArchitectureRules.demoUsesOnlyApplication()`: `noClasses().that().resideInAPackage("$root.demo..").should().dependOnClassesThat().resideInAnyPackage(modules.flatMap { listOf("$root.$it.domain..", "$root.$it.presentation..", "$root.$it.infrastructure..") })`, `ArchitectureTest`에 등록.
- Produces(설정): `application-demo.yaml` — `kairos.demo.password: ${KAIROS_DEMO_PASSWORD}`. `.env.example`에 `# 데모 프로필(SPRING_PROFILES_ACTIVE=local,demo)일 때 세 데모 계정의 비밀번호(8자 이상). 운영에는 두지 않는다.` + `KAIROS_DEMO_PASSWORD=`.

- [ ] **Step 1: OpenAPI** — 위 변경 + `OpenApiContractTest` 분기. `./gradlew test --tests '*OpenApiContractTest'` PASS(test 프로필이라 Demo 제외).
- [ ] **Step 2: 실패 테스트** — `DemoProfileGuardTest`(순수 단위, `org.springframework.mock.env.MockEnvironment`): `prod와_demo가_동시에_활성이면_생성자가_IllegalStateException`, `demo만_활성이면_통과`. `DemoApiTest`(`@ActiveProfiles("test", "demo")`, `@TestPropertySource(properties = ["kairos.demo.password=demo-password1"])`, `CommerceTestSupport` 상속): `기동_시_데모_계정_셋과_승인_판매자_브랜드_카테고리_상품_20개가_있다`(`buyer@kairos.demo` 로그인 성공, `GET /products?size=1`의 `totalElements >= 20`, `GET /categories` 대분류 6개), `reseed를_두_번_불러도_새로_만드는_수는_두_번째에_전부_0이고_상품_수가_그대로다`, `데모_상품_하나를_판매_종료한_뒤_reseed하면_부족분_1개만_새로_만든다`, `일반_회원의_reseed는_403`, `비로그인_reseed는_401`.
- [ ] **Step 3: 실패 확인**, **Step 4: 구현**(시더 순서: 계정 → 카테고리 → 판매자 신청·승인(수수료 1000bp, reviewerId = 데모 관리자) → 브랜드 신청·승인 → 이미지 업로드(판매자 회원) → 상품 등록(`CommerceTestSupport.productContent`와 같은 모양: 옵션 색상 2 × 사이즈 2, 정가 39000·판매가 27300 등 다양하게)·승인 → 대기 건).
- [ ] **Step 5: PASS** — `./gradlew test --tests 'com.dogyun.kairos.demo.*' --tests '*ArchitectureTest' --tests '*OpenApiContractTest'`, 이어 전체. `SPRING_PROFILES_ACTIVE=local,demo KAIROS_DEMO_PASSWORD=demo-password1 ./gradlew bootRun`으로 한 번 띄워 로그에 시더 건수가 찍히는지 확인(`docker compose up -d` 필요).
- [ ] **Step 6: 커밋** `feat: demo 프로필 데이터 시더와 reseed 엔드포인트, prod+demo 동시 활성 차단`

### Task 5: 프론트 스캐폴딩 — pnpm·Next.js·TS strict·Tailwind·shadcn·디자인 토큰·OpenAPI 타입 생성·check:api·lint 규칙·Vitest·Dockerfile

**Files:**
- Create: `frontend/package.json`, `.nvmrc`(`22`), `next.config.ts`, `tsconfig.json`, `tailwind.config.ts`, `postcss.config.js`, `components.json`, `eslint.config.mjs`, `vitest.config.ts`, `vitest.setup.ts`, `.env.example`, `.gitignore`, `Dockerfile`, `scripts/check-api-types.sh`, `scripts/check-bundle.sh`, `src/app/layout.tsx`, `src/app/globals.css`, `src/app/page.tsx`(임시 "KAIROS"), `src/api/schema.d.ts`(생성물), `src/components/ui/*`(shadcn: button, input, select, dialog, tabs, table, badge, toast/toaster, tooltip, checkbox, switch, textarea, label, skeleton)
- Test: `frontend/src/lib/__tests__/smoke.test.ts`(Vitest 동작 확인용 1건, Task 6에서 money 테스트가 생기면 삭제)

**Interfaces:**
- Produces(scripts): `"dev": "next dev"`, `"build": "pnpm check:api && next build"`, `"start": "next start"`, `"lint": "eslint ."`, `"typecheck": "tsc --noEmit"`, `"test": "vitest run"`, `"e2e": "playwright test"`(Task 15), `"gen:api": "openapi-typescript ../docs/api/openapi.yaml -o src/api/schema.d.ts"`, `"check:api": "sh scripts/check-api-types.sh"`, `"check:bundle": "sh scripts/check-bundle.sh"`. `packageManager: "pnpm@9.x"`, `engines.node: ">=22"`.
- Produces(config): `next.config.ts` — `output: 'standalone'`, `async rewrites()` → `[{ source: '/api/v1/:path*', destination: `${process.env.BACKEND_URL ?? 'http://localhost:8080'}/api/v1/:path*` }, { source: '/files/:path*', destination: `${BACKEND_URL}/files/:path*` }]`. `tsconfig.json` `strict: true`, `noUncheckedIndexedAccess: true`, `paths: { "@/*": ["./src/*"] }`. `tailwind.config.ts` `theme.extend.colors`에 Global Constraints 토큰(키 `bg, surface, 'surface-2', border, navy, 'navy-hover', 'text-2', 'text-3', discount, success, warning, danger, hot, 'hot-hover'`), `borderRadius: { DEFAULT: '4px', lg: '8px' }`, `fontFamily.sans: ['"Noto Sans KR"', …]`, `container: { center: true, padding: '24px', screens: { xl: '1280px' } }`. `globals.css`: Noto Sans KR `@import`(Google Fonts), `body { min-width: 1280px; font-size: 14px; color: navy }`, `:focus-visible { outline: 2px solid #1B2A4A; outline-offset: 2px }`, shadcn 변수 `--primary: navy`, `--destructive: danger`, `--radius: 4px`. `eslint.config.mjs`: `next/core-web-vitals` + `typescript-eslint` + 규칙 `no-restricted-syntax`에 `{ selector: "BinaryExpression[operator='/']", message: "금액 나눗셈은 src/lib/money.ts 에서만" }`, `{ selector: "BinaryExpression[operator='%']", … }`, `{ selector: "AssignmentExpression[operator='/=']" }`, `{ selector: "AssignmentExpression[operator='%=']" }`; `files: ['src/lib/money.ts']` 블록에서 해당 규칙 `off`. `vitest.config.ts`: `environment: 'jsdom'`, `include: ['src/**/*.test.{ts,tsx}']`, `setupFiles: ['./vitest.setup.ts']`(`@testing-library/jest-dom/vitest`), `alias '@'`.
  `scripts/check-api-types.sh`: `tmp=$(mktemp)`; `pnpm exec openapi-typescript ../docs/api/openapi.yaml -o "$tmp"`; `diff -q "$tmp" src/api/schema.d.ts || { echo "openapi.yaml 이 바뀌었습니다. pnpm gen:api 를 실행해 src/api/schema.d.ts 를 같은 커밋에 넣으세요."; exit 1; }`.
  `scripts/check-bundle.sh`: `rm -rf .next && env -u NEXT_PUBLIC_DEMO_MODE -u NEXT_PUBLIC_DEMO_PASSWORD pnpm exec next build`; `grep -rqE "kairos\.demo|DemoBar|/demo/reseed" .next/static .next/server && { echo "운영 번들에 데모 코드가 있습니다"; exit 1; }`; 이어 `NEXT_PUBLIC_DEMO_MODE=true pnpm exec next build` 후 같은 grep이 **매칭돼야** 통과(아니면 "데모 빌드에 DemoBar 가 없습니다" exit 1). Task 8 전까지는 두 번째 검사를 `--skip-demo-check` 인자로 건너뛴다(Task 8에서 인자 제거).
  `.env.example`(스펙 §15 그대로): `BACKEND_URL=http://localhost:8080`, `NEXT_PUBLIC_DEMO_MODE=false`, `# NEXT_PUBLIC_DEMO_PASSWORD=`, 주석 "`NEXT_PUBLIC_DEMO_*`는 로컬·데모 서버에서만. 운영 빌드에 두면 데모 계정이 번들에 들어간다". `Dockerfile`: `node:22-alpine` 3단계(deps → build → `.next/standalone` 실행, `ENV NODE_ENV=production`, `CMD ["node", "server.js"]`), compose에는 넣지 않는다.

- [ ] **Step 1: 생성** — `cd frontend`가 아닌 루트에서 `pnpm dlx create-next-app@15 frontend --ts --tailwind --eslint --app --src-dir --import-alias "@/*" --use-pnpm --no-turbopack`, 이어 `cd frontend && pnpm dlx shadcn@latest init`(new-york, neutral, CSS variables) + `pnpm dlx shadcn@latest add button input select dialog tabs table badge toast tooltip checkbox switch textarea label skeleton`. 의존성 추가: `@tanstack/react-query`, `openapi-fetch`, `react-hook-form`, `@hookform/resolvers`, `zod`; dev: `openapi-typescript`, `vitest`, `@vitejs/plugin-react`, `jsdom`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `typescript-eslint`. Tailwind가 v4로 생성되면 3.4로 내린다(`tailwind.config.ts` 방식 유지).
- [ ] **Step 2: 설정 파일** — Interfaces대로 작성. `.nvmrc`, `packageManager`, `.gitignore`, `.env.example`, `Dockerfile`, 두 스크립트.
- [ ] **Step 3: 타입 생성** — `pnpm gen:api` → `src/api/schema.d.ts` 커밋 대상. `pnpm check:api` PASS. 임시로 `docs/api/openapi.yaml`의 description 한 글자를 바꿔 `pnpm check:api`가 FAIL하는지 확인한 뒤 되돌린다(`git checkout docs/api/openapi.yaml`).
- [ ] **Step 4: 검증** — `pnpm lint && pnpm typecheck && pnpm test && pnpm build` PASS. `src/app/page.tsx`에 `const x = 10 / 2`를 넣어 `pnpm lint`가 "금액 나눗셈은 …" 메시지로 FAIL하는지 확인 후 제거. `pnpm check:bundle --skip-demo-check` PASS. `BACKEND_URL=http://localhost:8080 pnpm dev` 후 `curl -s localhost:3000/api/v1/categories`가 백엔드 JSON을 돌려주는지 확인(백엔드 기동 필요).
- [ ] **Step 5: 커밋** `chore: 프론트엔드 스캐폴딩(Next.js, Tailwind 토큰, shadcn, OpenAPI 타입 생성·검사, lint 금액 규칙)` — 본문에 의존성 추가 이유 한 줄씩.

### Task 6: API 계층 — `client.ts`(인증 미들웨어·401 단일 reissue), `errors.ts`, `idempotency.ts`·`useIdempotentSubmit`, `money.ts`, `datetime.ts`, `query-keys.ts`

**Files:**
- Create: `frontend/src/api/{client,errors,idempotency}.ts`, `src/auth/token-store.ts`, `src/hooks/useIdempotentSubmit.ts`, `src/lib/{money,datetime,query-keys}.ts`
- Test: `src/api/{client,errors,idempotency}.test.ts`, `src/hooks/useIdempotentSubmit.test.tsx`, `src/lib/{money,datetime}.test.ts`
- Delete: `src/lib/__tests__/smoke.test.ts`

**Interfaces:**
- Produces(`token-store.ts`): `getAccessToken(): string | null`, `setAccessToken(token: string | null): void`, `subscribe(listener: () => void): () => void`(모듈 스코프 변수, 저장소 접근 없음).
- Produces(`client.ts`): `import type { paths } from '@/api/schema'`; `export const api = createClient<paths>({ baseUrl: '/' })`; `export const REQUESTED_WITH = { 'X-Requested-With': 'kairos-web' } as const`; `export class ApiError extends Error { constructor(readonly status: number, readonly code: ErrorCode, message: string, readonly traceId: string | null) }`; `export type ErrorCode = components['schemas']['ErrorResponse']['code']`; `export async function reissueOnce(): Promise<string | null>`(진행 중 Promise를 모듈 변수로 공유, `POST /api/v1/auth/reissue` 본문 없음 + `REQUESTED_WITH`, 200이면 `setAccessToken` 후 토큰 반환, 아니면 `setAccessToken(null)` 후 null). 미들웨어 `onRequest`: 토큰 있으면 `Authorization: Bearer`; `onResponse`: `response.status === 401 && !url.pathname.startsWith('/api/v1/auth/') && !request.headers.has('X-Retried')` → `reissueOnce()` → 토큰 있으면 원 요청을 `X-Retried: 1` 헤더로 한 번 재전송(**`onResponse` 시점의 `request`는 본문 스트림이 이미 소비돼 그대로 재전송할 수 없다** — `onRequest`에서 `request.clone()`을 `WeakMap<Request, Request>`에 보관했다가 그 복제본으로 재전송한다. 멀티파트 업로드(Task 10 `ImageUploader`)도 같은 경로를 타므로 `client.test.ts`에 `FormData_본문도_재시도된다`를 둔다), 없으면 `onAuthLost()` 호출(`setOnAuthLost(fn)`으로 Task 7의 AuthProvider가 등록: QueryClient 초기화 + `/login?next=`). 응답이 `!ok`이면 `ErrorResponse`를 읽어 `ApiError`를 throw(`traceId = response.headers.get('X-Trace-Id')`), 네트워크 실패는 `ApiError(0, 'INTERNAL_ERROR', '네트워크 오류', null)`. `export function unwrap<T>(r: { data?: { data?: T } }): T`(ApiResponse 언래핑, 훅이 쓴다).
- Produces(`errors.ts`): `export type ErrorKind = 'field' | 'form' | 'toast' | 'page' | 'banner'`; `export const errorTable: Partial<Record<ErrorCode, { kind: ErrorKind; field?: string; message?: string }>>`(스펙 §10 그대로: `MEMBER_EMAIL_DUPLICATED {field,'email'}`, `AUTH_INVALID_CREDENTIALS {form}`, `SELLER_BUSINESS_NUMBER_DUPLICATED {field,'businessNumber'}`, `BRAND_NAME_DUPLICATED {field,'name'}`, `PRODUCT_INVALID_PRICE {field,'salePrice'}`, `CATEGORY_NOT_LEAF {field,'categoryId'}`, `SELLER_NOT_ACTIVE {banner}`, `UNAUTHORIZED {page}`, `FORBIDDEN {page}`, `PRODUCT_NOT_FOUND {page}`, `BRAND_NOT_FOUND {page}`, `CATEGORY_HAS_PRODUCTS {toast}`, `CATEGORY_HAS_CHILDREN {toast}`, `CHANGE_REQUEST_ALREADY_PENDING {toast, message:'이미 검수 중인 변경 요청이 있습니다. 철회한 뒤 다시 요청하세요.'}`, `IDEMPOTENCY_KEY_REUSED {form}`, `IDEMPOTENCY_IN_PROGRESS {toast, message:'같은 요청이 처리 중입니다. 잠시 후 확인하세요.'}`, `INVALID_REQUEST {form}`, `INTERNAL_ERROR {toast, message:'잠시 후 다시 시도해 주세요.'}`); `export function resolveError(e: unknown): { kind: ErrorKind; field?: string; message: string; traceId: string | null }`(표에 없으면 `{ kind: 'toast', message: e.message }`, `ApiError`가 아니면 `INTERNAL_ERROR` 취급).
- Produces(`idempotency.ts`): `export function newIdempotencyKey(): string`(`crypto.randomUUID()`), `export const IDEMPOTENCY_HEADER = 'Idempotency-Key'`.
- Produces(`useIdempotentSubmit.ts`): `export function useIdempotentSubmit<TValues>(form: UseFormReturn<TValues>, submit: (values: TValues, key: string) => Promise<void>): { onSubmit: (e?: BaseSyntheticEvent) => Promise<void>; submitting: boolean; retry: () => Promise<void> }` — 키는 `form.watch` 값이 마지막 제출 이후 바뀌면 새로 만들고(`form.formState.isDirty` 변화 + 값 스냅샷 비교), `retry`는 같은 키로 다시 호출, `submitting` 동안 두 번째 호출은 무시.
- Produces(`money.ts`): `export function assertSafeInt(n: number, label?: string): number`(아니면 `process.env.NODE_ENV !== 'production'`이면 throw, 운영이면 `NaN` 반환), `export function formatKrw(n: number): string`(`Intl.NumberFormat('ko-KR').format(n) + '원'`, `NaN`이면 `'금액 오류'`), `export function discountRate(originalPrice: number, salePrice: number): number`(`Math.floor(((orig - sale) * 100) / orig)`, `orig <= 0`이면 throw), `export function lineTotal(salePrice: number, extraPrice: number, quantity: number): number`, `export function parseWon(input: string): number | null`(숫자·콤마만 허용, 소수점·음수 null), `export function formatBp(bp: number): string`(`1000 → '10%'`, `1250 → '12.5%'`, `Math.floor(bp / 100)`·`bp % 100` 사용은 이 파일만 허용).
- Produces(`datetime.ts`): `export function formatDateTime(s: string): string`(`'2026-11-07T14:20:00' → '2026-11-07 14:20'`, 문자열 슬라이스), `export function formatDate(s: string): string`(`'2026-11-07'`), `export function toLocalDateTimeString(d: { y, m, d, hh, mm }): string`(`YYYY-MM-DDTHH:mm:00`).
- Produces(`query-keys.ts`): 계획자 판단 절의 `queryKeys` 객체(`as const`).

- [ ] **Step 1: 실패 테스트** — `money.test.ts`: `formatKrw는_27300을_27_300원으로_만든다`, `discountRate(39000, 27300)은_30`, `discountRate는_버림이다(10000, 6667 → 33)`, `정가_0이면_throw`, `lineTotal(27300, 2000, 3)은_87900`, `parseWon은_소수와_음수를_null로`, `안전_정수가_아니면_개발에서_throw`, `formatBp(1000)은_10퍼센트`. `datetime.test.ts`: `LocalDateTime_문자열을_Date_없이_표시한다`(`Date` 전역을 `vi.stubGlobal`로 throw하게 해도 통과). `errors.test.ts`: `MEMBER_EMAIL_DUPLICATED는_email_필드`, `SELLER_NOT_ACTIVE는_kind_banner`, `표에_없는_코드는_서버_메시지를_토스트로`, `ApiError가_아니면_INTERNAL_ERROR_토스트`. `idempotency.test.ts`: `키는_UUID_v4_형식`. `useIdempotentSubmit.test.tsx`(`renderHook` + `useForm`): `같은_시도의_retry는_같은_키`, `값을_고친_뒤_제출하면_새_키`, `제출_중_두_번째_제출은_무시`. `client.test.ts`(`vi.stubGlobal('fetch', …)` 순서 기록): `401이면_reissue_한_번_뒤_원_요청을_한_번_재시도한다`, `동시_401_세_건은_reissue를_한_번만_호출하고_모두_같은_토큰으로_재시도한다`, `auth_경로의_401은_재시도하지_않는다`, `reissue_실패면_토큰을_비우고_onAuthLost를_부른다`, `재시도도_401이면_다시_reissue하지_않는다`, `에러_응답은_ApiError에_code와_X_Trace_Id를_담는다`, `요청에_Bearer가_붙는다`.
- [ ] **Step 2: 실패 확인** — `pnpm test` FAIL. **Step 3: 구현**.
- [ ] **Step 4: PASS** — `pnpm test && pnpm lint && pnpm typecheck`(lint가 `money.ts` 밖 `/` 없음 확인).
- [ ] **Step 5: 커밋** `feat(frontend): API 클라이언트(401 단일 재발급), 에러 매핑, 멱등 제출, 금액·일시 유틸`

### Task 7: 인증 부트스트랩·로그인·가입, 역할별 레이아웃(헤더·콘솔)·가드·공용 컴포넌트·오류 페이지

**Files:**
- Create: `src/auth/{AuthProvider.tsx,useAuth.ts}`, `src/auth/guards/{RequireAuth,RequireRole}.tsx`, `src/app/providers.tsx`, `src/app/(store)/layout.tsx`, `src/app/(store)/login/page.tsx`, `src/app/(store)/signup/page.tsx`, `src/app/seller/layout.tsx`, `src/app/admin/layout.tsx`, `src/app/forbidden/page.tsx`, `src/app/not-found.tsx`, `src/app/error.tsx`, `src/features/auth/{api.ts,schemas.ts,components/LoginForm.tsx,components/SignupForm.tsx}`, `src/components/layout/{StoreHeader,CategoryNav,Footer,ConsoleShell,SideNav}.tsx`, `src/components/common/{StatusChip,Money,EmptyState,ErrorState,PageSkeleton,Pagination,ConfirmDialog}.tsx`
- Modify: `src/app/layout.tsx`(providers 감싸기), `src/app/page.tsx`(Task 9에서 목록으로 교체 전까지 `(store)/page.tsx`로 이동)
- Test: `src/features/auth/schemas.test.ts`, `src/components/common/StatusChip.test.tsx`, `src/auth/AuthProvider.test.tsx`

**Interfaces:**
- Consumes: Task 6 `api`, `unwrap`, `reissueOnce`, `setOnAuthLost`, `resolveError`, `useIdempotentSubmit`, `queryKeys.auth.me`.
- Produces(auth): `type Me = components['schemas']['MeResponse']`; `AuthProvider`(children) — 마운트 시 `reissueOnce()` → 토큰 있으면 `GET /api/v1/auth/me` → `status: 'loading' | 'anonymous' | 'authenticated'`; `useAuth(): { status; me: Me | null; login(email, password): Promise<Me>; logout(): Promise<void> }`(login = `POST /auth/login` → `setAccessToken` → `me` 조회; logout = `POST /auth/logout`(헤더 `REQUESTED_WITH`) → `setAccessToken(null)` → `queryClient.clear()` → `router.push('/')`). `setOnAuthLost`에 `queryClient.clear()` + `router.replace('/login?next=' + encodeURIComponent(pathname + search))` 등록.
  `RequireAuth({ children })`: `loading`이면 `PageSkeleton`, `anonymous`면 `/login?next=` 이동(렌더 null), 아니면 children. `RequireRole({ role: 'ADMIN', children })`: `RequireAuth` 안에서 `me.role !== role`이면 `router.replace('/forbidden')`.
- Produces(features/auth): `loginSchema = z.object({ email: z.string().email(), password: z.string().min(8) })`, `signupSchema = loginSchema.extend({ name: z.string().min(1).max(50) })`(OpenAPI `SignupRequest` 제약: password 8..72, name ≤ 50). `useSignup()`(`POST /auth/signup`, `Idempotency-Key` 헤더). 로그인 성공 후 이동: `next` 쿼리가 있으면 거기, 없으면 `me.role === 'ADMIN' ? '/admin' : '/'`. `409 MEMBER_EMAIL_DUPLICATED` → `form.setError('email')`, `401 AUTH_INVALID_CREDENTIALS` → 폼 상단 메시지(`resolveError.kind` 분기 공통 헬퍼 `applyFormError(form, err)`를 `features/auth/apply-form-error.ts`에 두고 이후 모든 폼이 쓴다).
- Produces(layout): `StoreHeader`(로고 → `/`, 검색 폼 → `/products?keyword=`, 유틸 내비: `anonymous` "로그인"·"가입", `authenticated` "판매자 센터"(→ `/seller`) · "관리자"(ADMIN만, → `/admin`) · "로그아웃"; `loading`이면 유틸 영역 `Skeleton`; 장바구니·마이·좋아요 아이콘은 두지 않는다), `CategoryNav({ categories })`(첫 항목 "핫딜"은 `aria-disabled` + "준비 중", "전체" + 대분류, 현재 `categoryId`에 `aria-current="page"`), `Footer`, `ConsoleShell({ role: '판매자 센터' | '관리자 콘솔', nav: NavItem[], userLabel, children })`(232px 사이드바 + `console__top` h1 + 본문; `NavItem { href, label, badge?: number, disabled?: boolean, soonLabel?: '준비 중' }`), `SideNav`(현재 경로 `startsWith`로 `aria-current`).
  `src/app/seller/layout.tsx`: `RequireAuth` + `ConsoleShell(role='판매자 센터')` + Task 10의 `SellerGate`(이 Task에서는 자리만: children 통과). `src/app/admin/layout.tsx`: `RequireRole('ADMIN')` + `ConsoleShell(role='관리자 콘솔', nav: 대시보드 `/admin`, 검수 `/admin/review`(배지는 Task 13), 카테고리 `/admin/categories`, 판매자 `/admin/sellers`, 핫딜 disabled '준비 중')`.
- Produces(common): `StatusChip({ kind: 'seller' | 'brand' | 'product' | 'changeRequest', status: string })`(스펙 §9-4 표 그대로, 칩 색 Global Constraints; 모르는 값은 `chip-paused`에 원문), `Money({ value, className? })`(`formatKrw`, `tabular-nums`), `EmptyState({ message, action?: { label, href | onClick } })`, `ErrorState({ error: unknown, onRetry })`(`resolveError` 메시지 + "다시 시도" + `traceId`가 있으면 "문의 시 전달: {traceId}" 12px), `PageSkeleton({ rows?: number })`, `Pagination({ page, totalPages, onChange })`(`nav aria-label="페이지"`, `aria-current="page"`, 버튼 40px), `ConfirmDialog({ open, title, description, confirmLabel, destructive?, onConfirm, onCancel })`.
  `error.tsx`: "문제가 발생했습니다" + "다시 시도"(`reset`) + traceId(`error` 가 `ApiError`면). `not-found.tsx`: "페이지를 찾을 수 없습니다" + 홈 링크. `forbidden/page.tsx`: "권한이 없습니다".

- [ ] **Step 1: 실패 테스트** — `schemas.test.ts`: `비밀번호_7자는_거부_8자는_통과`, `이메일_형식_검사`. `StatusChip.test.tsx`: `상품_PENDING_REVIEW는_검수_대기_텍스트`, `판매자_SUSPENDED는_정지`. `AuthProvider.test.tsx`(fetch mock): `reissue_200이면_me를_불러_authenticated`, `reissue_401이면_anonymous이고_me를_부르지_않는다`, `logout은_토큰을_비우고_쿼리를_초기화한다`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현** — 시안 `design/login.html`(Task 1 확정본)과 `.console`·`.site-header` 구조를 Tailwind 클래스로 옮긴다. shadcn `Toaster`를 `providers.tsx`에 두고 `aria-live="polite"`.
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 백엔드(`local,demo`)와 `pnpm dev`로 수동 확인: 가입 → 로그인 → 새로고침 유지 → `/admin` 접근 시 `/forbidden` → 로그아웃 → `/seller` 접근 시 `/login?next=%2Fseller`.
- [ ] **Step 5: 커밋** `feat(frontend): 인증 부트스트랩·로그인·가입, 구매자 헤더와 콘솔 레이아웃, 가드와 공용 컴포넌트`

### Task 8: 데모 바(`features/demo`) — 역할 전환·데이터 보충, 트리 셰이킹과 번들 문자열 검사

**Files:**
- Create: `src/features/demo/{DemoBar.tsx,api.ts,accounts.ts}`
- Modify: `src/app/layout.tsx`, `scripts/check-bundle.sh`(`--skip-demo-check` 제거), `frontend/.env.example`(설명 보강)
- Test: `src/features/demo/DemoBar.test.tsx`, `pnpm check:bundle`

**Interfaces:**
- Consumes: Task 7 `useAuth().login/logout`, Task 6 `api`.
- Produces: `accounts.ts` — `DEMO_ACCOUNTS = { buyer: 'buyer@kairos.demo', seller: 'seller@kairos.demo', admin: 'admin@kairos.demo' } as const`, `DEMO_PASSWORD = process.env.NEXT_PUBLIC_DEMO_PASSWORD ?? ''`. `DemoBar`: 화면 하단 고정 32px 바(배경 `surface-2`, 글자 12px): "데모" 라벨 + 버튼 "구매자로 전환"·"판매자로 전환"·"관리자로 전환"(`logout` 뒤 해당 계정 `login`, 완료 후 역할 기본 페이지) + "데모 데이터 보충"(관리자 계정으로 로그인 → `POST /api/v1/demo/reseed` → 토스트에 생성 건수 → `queryClient.invalidateQueries()`). `src/app/layout.tsx`: 정적 `import { DemoBar } from '@/features/demo/DemoBar'`는 **쓰지 않는다** — 앱 소스는 `sideEffects` 플래그가 없어 webpack이 사용처가 죽은 코드여도 모듈 자체는 남긴다. 대신 상수 조건의 **죽은 분기 안에서 동적 import**를 한다: `const DemoBar = process.env.NEXT_PUBLIC_DEMO_MODE === 'true' ? dynamic(() => import('@/features/demo/DemoBar'), { ssr: false }) : null;` 그리고 `{DemoBar ? <DemoBar /> : null}`. **반드시 리터럴 비교식**으로 써야 Next가 빌드 시 상수 폴딩으로 분기와 `import()` 청크를 함께 제거한다. `check:bundle`이 이를 증명한다(운영 빌드에서 `DemoBar`·`kairos.demo` 0건, 데모 빌드에서 존재).

- [ ] **Step 1: 실패 테스트** — `DemoBar.test.tsx`: `판매자로_전환은_logout_뒤_seller_계정으로_login한다`, `데이터_보충은_관리자_로그인_뒤_reseed를_부른다`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**.
- [ ] **Step 4: 검증** — `pnpm test`, `pnpm check:bundle`(두 빌드 모두 기대대로: 운영 빌드 grep 0건, 데모 빌드 grep 매칭). `grep -rn "KAIROS_ADMIN" src/`가 0건.
- [ ] **Step 5: 커밋** `feat(frontend): 데모 역할 전환·데이터 보충 바(NEXT_PUBLIC_DEMO_MODE 전용)와 번들 검사`

### Task 9: 구매자 화면 — 상품 목록·검색·브랜드·상품 상세(SKU 매칭·좋아요)

**Files:**
- Create: `src/app/(store)/page.tsx`(목록과 같은 컴포넌트), `src/app/(store)/products/page.tsx`, `src/app/(store)/products/[productId]/page.tsx`, `src/app/(store)/brands/[brandId]/page.tsx`, `src/features/catalog/{api.ts,search-params.ts,sku-match.ts}`, `src/features/catalog/components/{CategoryTree,FilterPanel,SortBar,ProductCard,ProductGrid,ProductListPage,ProductGallery,OptionSelector,QuantityInput,LikeButton}.tsx`
- Test: `src/features/catalog/{search-params,sku-match}.test.ts`

**Interfaces:**
- Consumes: `GET /api/v1/categories`(`CategoryNode[]`), `GET /api/v1/products`(파라미터 `categoryId, brandId, minPrice, maxPrice, keyword, sort: ProductSort, page, size`), `GET /api/v1/products/{productId}`(`ProductDetail`: `imageUrls, options, skus[{skuId, optionValueIds, extraPrice, soldOut}], liked`), `GET /api/v1/brands/{brandId}`, `POST/DELETE /api/v1/products/{productId}/like`. 공용 컴포넌트·`Money`·`Pagination`·`useAuth`.
- Produces(`search-params.ts`): `type ProductListParams = { categoryId?: number; brandId?: number; minPrice?: number; maxPrice?: number; keyword?: string; sort: ProductSort; page: number; size: number }`, `parseProductListParams(sp: URLSearchParams): ProductListParams`(기본 `sort 'LATEST'`, `page 0`, `size 20`, 잘못된 값은 기본값), `toSearchString(p: ProductListParams): string`(기본값은 생략). **URL이 단일 진실**: 필터·정렬·검색·페이지 변경은 전부 `router.push('/products?' + toSearchString(next))`.
- Produces(`sku-match.ts`): `findSku(skus: Sku[], selected: Record<optionId, valueId>): Sku | null`(모든 축이 선택됐을 때 `optionValueIds` 집합 일치), `disabledValues(skus, options, selected): Set<valueId>`(현재 선택과 조합 가능한 SKU가 전부 `soldOut`이거나 없는 값), `selectionTotal(salePrice, sku, qty) = lineTotal(salePrice, sku.extraPrice, qty)`.
- Produces(api hooks): `useCategories()`, `useProducts(params)`(`keepPreviousData`), `useProduct(id)`, `useBrand(id)`, `useToggleLike(productId)`(비로그인이면 호출 전 `/login?next=` 이동; 성공 시 `queryKeys.catalog.product(id)`·`products` invalidate).
- 화면: `/products` — 좌측 `CategoryTree`(대분류 접기/펼치기, 현재 항목 `aria-current`), `FilterPanel`(브랜드는 목록 응답의 brand를 모아 셀렉트, 가격 min/max 숫자 입력 `parseWon`), `SortBar`(5개 버튼 `aria-pressed`), `ProductGrid` 4열 카드(대표 이미지 `<img>`, 브랜드명, 상품명, `discountRate`%, `Money salePrice`, 정가 취소선, `soldOut` 배지, 좋아요 수·별점은 표시만), 상단 "핫딜" 배너 자리는 "준비 중" 한 줄. 상태: 로딩 `PageSkeleton`(카드 8개 골격), 0건은 필터가 있으면 "조건에 맞는 상품이 없습니다 → 필터 초기화", 없으면 "등록된 상품이 없습니다", 오류 `ErrorState`. `/brands/[id]` — `useBrand` 헤더(로고·이름·소개) + `ProductListPage`에 `brandId` 고정. `/products/[id]` — 갤러리(대표 + 썸네일, 키보드 선택), 브랜드 링크, 정가·할인율·판매가, `OptionSelector`(축별 버튼 `aria-pressed`, `disabledValues`는 `disabled` + "품절"), `QuantityInput`(1 이상 정수), 합계 `Money`(SKU 미선택이면 "옵션을 선택하세요"), `LikeButton`(`aria-pressed`, `liked === null`이면 미선택 표시), 장바구니·구매 버튼은 **숨기고** "구매 기능은 곧 열립니다" 문구. `404 PRODUCT_NOT_FOUND` → `notFound()`.

- [ ] **Step 1: 실패 테스트** — `search-params.test.ts`: `기본값은_LATEST_0_20`, `잘못된_sort는_LATEST로`, `toSearchString은_기본값을_생략한다`, `minPrice가_maxPrice보다_크면_둘_다_버린다`. `sku-match.test.ts`: `모든_축을_고르면_optionValueIds가_같은_SKU_하나`, `축이_덜_골라지면_null`, `남은_조합이_전부_품절인_값은_disabled`, `selectionTotal은_추가_금액을_수량만큼_곱한다`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**(시안 `user-product-list.html`·`user-product-detail.html`).
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 수동: 데모 데이터로 필터·정렬·검색·페이지 → URL 변경 → 뒤로 가기 복원, 상세에서 품절 조합 비활성, 비로그인 좋아요 → 로그인 이동.
- [ ] **Step 5: 커밋** `feat(frontend): 구매자 상품 목록·검색·브랜드·상세 화면`

### Task 10: 판매자 — 입점 신청, `SellerGate` 상태별 진입, 대시보드, 브랜드

**Files:**
- Create: `src/app/seller/page.tsx`, `src/app/seller/apply/page.tsx`, `src/app/seller/brands/page.tsx`, `src/features/seller/gate/{SellerGate.tsx,useSellerMe.ts}`, `src/features/seller/apply/{api.ts,schemas.ts,components/ApplyForm.tsx,components/ApplicationSummary.tsx}`, `src/features/seller/brands/{api.ts,schemas.ts,components/BrandCard.tsx,components/BrandDialog.tsx}`, `src/features/seller/dashboard/{api.ts,components/StatusCards.tsx,components/RecentProducts.tsx}`
- Modify: `src/app/seller/layout.tsx`(`SellerGate` 연결, 사이드 메뉴: 대시보드 `/seller`, 상품 관리 `/seller/products`, 브랜드 `/seller/brands`, 정산 disabled '준비 중'), `src/components/layout/ConsoleShell.tsx`(`banner?: ReactNode` prop 추가)
- Create(공용, 브랜드 로고 `max=1`로 먼저 쓰고 Task 11 상품 이미지 `max=10`이 재사용): `src/components/common/ImageUploader.tsx`, `src/features/files/api.ts`
- Test: `src/features/seller/apply/schemas.test.ts`, `src/features/seller/gate/SellerGate.test.tsx`, `src/components/common/ImageUploader.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/seller/me`(404 `SELLER_NOT_FOUND` = 미신청), `POST/PUT /api/v1/seller/application`(`SellerApplicationRequest`: `companyName, businessNumber(10자리), representativeName, phone, bank, accountNumber, holder`), `GET/POST /api/v1/seller/brands`, `PUT /api/v1/seller/brands/{brandId}`(`BrandRequest`: `name, englishName, logoFileId, description`), `POST /api/v1/files/images`(multipart `file` → `UploadResult{fileId, url}`), `GET /api/v1/seller/products?status&size=1`(대시보드 수치).
- Produces(gate): `useSellerMe(): { status: 'loading' | 'none' | SellerStatus; seller: SellerDetail | null; error }`(404는 `'none'`, 그 외 에러는 throw). `SellerGate({ children })`: `none` → 입점 안내 + "입점 신청" 링크(사이드 메뉴 숨김, `ConsoleShell nav=[]`); `PENDING` → `ApplicationSummary` 읽기 전용 + "심사 중" `notice` + "수정"(→ `/seller/apply`); `REJECTED` → `rejectReason` 경고 + `ApplyForm`(PUT); `APPROVED` → children; `SUSPENDED` → 상단 배너 "판매자 정지 중: {suspendReason}" + children. `useSellerCapabilities(): { canManage: boolean }`(`APPROVED`만 true)을 export해 등록·수정·브랜드 신청 버튼이 `disabled` + `title="정지 중"`. 서버 `409 SELLER_NOT_ACTIVE`(`kind: 'banner'`)는 `ConsoleShell`의 배너 슬롯에 표시(Task 7 `ConsoleShell`에 `banner?: ReactNode` prop 추가).
- Produces(apply): `applySchema`(`businessNumber`: 입력 중 하이픈 제거 `transform`, `/^\d{10}$/`; 나머지 `min(1)`, OpenAPI `maxLength`와 같게), `useApply()`·`useUpdateApplication()`. 에러: `SELLER_BUSINESS_NUMBER_DUPLICATED` → 필드, `SELLER_ALREADY_EXISTS` → `/seller`로 이동.
- Produces(brands): `brandSchema`(`name 1..50`, `englishName 1..50`, `logoFileId number`, `description ≤ 500` — OpenAPI `BrandRequest` 제약을 읽어 맞춘다), `useBrands()`, `useCreateBrand()`, `useUpdateBrand(id)`. `BrandCard`(로고·이름·`StatusChip kind='brand'`·반려 사유·반려면 "수정"), `BrandDialog`(`mode: 'create' | 'edit'`). `BRAND_NAME_DUPLICATED` → `name` 필드.
- Produces(ImageUploader): `ImageUploader({ value: { fileId: number; url: string }[], onChange, max: number, label })` — `input type=file accept="image/jpeg,image/png,image/webp"`, 선택 즉시 클라이언트 검증(5MB 이하·형식, 아니면 인라인 오류) → `POST /files/images` → `value`에 추가; 각 항목 "위로"·"아래로"·"삭제" 버튼(`aria-label`), 첫 장에 "대표" 배지; 업로드 중 항목 스켈레톤; 서버 `FILE_UNSUPPORTED_FORMAT`은 항목 오류. `useUploadImage()`는 `features/files/api.ts`.
- Produces(dashboard): `useStatusCounts(): { PENDING_REVIEW, ON_SALE, PAUSED, REJECTED, ENDED }`(5개 `useQueries`, `size=1`의 `totalElements`), `StatusCards`(4칸: 검수 대기/판매 중/일시 중지/반려·종료(합), 각 `/seller/products?status=` 링크), `RecentProducts`(`GET /seller/products?size=5` 표: 이미지·상품명·칩·`formatDate(createdAt)`), 바로 가기 "상품 등록"·"브랜드 신청"(`canManage` 아니면 disabled).

- [ ] **Step 1: 실패 테스트** — `apply/schemas.test.ts`: `사업자번호_하이픈은_제거되고_10자리만_통과`, `9자리는_거부`. `SellerGate.test.tsx`(`useSellerMe` mock): `404면_입점_안내와_신청_링크`, `PENDING이면_심사_중_안내와_읽기_전용`, `REJECTED면_사유와_수정_폼`, `SUSPENDED면_정지_배너와_children`, `SUSPENDED면_등록_버튼이_disabled이고_툴팁이_정지_중`(`useSellerCapabilities`). `ImageUploader.test.tsx`: `6MB_파일은_업로드하지_않고_오류를_보인다`, `gif는_거부한다`, `아래로_버튼은_순서를_바꾼다`, `max를_넘으면_입력이_disabled`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**(시안 `seller-dashboard.html` 확정본).
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 수동: 새 계정으로 `/seller` → 안내 → 신청 → 심사 중 → (데모 관리자로 승인) → 전체 메뉴·대시보드 수치 → 브랜드 신청 → 반려 → 수정.
- [ ] **Step 5: 커밋** `feat(frontend): 판매자 입점 신청·상태별 콘솔 진입·대시보드·브랜드, 이미지 업로더`

### Task 11: 판매자 — 상품 관리 목록(상태 요약·행 작업·판매가 수정), 상품 등록 폼(옵션 축·SKU 미리 보기)

**Files:**
- Create: `src/app/seller/products/page.tsx`, `src/app/seller/products/new/page.tsx`, `src/features/seller/products/{api.ts,schemas.ts,sku-preview.ts,sku-impact.ts}`, `src/features/seller/products/components/{StatusSummary,ProductTable,PriceDialog,ProductForm,OptionAxesEditor,SkuPreviewTable,CategorySelect,BrandSelect}.tsx`
- Test: `src/features/seller/products/{schemas,sku-preview,sku-impact}.test.ts`

**Interfaces:**
- Consumes: `GET /api/v1/seller/products?status&page&size`(`SellerProductSummary` — Task 3 필드 포함), `POST /api/v1/seller/products`(`RegisterProductRequest`: `brandId, categoryId, name, description, imageFileIds[1..10], originalPrice, salePrice, options[0..2]{name, values[]}` — 정확한 필드명·제약은 `schema.d.ts`를 읽는다), `PATCH /api/v1/seller/products/{id}/price`(`ChangeSalePriceRequest{salePrice}`), `POST …/pause|resume|end`, `GET /api/v1/categories`, `GET /api/v1/seller/brands`(APPROVED만 선택). Task 10 `useSellerCapabilities`, `ImageUploader`, Task 6 `discountRate`·`parseWon`·`useIdempotentSubmit`.
- Produces(`schemas.ts`): `productSchema` — `brandId number`, `categoryId number`, `name 1..100`, `description 1..2000`, `images 1..10`, `originalPrice`/`salePrice`: 문자열 입력을 `parseWon`으로 `transform`, `originalPrice ≥ 1`, `1 ≤ salePrice ≤ originalPrice`(`superRefine`, 경로 `salePrice`), `options`: 0..2개, 각 `name 1..20`, `values 1..20개` 각 `1..20자`, 축 이름 중복·값 중복 거부(길이 제약은 OpenAPI 값으로 맞춘다). `priceSchema`(`salePrice ≤ originalPrice`).
- Produces(`sku-preview.ts`): `cartesian(options: { name: string; values: string[] }[]): string[][]`(축 0개면 `[[]]` 1행), `previewRows(options): { displayName: string }[]`(`" / "` 연결).
- Produces(`sku-impact.ts`, Task 12·13도 사용): `type ChangeOption = { optionId: number | null; name: string; values: { valueId: number | null; name: string }[] }`; `skuImpact(current: { options: OptionResult[]; skus: { skuId: number; optionValueIds: number[]; displayName: string }[] }, next: ChangeOption[]): { kept: string[]; added: string[]; discontinued: string[] }` — 유지 = 다음 옵션의 값 ID(비null) 조합과 일치하는 기존 SKU, 단종 = 그 외 기존 SKU, 신규 = 새 조합(값 ID가 null이거나 기존 조합에 없음).
- Produces(api): `useSellerProducts(params)`, `useRegisterProduct()`, `useChangePrice(id)`, `usePause/useResume/useEnd(id)`(성공 시 `seller.products`·`seller.product(id)` invalidate, 토스트).
- 화면: `/seller/products` — `StatusSummary` 5칸(`aria-pressed`로 상태 필터, URL `?status=`가 진실, 선택 칸에만 `totalElements` 표시, 나머지 칸은 숫자 없이 라벨만 — 스펙 §9-2), 표(이미지·상품명·브랜드·`Money`·`StatusChip` + `hasPendingChangeRequest`면 "변경 요청 검수 중" 배지·`soldOut` 배지·`stockTotal`·`formatDate(createdAt)`), 행 작업: 상세 링크, 일시 중지/재개(상태에 따라 하나), 종료(`ConfirmDialog` "되돌릴 수 없습니다"), 판매가 수정(`PriceDialog`: 정가 표시, 입력, 할인율 미리 보기 `discountRate`). `canManage` false면 재개·판매가 수정 disabled. 상태: 로딩 표 골격, 0건 "등록한 상품이 없습니다 → 상품 등록"(필터 있으면 "해당 상태의 상품이 없습니다"), 오류. `/seller/products/new` — `ProductForm(mode='create')`: `BrandSelect`(APPROVED 브랜드 없으면 "승인된 브랜드가 없습니다 → 브랜드 신청"), `CategorySelect`(대분류 → 소분류 2단), 상품명·설명, `ImageUploader max=10`, 정가·판매가 + 할인율 힌트, `OptionAxesEditor`(축 추가 ≤ 2, 값 태그 입력: Enter로 추가, Backspace로 삭제), `SkuPreviewTable`(읽기 전용, "재고·추가 금액은 등록 후 상세에서 입력합니다"), 제출(`useIdempotentSubmit`, 제출 중 `fieldset disabled`). 성공 → 토스트 "검수 대기 상태로 등록됐습니다" → `/seller/products/{id}`. `CATEGORY_NOT_LEAF` → `categoryId` 필드.

- [ ] **Step 1: 실패 테스트** — `schemas.test.ts`: `판매가가_정가보다_크면_salePrice_필드_에러`, `소수_입력은_거부한다`, `이미지_0장은_거부`, `옵션_축_3개는_거부`, `같은_축_안_값_중복은_거부`. `sku-preview.test.ts`: `색상2_사이즈2는_4행`, `축_없음은_1행`. `sku-impact.test.ts`: `값_ID를_유지하면_kept`, `새_값을_더하면_신규_조합만_added`, `축_하나를_없애면_남은_축의_값마다_신규_1개_기존_조합은_전부_단종`, `축_0개에서_1개로_가면_기존_단일_SKU는_단종`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**(시안 `seller-product-list.html`·`seller-product-form.html`).
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 수동: 등록(이미지 2장, 옵션 2축) → 목록에 검수 대기 → 판매가 수정 할인율 미리 보기 → 키보드만으로 등록 폼 끝까지 제출 가능(Tab 순서 확인).
- [ ] **Step 5: 커밋** `feat(frontend): 판매자 상품 관리 목록과 상품 등록 폼`

### Task 12: 판매자 — 상품 상세(SKU 편집·판매 상태·변경 요청 철회), 상품 수정(변경 요청)

**Files:**
- Create: `src/app/seller/products/[productId]/page.tsx`, `src/app/seller/products/[productId]/edit/page.tsx`, `src/features/seller/products/components/{SkuEditTable,ChangeRequestCard,ProductStatusActions,ProductSummaryCard}.tsx`
- Modify: `src/features/seller/products/{api.ts,schemas.ts}`, `components/ProductForm.tsx`(`mode: 'create' | 'change-request'`, `initial`)
- Test: `src/features/seller/products/components/SkuEditTable.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/seller/products/{id}`(`SellerProductDetail`: `skus[{skuId, displayName, extraPrice, stock, onSale, skuStatus}]`, `pendingChangeRequestId`, `rejectReason`), `PATCH …/skus/{skuId}`(`UpdateSkuRequest{extraPrice, stock, onSale}` — 필드는 `schema.d.ts` 확인), `POST …/change-requests`(`ProductContentRequest` + `originalPrice`: `ChangeRequestDetail.content` 기준 — 상품명·설명·카테고리·이미지·옵션(`optionId`·`valueId` 유지 시 포함)·정가), `GET …/change-requests/{id}`(있으면; 없으면 관리자 조회를 쓰지 않고 `pendingChangeRequestId`만 카드에 표시), `DELETE …/change-requests/{id}`. Task 11 `skuImpact`, `ProductForm`.
- Produces: `useSellerProduct(id)`, `useUpdateSku(productId)`(행 단위 저장, 성공 시 상세 invalidate), `useCreateChangeRequest(productId)`, `useWithdrawChangeRequest(productId)`. `SkuEditTable({ skus, onSave(skuId, values), disabled })`: 행마다 추가 금액(`parseWon`, 0 이상)·재고(0 이상 정수)·판매 `Switch role="switch"`·"저장"(변경된 행만 활성); `DISCONTINUED` 행은 읽기 전용 + "단종". `ProductStatusActions`(일시 중지/재개/종료 — Task 11 훅 재사용). `ChangeRequestCard`(대기 중이면 "검수 중" 칩 + 철회 `ConfirmDialog`; 없으면 "내용 수정(재검수)" → `/edit`). `skuSchema`(`extraPrice ≥ 0`, `stock ≥ 0` 정수).
- 화면: `/seller/products/[id]` — 상단 `ProductSummaryCard`(대표 이미지·이름·브랜드·`StatusChip`·반려 사유·정가/할인율/판매가·판매가 수정 `PriceDialog`), `ProductStatusActions`, `SkuEditTable`, `ChangeRequestCard`. `canManage` false면 편집 전부 disabled(조회·일시 중지·종료만). `/seller/products/[id]/edit` — `ProductForm(mode='change-request', initial=상세)`(브랜드·판매가는 변경 불가: 읽기 전용 표시), 옵션 축을 바꾸면 폼 아래 `skuImpact` 결과를 "유지 n / 신규 n(재고 0) / 단종 n" + 목록으로 경고, 제출 → `/seller/products/[id]`로 이동 + 토스트 "변경 요청이 접수됐습니다". `409 CHANGE_REQUEST_ALREADY_PENDING` → 토스트 + 상세로 이동. 상품 `ENDED`면 수정 진입 버튼 숨김.

- [ ] **Step 1: 실패 테스트** — `SkuEditTable.test.tsx`: `값을_바꾼_행만_저장_버튼이_활성`, `음수_재고는_저장하지_않는다`, `DISCONTINUED_행은_입력이_없다`, `disabled면_모든_입력이_비활성`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**.
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 수동: SKU 재고 0 저장 → 구매자 상세 품절, 변경 요청 등록 → 목록 배지 → 철회.
- [ ] **Step 5: 커밋** `feat(frontend): 판매자 상품 상세(SKU 편집·판매 상태·변경 요청)와 변경 요청 폼`

### Task 13: 관리자 — 검수(4탭 큐·상세·승인/반려·변경 전후 diff)·대시보드·검수 배지

**Files:**
- Create: `src/app/admin/page.tsx`, `src/app/admin/review/page.tsx`, `src/features/admin/review/{api.ts,review-params.ts,diff.ts}`, `src/features/admin/review/components/{QueueTabs,QueueTable,SellerDetailCard,BrandDetailCard,ProductDetailCard,ChangeRequestDiff,ApproveSellerDialog,RejectDialog}.tsx`, `src/features/admin/dashboard/{api.ts,components/PendingCards.tsx,components/OldestPending.tsx}`
- Modify: `src/app/admin/layout.tsx`(검수 메뉴 배지 = 4탭 대기 수 합)
- Test: `src/features/admin/review/{review-params,diff}.test.ts`

**Interfaces:**
- Consumes: `GET /api/v1/admin/sellers?status&sort&page&size`(`SellerDetail`), `/admin/brands`(`BrandDetail`), `/admin/products`(`AdminProductSummary`), `/admin/change-requests`(`ChangeRequestSummary`), `GET /admin/products/{id}`(`SellerProductDetail`), `GET /admin/change-requests/{id}`(`ChangeRequestDetail`), `POST /admin/sellers/{id}/approve`(`ApproveSellerRequest{commissionRateBp}`), `POST /admin/sellers|brands|products|change-requests/{id}/reject`(`{reason}`), `POST …/approve`. Task 11 `skuImpact`, Task 6 `formatBp`.
- Produces(`review-params.ts`): `type ReviewTab = 'sellers' | 'brands' | 'products' | 'change-requests'`; `parseReviewParams(sp): { tab: ReviewTab; id: number | null; page: number; sort: 'LATEST' | 'OLDEST' }`(기본 `tab 'sellers'`, `sort 'OLDEST'`), `toReviewSearch(p)`. 기본 상태 필터: sellers/brands/change-requests `PENDING`, products `PENDING_REVIEW`.
- Produces(`diff.ts`): `type FieldDiff = { field: 'name' | 'description' | 'categoryId' | 'images' | 'options' | 'originalPrice'; before: unknown; after: unknown; changed: boolean }`; `contentDiff(current: SellerProductDetail, request: ChangeRequestDetail): FieldDiff[]`(이미지는 `fileId` 배열 비교 + 신규 `fileId` 집합, 옵션은 `skuImpact(current, request.content.options)`를 함께 반환: `{ diffs, newImageIds: Set<number>, impact }`).
- Produces(api): `useQueue(tab, params)`, `useQueueCount(tab)`(`size=1` `totalElements`, `refetchInterval 30_000`), `useQueueCounts()`(4개), `useAdminProduct(id)`, `useAdminChangeRequest(id)`, `useApprove(tab)`, `useReject(tab)`(성공 시 해당 큐·카운트 invalidate, 선택 행 제거 후 **다음 행**(없으면 이전 행)을 `?id=`로 선택).
- 화면: `/admin/review` — `QueueTabs`(`role="tablist"`, `aria-selected`, 각 탭 `count`), 좌측 `QueueTable`(열: 이름 · 판매자(`sellerName`; 판매자 탭은 `companyName`, 브랜드 탭은 `sellerId`만 있으므로 생략) · 요청일(`formatDateTime(createdAt ?? requestedAt)`, Task 3 필드) · 상태 칩, 정렬 셀렉트 오래된/최신 → `sort`, 행 선택 `aria-current="true"`, `Pagination`), 우측 상세 카드(탭별: 판매자 — 회사·사업자번호·대표·연락처·계좌; 브랜드 — 로고·이름·영문명·소개; 상품 — 이미지·이름·설명·카테고리·정가/판매가·옵션·SKU; 변경 요청 — `ChangeRequestDiff`: 필드별 전/후 2열, 바뀐 필드만 강조, 신규 이미지 "신규" 배지, 옵션 변경 시 "유지/신규/단종" 목록). 승인: 판매자는 `ApproveSellerDialog`(`commissionRateBp` 정수 입력 기본 `1000`, 미리 보기 `formatBp`), 나머지는 `ConfirmDialog`. 반려: `RejectDialog`(사유 `1..500자` 필수). 상태: 큐 로딩 골격, 0건 "대기 중인 건이 없습니다", 상세 미선택 "왼쪽에서 건을 선택하세요", 오류. `/admin` — `PendingCards` 4칸(각 `/admin/review?tab=`) + `OldestPending`(4탭 `sort=OLDEST&size=2`의 8행을 `createdAt`/`requestedAt` 오름차순으로 합쳐 앞 5행; 열은 시안대로 종류·이름·판매자(`sellerName`/`companyName`, 브랜드는 "-")·요청일·"열기" → `/admin/review?tab=&id=`) + 바로 가기.

- [ ] **Step 1: 실패 테스트** — `review-params.test.ts`: `기본은_sellers_OLDEST`, `잘못된_tab은_sellers`. `diff.test.ts`: `이름만_바뀌면_name만_changed`, `이미지_추가는_newImageIds에_들어간다`, `옵션_값_추가는_impact_added에_표시된다`, `정가_변경은_originalPrice_changed`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**(시안 `admin-review.html`·`admin-dashboard.html` 확정본).
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 수동: 데모 관리자로 4탭 각각 승인·반려 1건, 변경 요청 diff 확인, 키보드만으로 승인(Tab → 행 "열기" → 승인 → 다이얼로그 확인).
- [ ] **Step 5: 커밋** `feat(frontend): 관리자 검수 화면(4탭 큐·상세·변경 전후 비교)과 대시보드`

### Task 14: 관리자 — 카테고리 관리, 판매자 관리(정지·해제)

**Files:**
- Create: `src/app/admin/categories/page.tsx`, `src/app/admin/sellers/page.tsx`, `src/features/admin/categories/{api.ts,schemas.ts,components/CategoryTreeEditor.tsx,components/CategoryDialog.tsx}`, `src/features/admin/sellers/{api.ts,components/SellerTable.tsx,components/SuspendDialog.tsx}`
- Test: `src/features/admin/categories/schemas.test.ts`

**Interfaces:**
- Consumes: `GET /api/v1/categories`(`CategoryNode[]`), `POST /admin/categories`(`CategoryRequest{parentId?, name, displayOrder}`), `PUT /admin/categories/{id}`, `DELETE /admin/categories/{id}`(409 `CATEGORY_HAS_PRODUCTS`/`CATEGORY_HAS_CHILDREN`), `GET /admin/sellers?status&page&size`, `POST /admin/sellers/{id}/suspend`(`{reason}`), `POST …/unsuspend`.
- Produces: `categorySchema`(`name 1..50`, `displayOrder 0 이상 정수` — OpenAPI 제약 확인), `useCreateCategory/useUpdateCategory/useDeleteCategory`(성공 시 `catalog.categories` invalidate), `CategoryTreeEditor`(대분류 행 + 접히는 소분류 행, 각 행 "수정"·"삭제", 대분류 행에 "소분류 추가", 상단 "대분류 추가"; 삭제는 `ConfirmDialog`, 409는 토스트), `CategoryDialog(mode, parentId, initial)`. `useAdminSellers(params)`, `useSuspend/useUnsuspend(id)`, `SellerTable`(회사명·사업자번호·`StatusChip`·수수료 `formatBp`·정지 사유·작업: `APPROVED`면 "정지", `SUSPENDED`면 "해제"), `SuspendDialog`(사유 필수). 상태 필터 URL `?status=`(전체 기본). 세 상태(로딩·빈·오류) 공용 컴포넌트.

- [ ] **Step 1: 실패 테스트** — `schemas.test.ts`: `displayOrder_음수는_거부`, `이름_공백은_거부`.
- [ ] **Step 2: 실패 확인**, **Step 3: 구현**(시안 없음, §4 토큰·공용 컴포넌트로).
- [ ] **Step 4: 검증** — `pnpm test && pnpm lint && pnpm typecheck && pnpm build`. 수동: 소분류 추가 → 상품 등록 셀렉트에 반영, 상품 있는 소분류 삭제 → 토스트, 판매자 정지 → 판매자 콘솔 배너 → 해제.
- [ ] **Step 5: 커밋** `feat(frontend): 관리자 카테고리 관리와 판매자 정지·해제`

### Task 15: Playwright E2E 3개(실제 백엔드), CI 워크플로 `frontend.yml`, README

**Files:**
- Create: `frontend/playwright.config.ts`, `frontend/e2e/fixtures/api.ts`, `frontend/e2e/{auth,c1-seller-onboarding,c1-buyer-browse}.spec.ts`, `frontend/e2e/assets/sample.webp`, `.github/workflows/frontend.yml`
- Modify: `frontend/package.json`(`e2e` 스크립트, `@playwright/test` devDependency), `README.md`

**Interfaces:**
- Produces(config): `playwright.config.ts` — `testDir: 'e2e'`, `use.baseURL: 'http://localhost:3000'`, `viewport { 1440, 900 }`, `webServer: { command: 'pnpm dev', url: 'http://localhost:3000', reuseExistingServer: true, env: { NEXT_PUBLIC_DEMO_MODE: 'false' } }`, 단일 프로젝트 chromium, `retries 0`. 필요 env: `BACKEND_URL`(기본 `http://localhost:8080`), `KAIROS_DEMO_PASSWORD`(데모 관리자 로그인용, 없으면 spec이 `test.skip`으로 이유 출력).
- Produces(fixtures/api.ts): `createUser(request, { name }): { email, password, accessToken }`(이메일 `e2e-${Date.now()}-${rand}@kairos.test`), `loginToken(request, email, password)`, `adminToken(request)`(`admin@kairos.demo`), `applySeller(request, token, businessNumber = 10자리 랜덤)`, `approveSeller(request, adminToken, sellerId, bp = 1000)`, `createApprovedBrand(request, token, adminToken, name = 'E2E 브랜드 ' + rand)`, `uploadImage(request, token, path)`, `leafCategoryId(request)`, `uiLogin(page, email, password)`(화면 로그인). 모든 요청에 `X-Requested-With`는 불필요(Bearer).
- 흐름(스펙 §16 그대로): `auth.spec` — 화면 가입 → 로그인 → 헤더에 이름 → 새로고침 뒤 유지 → 로그아웃 → `/seller` 접근 시 `/login?next=%2Fseller`로 **클라이언트 이동**(`page.waitForURL`) → 로그인 뒤 `/seller`로 복귀 → `/admin` 접근 시 `/forbidden`. `c1-seller-onboarding.spec` — 화면: 입점 신청 → (API) 관리자 승인은 **화면**으로(검수 탭 판매자 → 수수료 1000 입력 → 승인, 이 단계는 `page.keyboard`만 사용) → 화면 브랜드 신청 → (API) 승인 → 화면 상품 등록(이미지 `sample.webp` 업로드, 옵션 색상 2 × 사이즈 2, **키보드만**으로 제출) → 화면 관리자 상품 승인 → 구매자 목록에서 키워드 검색으로 보임 → 상세에서 옵션 선택 합계 → 판매자 상세에서 SKU 4개 재고 0 → 구매자 상세 품절 → 판매자 일시 중지 → 목록에서 사라짐. `c1-buyer-browse.spec` — 데모 데이터 기준(`totalElements ≥ 20` 전제, 아니면 skip): 카테고리 클릭 → URL `categoryId`, 정렬 `PRICE_ASC` → 첫 카드 가격 ≤ 둘째, 검색어 → URL `keyword`, 2페이지 → URL `page=1`, 뒤로 가기 두 번 → 이전 URL·결과 복원.
- Produces(CI): `.github/workflows/frontend.yml` — `on: pull_request`·`push`(branches `dev`, `main`) with `paths: ['frontend/**', 'docs/api/openapi.yaml', '.github/workflows/frontend.yml']`; job `frontend`(`ubuntu-latest`, `defaults.run.working-directory: frontend`): `actions/checkout@v4`, `pnpm/action-setup@v4`(version from `packageManager`), `actions/setup-node@v4`(`node-version-file: frontend/.nvmrc`, `cache: pnpm`, `cache-dependency-path: frontend/pnpm-lock.yaml`), `pnpm install --frozen-lockfile`, `pnpm check:api`, `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`(env `NEXT_PUBLIC_DEMO_MODE` 미설정), `pnpm check:bundle`. E2E는 돌리지 않는다(주석으로 이유: 백엔드 컨테이너 필요, 로컬 `pnpm e2e`).
- Produces(README): "## 프론트엔드" 절 — 위치, 스택 한 줄, 실행(`docker compose up -d` → `SPRING_PROFILES_ACTIVE=local,demo KAIROS_DEMO_PASSWORD=… ./gradlew bootRun` → `cd frontend && cp .env.example .env.local && pnpm install && pnpm dev` → `http://localhost:3000`), 명령 목록, E2E 전제(`local,demo` 백엔드 + `KAIROS_DEMO_PASSWORD`), 경고 "`NEXT_PUBLIC_DEMO_*`는 운영 빌드에 두지 않는다". 진행 단계에 `- [x] F1. 프론트엔드 기반과 C1 화면` 줄(C4 아래), 문서 목록에 F1 스펙·이 계획.

- [ ] **Step 1: 설치·설정** — `pnpm add -D @playwright/test && pnpm exec playwright install chromium`, config·fixtures 작성.
- [ ] **Step 2: spec 작성** — 위 세 흐름. 선택자는 역할·라벨 기반(`getByRole`, `getByLabel`), `data-testid`는 쓰지 않는다(접근성 마크업이 E2E 선택자여야 §13이 지켜진다).
- [ ] **Step 3: 실행** — 백엔드 `local,demo` 기동 후 `KAIROS_DEMO_PASSWORD=… pnpm e2e` 3개 PASS. 실패하면 해당 화면 Task 코드를 고치고 `fix(frontend): …`로 커밋.
- [ ] **Step 4: CI·README** — 워크플로 작성 후 `act`가 없으면 로컬에서 같은 명령을 순서대로 실행해 PASS 확인(`pnpm install --frozen-lockfile && pnpm check:api && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle`).
- [ ] **Step 5: 커밋** `test(frontend): C1 핵심 흐름 E2E 3개, 프론트엔드 CI 워크플로, README 프론트 실행 방법`

### Task 16: CLAUDE.md 프론트엔드 절(마지막 작업)

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 문서** — 상단 목록에 `- F1 스펙: docs/superpowers/specs/2026-11-11-frontend-f1-foundation-design.md`, `현재 구현 계획`을 `docs/superpowers/plans/2026-11-11-frontend-f1-foundation.md (이전: …c4…, …c3…, …c2…, …c1…)`, "현재 단계"를 `**F1 프론트엔드 기반·C1 화면**(… C1~C4는 완료)`. 명령어 절에 `- 데모 데이터: SPRING_PROFILES_ACTIVE=local,demo + KAIROS_DEMO_PASSWORD (demo 프로필에서만 DemoDataInitializer·POST /api/v1/demo/reseed 가 있다. prod 와 demo 동시 활성은 기동 실패)`. API 규칙 절에 `- 인증: 액세스 토큰 Bearer(30분), 리프레시 토큰은 본문과 httpOnly 쿠키 kairos_refresh(Path=/api/v1/auth, SameSite=Strict) 둘 다. 쿠키 기반 reissue·logout 은 X-Requested-With: kairos-web 헤더를 요구한다. GET /auth/me 가 역할·이름을 준다(프론트는 JWT 를 해석하지 않는다)`. 아키텍처 절 모듈 목록 뒤에 `demo 패키지는 바운디드 컨텍스트가 아니라 demo 프로필 전용 시더·엔드포인트이며 다른 모듈의 application 만 호출한다(ArchitectureTest demoUsesOnlyApplication)`. 새 절 `## 프론트엔드`(스펙 §18 초안 그대로): 위치 `frontend/`, 스택 한 줄, 명령(`pnpm dev/build/lint/typecheck/test/e2e/gen:api/check:api/check:bundle`), 로컬 실행(백엔드 + `pnpm dev`, `BACKEND_URL` rewrite), 규칙 — OpenAPI가 바뀌면 `pnpm gen:api`로 `src/api/schema.d.ts`를 재생성해 **같은 커밋**에(`check:api`가 빌드를 막는다); 금액은 `src/lib/money.ts`로만 계산·표시(`/`·`%`는 lint가 막는다); 모든 POST는 `useIdempotentSubmit`; 에러 메시지는 `src/api/errors.ts` 표로만; 데모 기능은 `features/demo`에만 두고 `NEXT_PUBLIC_DEMO_MODE` 리터럴 비교로 감싼다(`check:bundle`); `app/`은 조립만, 기능은 `features/`; 데스크톱 전용(1280px); 가드는 클라이언트 레이아웃(`middleware` 없음); 시안 `design/`은 기록이며 고치지 않고 새 화면은 시안 → 사용자 확인 → 구현; 테스트 이름 한국어, 단위는 pure util·훅, 화면 흐름은 E2E(로컬 `pnpm e2e`, CI 제외).
- [ ] **Step 2: 최종 확인** — `./gradlew test` PASS, `cd frontend && pnpm check:api && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle` PASS, `pnpm e2e` 3개 PASS(Task 15 결과 재확인). 스펙 §19 완료 기준을 항목별로 체크해 보고에 적는다.
- [ ] **Step 3: 커밋** `docs: CLAUDE.md 프론트엔드 절과 F1 단계 갱신`

---

## 스펙과의 차이 (실행 전 확인)

- **§7-3 (3) logout**: 스펙은 "액세스 토큰이 만료돼 401이어도 프론트는 메모리를 비우고 `/login`으로"라고만 했지만, 그러면 Redis 토큰과 쿠키가 살아남아 다음 부트스트랩 reissue가 성공해 로그아웃이 되돌아간다(Review Focus 1). **logout을 `permitAll`로 바꾸고 쿠키(+헤더)로도 회원을 찾아 토큰을 지운다.** 프론트의 `/auth/*` 무재시도 규칙은 그대로다.
- **§7-3 (8) 심사 큐 정렬**: "변경 요청 큐가 이미 가진 것과 같은 형태"라고 했으나 변경 요청 큐에도 `sort`가 없다. 네 큐 모두에 `QueueSort`(`LATEST|OLDEST`, 기본 `LATEST`)를 붙인다. 검수 화면은 `OLDEST`를 기본으로 보낸다(§9-3).
- **§7-3 (7) `createdAt`**: 도메인 `Product`에 `createdAt: LocalDateTime?`을 추가해 엔티티의 `BaseEntity.createdAt`을 읽기 전용으로 싣는다(도메인은 순수 Kotlin이라 `LocalDateTime`은 허용). `stockTotal`은 이미 로드된 SKU에서 합산하고, `hasPendingChangeRequest`는 상품 ID 묶음 한 쿼리로 채운다.
- **§7-3 (7) 관리자 큐 행 필드(컨트롤러 지시)**: 관리자 대시보드 시안의 "오래된 대기 건" 표(종류·이름·판매자·요청일)를 채우기 위해 `SellerDetail`·`BrandDetail`·`AdminProductSummary`·`ChangeRequestSummary`에 요청 일시(`createdAt`/`requestedAt`)를, 상품·변경 요청 행에는 `sellerName`을 Task 3에서 추가한다. 스펙은 `SellerProductSummary` 확장만 적었다.
- **§14 데모 관리자 계정**: `SignUpService`는 `USER`만 만들고 demo는 `MemberRepository`를 못 쓰므로 `SignUpService.signUpAdmin`(컨트롤러 미노출)을 추가한다. `DemoProperties`는 `@ConfigurationPropertiesScan`이 패키지 기준으로 등록하므로 `@Profile("demo")`를 붙여 demo 밖 기동이 깨지지 않게 한다.
- **§14 demo 패키지와 ArchUnit**: `demo`는 `ArchitectureRules.modules` 밖(`common`처럼)에 두고, 다른 모듈의 `application`만 부를 수 있다는 전용 규칙을 추가한다. 모듈 간 포트 규칙을 시더에 적용하지 않는 것은 시더가 도메인 규칙을 "그대로 지키려고" application 서비스를 쓰는 소비자일 뿐이기 때문이다.
- **§14·§19 `OpenApiContractTest`**: demo 경로는 `test` 프로필 컨텍스트에 없으므로 스펙 쪽에서 `Demo` 태그 operation을 제외한다(활성 프로필에 `demo`가 있으면 포함). `DemoApiTest`가 `test,demo` 프로필로 reseed 계약을 따로 검증한다.
- **§14 데모 계정 추가**: 대기 중 판매자 신청 1건을 만들기 위해 네 번째 데모 계정 `seller2@kairos.demo`(같은 비밀번호)를 둔다. 프론트 전환 바에는 노출하지 않는다.
- **§5 `tailwind.config.ts`**: `create-next-app`이 Tailwind v4를 생성하면 **3.4로 내려** 스펙의 파일 구조(`tailwind.config.ts`·`postcss.config.js`)를 유지한다. v4로 가려면 `globals.css` `@theme` 방식으로 바뀌므로 별도 결정.
- **§9-2 상태 요약 5칸**: 스펙대로 현재 필터의 `totalElements`만 숫자로 보이고 나머지 칸은 라벨 버튼이다. 반면 **대시보드**(§4-1 시안 대상)는 상태별 4칸에 각각 숫자를 보이며, 이는 `size=1` 조회 4번(관리자 대시보드와 같은 방식)으로 채운다. 집계 API는 만들지 않는다.
- **§9-1 좋아요 아이콘**: 시안 헤더의 "좋아요" 유틸 항목은 F3(좋아요 목록)에서 활성화하므로 장바구니·마이와 함께 F1에서는 숨긴다(§8-1 "비활성 아이콘을 두지 않는다"와 같은 이유).
- **§13 이미지**: `next/image`를 쓰지 않고 `<img>`로 백엔드 절대 URL을 그대로 쓴다(최적화·SEO 비목표, 도메인 허용 목록 관리 불필요).
- **§16 E2E 관리자 승인**: 스펙은 "관리자 승인(수수료 입력)"만 적었는데, 판매자 승인과 상품 승인은 **화면**으로(키보드 전용 단계 포함), 브랜드 승인은 API 픽스처로 한다(흐름 길이를 줄이되 §19 "키보드만으로 검수 승인"을 증명).
- **§19 번들 검사**: "운영 번들에 데모 코드·계정 문자열 없음"에 더해 **데모 빌드에는 있음**도 같은 스크립트가 확인한다(검사 자체가 헛돌지 않게).
- **§15 README 경고와 §16 CI**: 데모 변수가 설정된 빌드를 `prod`에 배포하지 못하게 막는 규칙은 배포 파이프라인이 없어 CI에서는 `NEXT_PUBLIC_DEMO_MODE` 미설정 빌드 + `check:bundle`로만 둔다(배포 단계 후속, §17).
- **C4 계획과 달리 ADR 없음**: F1은 아키텍처 예외를 만들지 않는다(프록시 rewrite·쿠키 경로 결정은 스펙 §7-2에 근거가 있다). 필요해지면 배포 결정 때 ADR로 남긴다.
