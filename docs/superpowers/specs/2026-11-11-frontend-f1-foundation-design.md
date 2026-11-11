# 프론트엔드 F1 기반·C1 화면 설계

> 작성일: 2026-11-11 · 상위: `2026-11-09-commerce-roadmap-design.md` 4장(화면), `2026-11-09-c1-seller-catalog-design.md` 9장 · 시안: `design/` (PR #5)

## 1. 의도

Kairos 백엔드는 C1~C4까지 API가 끝났고, 화면은 C1 시안(`design/*.html`)까지 나왔다. 이제 **실제 서비스 품질의 커머스 프론트엔드**를 만든다. 데모 경로만 통하는 껍데기가 아니라, 구매자·판매자·관리자가 모든 C1~C4 기능을 화면에서 끝까지 쓸 수 있어야 한다(사용자 결정). 면접 데모에서도 같은 화면을 쓴다. 데모용 편의 기능(역할 빠른 전환, 데모 데이터 생성)은 부가 기능이고, 로컬·데모 환경 밖에서는 반드시 꺼진다.

프론트엔드는 하위 프로젝트 단위로 붙인다(로드맵 4장). **F1**은 프론트엔드 기반 전체(스택, 인증, API 계약 연결, 디렉터리, 테스트, 실행 방법)와 C1 화면을 다루고, F2~F4는 각각 C2~C4 화면을 다룬다. 이 문서는 F1을 자세히 적고, 전체 아키텍처는 F2~F4에서도 그대로 쓴다.

표기: **사용자 결정**(확정, 바꾸려면 사용자 승인) / **Claude 결정**(작성자 판단, 빌더·리뷰에서 반박 가능. "틀렸을 때 비용"을 적는다).

## 2. 목표와 비목표

- 목표(F1): 프론트엔드 기반 수립, 인증(로그인·가입·토큰 갱신·로그아웃), C1 화면 전부(구매자 상품 목록·검색·상세·브랜드, 판매자 입점·브랜드·상품 등록·수정·SKU·판매 상태, 관리자 심사 4종·판매자 정지·카테고리 관리), 핵심 흐름 E2E, CLAUDE.md 프론트엔드 절 추가.
- 비목표(F1): 모바일 대응(최소 너비 1280px 데스크톱 전용, 사용자 결정), C2~C4 화면(F2~F4), SEO·SSR 최적화, i18n, 다크 모드, 디자인 시스템 문서 사이트, 핫딜 화면(C6; 시안의 핫딜 배너·내비 항목은 자리만 둔다), 저장소 이름 변경(`kairos`로 바꾸는 것은 나중 선택지, 사용자 결정).
- 안티 목표(실패로 보는 것): 운영 빌드에 데모 전환 UI나 데모 계정 정보가 들어감, 리프레시 토큰이 JS에서 읽힘, 금액 계산을 부동소수점으로 함, OpenAPI가 바뀌었는데 프론트 빌드가 통과함, 같은 등록 버튼 두 번 눌러 상품이 두 개 생김.

## 3. 저장소와 스택 (사용자 결정)

- **모노레포**: 프론트엔드는 이 저장소의 `frontend/`에 둔다. 백엔드 Gradle 빌드와는 독립이며, 공유하는 것은 `docs/api/openapi.yaml`뿐이다.
- Next.js(App Router) + TypeScript, Tailwind CSS + shadcn/ui, TanStack Query, `openapi-typescript`로 `docs/api/openapi.yaml`에서 타입 생성, React Hook Form + Zod, pnpm, Playwright(E2E, 핵심 흐름만).
- 인증: 액세스 토큰은 메모리, 리프레시 토큰은 httpOnly 쿠키.
- 데스크톱 전용(최소 1280px). 모바일은 나중.

### 3-1. 스택 세부 (Claude 결정)

- HTTP 클라이언트는 **`openapi-fetch`**(openapi-typescript 제작진의 타입 안전 fetch 래퍼)를 쓴다. 경로·쿼리·본문·응답이 생성 타입으로 묶이고, 미들웨어로 Authorization 헤더·401 재시도를 한 곳에서 처리한다. 틀렸을 때 비용: 다른 클라이언트로 바꾸면 `src/api/client.ts` 한 파일과 훅의 호출 형태를 고친다.
- 데이터 조회는 **전부 클라이언트 컴포넌트 + TanStack Query**로 한다. 서버 컴포넌트는 레이아웃·정적 껍데기에만 쓴다. 액세스 토큰이 브라우저 메모리에만 있어 Next 서버에서 인증 요청을 대신 보낼 수 없고, SEO가 목표가 아니기 때문이다. 틀렸을 때 비용: 공개 페이지(상품 목록·상세) SSR이 필요해지면 그 두 페이지만 서버 fetch로 옮긴다(공개 API라 토큰이 필요 없다).
- 노드 버전은 `frontend/.nvmrc`로 고정한다(LTS). 패키지 매니저 버전은 `package.json`의 `packageManager`로 고정한다.
- 상태 관리 라이브러리는 추가하지 않는다. 서버 상태는 TanStack Query, 폼 상태는 React Hook Form, 전역은 인증 컨텍스트 하나뿐이다.

## 4. 시각 기준 (사용자 확정 시안)

`design/assets/kairos.css`와 `design/*.html`(PR #5)을 **시각 기준**으로 삼는다. 구현 시 다음을 지킨다.

- 색 토큰을 그대로 Tailwind 테마로 옮긴다: `bg #FFFFFF`, `surface #F3F5F8`, `surface-2 #EEF0F4`, `border #E3E6EC`, `navy #1B2A4A`(텍스트·기본 UI), `text-2 #5B6577`, `text-3 #8A93A3`, `discount #D2401F`, `success #1F7A4D`, `warning #9A6700`, `danger #B42318`, `hot #E04A22`(**핫딜 전용**, F1에서 핫딜 자리 외에 쓰지 않는다). radius 4px/8px, 글꼴 Noto Sans KR, 본문 14px, 컨테이너 1280px.
- shadcn/ui 컴포넌트(Button, Input, Select, Dialog, Tabs, Table, Badge, Toast 등)를 쓰되 CSS 변수를 위 토큰으로 바꿔 시안과 같은 모양으로 맞춘다. 시안의 `.chip-*`(상태 칩), `.console`(판매자·관리자 콘솔 레이아웃: 232px 사이드바 + 본문), `.site-header`/`.cat-nav`(구매자 헤더·카테고리 내비)는 공용 컴포넌트로 만든다.
- 최소 터치 영역 44px, `:focus-visible` 아웃라인 2px navy, `aria-current`·`aria-pressed`·`aria-selected`·`role="switch"` 사용은 시안대로 유지한다(§13).
- 시안의 구조를 바꿔야 하면(예: API 응답에 없는 필드) 이 문서 §9의 화면 설명에 적고, 시안 파일은 고치지 않는다. 시안은 기록이다.

## 5. 디렉터리 구조 (Claude 결정)

```
frontend/
  package.json  pnpm-lock.yaml  .nvmrc  next.config.ts  tsconfig.json
  tailwind.config.ts  postcss.config.js  components.json(shadcn)
  .env.example  .env.local(미커밋)
  playwright.config.ts  vitest.config.ts
  scripts/check-api-types.sh          # 생성 타입과 openapi.yaml 불일치 검사
  src/
    app/                              # 라우팅만. 페이지 파일은 얇게, 기능은 features/로
      layout.tsx  providers.tsx       # QueryClient, AuthProvider, Toaster
      (store)/                        # 구매자 영역: 헤더+카테고리 내비 레이아웃
        page.tsx                      # / → 상품 목록
        products/page.tsx  products/[productId]/page.tsx  brands/[brandId]/page.tsx
        login/page.tsx  signup/page.tsx
      seller/                         # 판매자 콘솔 레이아웃 (사이드바)
        layout.tsx  page.tsx(대시보드)  apply/page.tsx  brands/page.tsx
        products/page.tsx  products/new/page.tsx  products/[productId]/page.tsx
        products/[productId]/edit/page.tsx
      admin/                          # 관리자 콘솔 레이아웃
        layout.tsx  page.tsx  review/page.tsx  categories/page.tsx
      forbidden/page.tsx  not-found.tsx  error.tsx
    api/
      schema.d.ts                     # openapi-typescript 생성물 (커밋한다)
      client.ts                       # openapi-fetch 인스턴스 + 인증·에러 미들웨어
      errors.ts                       # ErrorResponse.code → UI 메시지 (§10)
      idempotency.ts                  # Idempotency-Key 생성 (§11)
    auth/                             # AuthProvider, useAuth, 토큰 메모리 저장, 부트스트랩
    features/                         # 기능 단위. 각 폴더: api.ts(쿼리·뮤테이션 훅), schemas.ts(Zod), components/
      catalog/   (상품 목록·상세·카테고리·브랜드 조회)
      seller/    (입점 신청, 브랜드, 상품 등록·수정, SKU, 판매 상태, 변경 요청)
      admin/     (심사 큐, 승인·반려, 판매자 정지, 카테고리 관리)
      demo/      (역할 전환 바, 재생성 버튼 — NEXT_PUBLIC_DEMO_MODE 일 때만 번들에 포함)
    components/
      ui/        (shadcn 생성물)
      layout/    (StoreHeader, CategoryNav, ConsoleShell, SideNav, Footer)
      common/    (StatusChip, Money, EmptyState, ErrorState, PageSkeleton, Pagination, ImageUploader, ConfirmDialog)
    lib/
      money.ts   (원화 표시, 정수 계산, §12)  datetime.ts(KST LocalDateTime 표시)  query-keys.ts
    middleware.ts                     # 리프레시 쿠키 없으면 /seller, /admin → /login (§8-3)
  e2e/                                # Playwright
    fixtures/ (계정 생성·로그인 헬퍼)  c1-seller-onboarding.spec.ts  c1-buyer-browse.spec.ts  auth.spec.ts
```

- `features/*`는 `app/`을 import하지 않는다. `app/`은 `features/`·`components/`만 조립한다. 틀렸을 때 비용: 라우트가 늘면 기능 폴더가 커지는데, 그때 `features/seller/products`처럼 한 단계 더 나눈다.
- 백엔드의 모듈 경계(`catalog`, `seller`, `file`…)와 프론트 `features/`가 1:1일 필요는 없다. 프론트는 **역할(구매자·판매자·관리자)** 기준으로 나눈다. 화면은 역할로 쓰이기 때문이다.

## 6. API 계약 연결 (사용자 결정 + Claude 세부)

- `pnpm gen:api`: `openapi-typescript ../docs/api/openapi.yaml -o src/api/schema.d.ts`. 생성물은 **커밋**한다(IDE·CI에서 생성 없이 타입 검사가 되게).
- `pnpm check:api`: 임시 경로로 다시 생성해 커밋본과 `diff`한다. 다르면 실패. `pnpm build`·CI 모두 이 검사를 먼저 돌린다. 그래서 OpenAPI가 바뀌면 타입을 재생성하지 않는 한 **빌드가 깨진다**(사용자 결정: 계약 드리프트는 빌드 실패).
- 응답 언래핑: 모든 성공 응답은 `ApiResponse{message,data,timestamp}`라서 훅은 `data`만 돌려준다. 목록은 `PageResponse{content,page,size,totalElements,totalPages}` 그대로 쓴다.
- 일시는 오프셋 없는 KST LocalDateTime 문자열이다. `lib/datetime.ts`가 **문자열을 Date로 바꾸지 않고** 표시만 포맷한다(브라우저 시간대에 흔들리지 않게). 날짜 입력도 `YYYY-MM-DDTHH:mm:ss` 문자열로 만든다.
- 응답 헤더 `X-Trace-Id`는 에러 토스트·에러 페이지에 작게 표시한다("문의 시 전달").

## 7. 인증 (사용자 결정: 액세스 토큰 메모리, 리프레시 토큰 httpOnly 쿠키)

### 7-1. 현재 백엔드

`POST /auth/login`·`/auth/reissue`는 본문으로 `{accessToken, refreshToken}`을 돌려주고, `reissue`는 본문의 `refreshToken`을 받는다. 리프레시 토큰은 Redis에 회원당 하나(회전). `logout`은 Redis 토큰 삭제. 액세스 TTL 30분, 리프레시 14일. CORS 설정은 없고 CSRF는 꺼져 있다(STATELESS). 공개 경로: 가입·로그인·재발급, `GET /products/**`·`/categories/**`·`/brands/**`·`/files/**`, 딜 조회.

### 7-2. 브라우저 ↔ 백엔드 경로: Next.js rewrite 프록시 (Claude 결정)

브라우저가 백엔드를 **직접** 부르지 않고, Next.js `rewrites`로 `/api/v1/*`와 `/files/*`를 백엔드(`BACKEND_URL`, 서버 전용 env)로 넘긴다. 브라우저 입장에서 API는 같은 출처(`localhost:3000`)다.

- 이유: 쿠키가 프론트 출처에 묶여 **CORS·`credentials: include`·`SameSite=None`이 필요 없다**. 리프레시 쿠키는 `SameSite=Strict`로 둘 수 있다. 운영에서 프론트·API 도메인이 달라도 같은 규칙이 유지된다. 설정만으로 끝나고 라우트 핸들러 코드가 없다.
- 라우트 핸들러 BFF(핸들러가 토큰을 들고 백엔드를 호출)는 **쓰지 않는다**. 엔드포인트마다 핸들러를 쓰거나 범용 프록시 핸들러를 직접 짜야 하고, 백엔드가 이미 쿠키를 다룰 수 있으므로 얻는 것이 없다.
- 트레이드오프: 모든 API 호출이 Next 서버를 한 번 거친다(지연 수 ms, 5MB 이미지 업로드도 통과). 프론트를 정적 export로 배포할 수는 없다(Node 서버 필요). 부하 측정(k6)은 지금처럼 백엔드를 직접 친다.
- 틀렸을 때 비용: 정적 배포나 CDN 분리가 필요해지면 대안 A(백엔드 CORS + `credentials: include`, 운영에서 같은 사이트(eTLD+1)면 `SameSite=Strict` 유지, 아니면 `None; Secure`)로 바꾼다. 백엔드에 CORS 설정 한 개와 프론트 `client.ts`의 baseUrl 변경이면 된다.

### 7-3. 백엔드 변경 (최소, OpenAPI 먼저 — CLAUDE.md SDD)

F1 백엔드 작업은 **이것뿐**이다. `docs/api/openapi.yaml`을 먼저 고치고 `OpenApiContractTest`로 맞춘다.

1. **`POST /auth/login`, `POST /auth/reissue`가 리프레시 토큰을 쿠키로도 내려준다.** `Set-Cookie: kairos_refresh=<token>; HttpOnly; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600`(14일, `kairos.jwt.refresh-ttl`과 같은 값). `Secure`는 `prod` 프로필에서만(설정 `kairos.auth.cookie-secure`, 기본 false). 본문의 `refreshToken` 필드는 **그대로 유지**한다(perf k6 스크립트와 Swagger가 쓴다. 틀렸을 때 비용: 없앴다면 `perf/` 스크립트를 고쳐야 했다).
2. **`reissue`의 본문을 선택으로 바꾼다.** 본문 `refreshToken`이 없으면 쿠키 `kairos_refresh`를 쓴다. 둘 다 없으면 400 `INVALID_REQUEST`. 회전 규칙은 그대로다(새 토큰을 쿠키와 본문에 다시 내려줌).
3. **`logout`이 쿠키를 지운다.** `Max-Age=0`으로 같은 이름·경로의 쿠키를 내린다. 액세스 토큰이 만료돼 401이어도 프론트는 메모리를 비우고 `/login`으로 보낸다(쿠키는 다음 reissue에서 Redis 불일치로 어차피 거부된다).
4. **CSRF**: 쿠키로 인증되는 요청은 `reissue` 하나뿐이고 `SameSite=Strict`가 다른 사이트의 요청을 막는다. 추가로 쿠키 기반 reissue는 **`X-Requested-With: kairos-web` 헤더를 요구**한다(없으면 쿠키를 무시하고 본문만 본다). 커스텀 헤더는 단순 요청이 될 수 없어 CORS 없이 다른 출처에서 보낼 수 없다. 틀렸을 때 비용: 오래된 브라우저의 SameSite 미지원 — 헤더 검사가 두 번째 방어선이라 괜찮다. 다른 API는 Bearer 헤더로만 인증되므로 CSRF 대상이 아니다.
5. **`GET /auth/me`(인증 필요) 추가**: `{memberId, email, name, role}`. 프론트가 액세스 토큰 페이로드를 직접 해석하지 않게 한다(토큰 구조는 계약이 아니다). 틀렸을 때 비용: 엔드포인트 하나. 이것을 빼면 프론트가 JWT를 디코딩해 `role`을 읽어야 하고, 이름은 가입 응답에 없어 헤더에 표시할 수 없다.
6. **CORS 설정은 추가하지 않는다**(§7-2). 다른 출처에서 직접 호출할 일이 생기면 그때 `kairos.cors.allowed-origins`로 넣는다.

`SELLER`는 JWT 역할이 아니다(C1 스펙 2장). 프론트도 "판매자인지"를 역할이 아니라 `GET /seller/me`(404면 미신청)와 그 `status`로 판단한다.

### 7-4. 프론트 흐름

- **부트스트랩**: 앱 로드 시 `AuthProvider`가 `POST /auth/reissue`(본문 없음, `X-Requested-With`)를 한 번 호출한다. 200이면 액세스 토큰을 메모리에 두고 `GET /auth/me`로 사용자 정보를 받는다. 401이면 비로그인. 이 동안은 헤더에 스켈레톤을 보이고 보호 페이지는 렌더하지 않는다.
- **요청**: `client.ts` 미들웨어가 메모리 토큰을 `Authorization: Bearer`로 붙인다. 401(`UNAUTHORIZED`)이면 reissue를 **한 번만** 시도(동시 401은 한 Promise를 공유)하고 원 요청을 재시도한다. reissue도 실패하면 메모리를 비우고 로그인 페이지로 보낸다(`?next=` 유지).
- **로그인**: 이메일·비밀번호 → 토큰 메모리 저장 → `/auth/me` → `next` 또는 역할 기본 페이지(ADMIN `/admin`, 그 외 `/`).
- **로그아웃**: `POST /auth/logout` → 메모리 비움 → QueryClient 초기화 → `/`.
- 새 탭·새로고침마다 부트스트랩이 돈다(토큰은 메모리에만). 탭 간 공유는 하지 않는다.

## 8. 역할별 라우팅과 가드

### 8-1. 영역

| 영역 | 경로 | 레이아웃 | 접근 |
|---|---|---|---|
| 구매자 | `/`, `/products`, `/products/[id]`, `/brands/[id]`, `/login`, `/signup` | 헤더 + 카테고리 내비 + 푸터 | 공개. 좋아요·장바구니(F2)·마이 페이지(F2)는 로그인 유도 |
| 판매자 | `/seller/**` | 콘솔(사이드바: 대시보드, 상품 관리, 브랜드, 정산(준비 중)) | 로그인 회원. 판매자 상태로 메뉴·화면이 바뀐다(§8-2) |
| 관리자 | `/admin/**` | 콘솔(사이드바: 대시보드, 검수(대기 수 배지), 카테고리, 판매자, 핫딜(준비 중)) | `role === ADMIN`. 아니면 `/forbidden` |

구매자 헤더의 유틸 내비: 로그인 전 "로그인 / 가입", 로그인 후 "판매자 센터"(판매자면 콘솔, 아니면 입점 안내) / "관리자"(ADMIN만) / "로그아웃". 장바구니·마이 아이콘은 F2에서 활성화하며 F1에서는 숨긴다(비활성 아이콘을 두지 않는다).

### 8-2. 판매자 상태별 진입 (C1 스펙 12장을 화면으로)

`/seller/layout.tsx`가 `GET /seller/me`를 읽고 분기한다.

| `seller/me` | 보이는 것 |
|---|---|
| 404 (미신청) | 입점 안내 + "입점 신청" → `/seller/apply`. 사이드 메뉴는 숨김 |
| `PENDING` | 신청 내용 읽기 전용 + "심사 중" 안내. 수정 가능(PUT, 다시 PENDING) |
| `REJECTED` | 반려 사유(`rejectReason`) + 수정 폼(같은 행 재신청) |
| `APPROVED` | 전체 메뉴 |
| `SUSPENDED` | 정지 사유 배너. 상품 목록은 조회·일시 중지·종료만, 등록·수정·브랜드 신청 버튼 비활성(툴팁 "정지 중") |

서버가 409 `SELLER_NOT_ACTIVE`를 돌려주면 같은 배너를 띄운다(화면 분기가 놓친 경우의 뒷받침).

### 8-3. 가드 구현 (Claude 결정)

- `middleware.ts`는 `/seller/**`·`/admin/**`에 **리프레시 쿠키가 없으면** `/login?next=`로 보낸다. 쿠키가 프록시 덕분에 프론트 출처에 있어 가능하다. 역할은 쿠키로 알 수 없으므로 검사하지 않는다.
- 역할·판매자 상태 검사는 **클라이언트 레이아웃**(`RequireRole`, `SellerGate`)이 `/auth/me`·`/seller/me` 결과로 한다. 최종 방어는 서버의 401/403/409다. 틀렸을 때 비용: 비관리자가 `/admin` 껍데기를 한순간 볼 수 있다 — 레이아웃이 사용자 정보가 올 때까지 본문을 그리지 않으므로 데이터는 새지 않는다.

## 9. F1 화면 (C1)

각 화면은 시안 파일을 기준으로 하고, 쓰는 API와 상태를 적는다. 모든 목록은 `?page&size`(기본 20) + 페이지네이션(시안 `.pagination`).

### 9-1. 구매자 (`design/user-product-list.html`, `user-product-detail.html`)

- **상품 목록 `/products`**: `GET /categories`(좌측 트리, 대분류 접기/펼치기), `GET /products?categoryId&brandId&minPrice&maxPrice&sort&keyword&page&size`. 필터·정렬·검색어·페이지는 **URL 쿼리가 단일 진실**이다(뒤로 가기·공유 가능). 정렬 `LATEST`/`PRICE_ASC`/`PRICE_DESC`/`POPULAR`/`LIKES`. 카드: 대표 이미지, 브랜드명, 상품명, 할인율(서버 `discountRate`), 판매가, 정가 취소선, 품절 배지(`soldOut`), 좋아요 수·별점(C3 필드, 표시만). 좋아요 버튼은 F1에서는 **비로그인이면 로그인 유도, 로그인이면 `POST/DELETE /products/{id}/like`** 호출(API가 이미 있고 시안에 있어 넣는다). 핫딜 배너·내비 항목은 "준비 중" 자리만.
- **상품 상세 `/products/[id]`**: `GET /products/{id}`. 갤러리(`imageUrls`, 대표 + 썸네일), 브랜드 링크, 정가·할인율·판매가, 옵션 축 버튼(`options`), SKU 매칭(`skus`: 선택 조합으로 `skuId`·`extraPrice`·`soldOut` 찾기, 품절 조합은 비활성 + "품절"), 수량, 합계(= `(salePrice + extraPrice) × 수량`, §12). "장바구니"·"바로 구매"는 F2까지 **비활성 + "구매는 곧 열려요" 툴팁**이 아니라 **버튼 자체를 숨기고** 안내 문구를 둔다(비활성 버튼이 데모에서 오해를 부른다). 404 → 상품 없음 페이지.
- **브랜드 `/brands/[id]`**: `GET /brands/{id}` + `GET /products?brandId=`. 목록 화면 재사용.
- **가입 `/signup`, 로그인 `/login`**: Zod(이메일 형식, 비밀번호 8자 이상 — OpenAPI 제약과 같게), 409 `MEMBER_EMAIL_DUPLICATED` → 이메일 필드 에러, 401 `AUTH_INVALID_CREDENTIALS` → 폼 상단 메시지(어느 쪽이 틀렸는지 구분하지 않는다, 서버 정책).

### 9-2. 판매자 (`design/seller-product-form.html`, `seller-product-list.html`)

- **입점 신청 `/seller/apply`**: `POST/PUT /seller/application`. 필드 회사명·사업자등록번호(10자리 숫자, 하이픈 자동 제거)·대표자명·연락처·은행·계좌번호·예금주. 409 `SELLER_BUSINESS_NUMBER_DUPLICATED` → 필드 에러, `SELLER_ALREADY_EXISTS` → `/seller`로. 상태별 화면은 §8-2.
- **브랜드 `/seller/brands`**: `GET /seller/brands` 카드 목록(상태 칩 PENDING/APPROVED/REJECTED, 반려 사유), "브랜드 신청" 다이얼로그(`POST /seller/brands`: 이름, 영문명, 로고 `logoFileId`(ImageUploader), 소개), 반려 건 수정(`PUT /seller/brands/{id}`). 409 `BRAND_NAME_DUPLICATED` → 필드 에러.
- **상품 관리 `/seller/products`**: 상단 상태 요약 5칸(전체/검수 대기/판매 중/일시 중지/반려·종료 — 서버에 집계 API가 없으므로 **현재 필터의 `totalElements`**만 숫자로 보이고, 나머지 칸은 상태 필터 버튼으로만 동작한다. 시안과 다른 점이며, 집계 API는 넣지 않는다. 틀렸을 때 비용: 데모에서 숫자가 덜 보인다 — 필요하면 F2에서 `GET /seller/products/summary`를 OpenAPI에 먼저 추가), `GET /seller/products?status&page&size` 표(이미지, 상품명, 브랜드, 판매가, 상태 칩 + "변경 요청 검수 중" 배지, 재고 합계, 등록일), 행 작업: 일시 중지/재개/종료(`POST …/pause|resume|end`, 종료는 ConfirmDialog "되돌릴 수 없음"), 판매가 수정(인라인 다이얼로그 `PUT …/price`, 정가 이하 검증 + 할인율 미리 보기).
- **상품 등록 `/seller/products/new`**: `POST /seller/products`. 브랜드(APPROVED만 선택 가능, 없으면 브랜드 신청 안내), 소분류 선택(대분류 → 소분류 2단 셀렉트, `CATEGORY_NOT_LEAF` 방지), 상품명, 설명, 이미지 1~10장(ImageUploader: 선택 즉시 `POST /files/images` 업로드 → `fileId`·`url`, 첫 장이 대표, 드래그 순서 변경, 5MB·jpg/png/webp 클라이언트 검증 + 서버 `FILE_UNSUPPORTED_FORMAT`), 정가·판매가(할인율 힌트 = `floor((정가 − 판매가) × 100 / 정가)` 정수 계산), 옵션 축 0~2개(축 이름 + 값 태그 입력), SKU 미리 보기 표(축 값의 데카르트 곱을 클라이언트에서 만들어 보여주되 **재고·추가 금액은 등록 후** 상품 상세에서 입력한다 — 등록 API는 재고를 받지 않는다. 시안의 SKU 표 입력란은 등록 화면에서는 읽기 전용 미리 보기로 바뀐다). 제출 후 "검수 대기" 안내와 상세로 이동.
- **상품 상세(판매자) `/seller/products/[id]`**: `GET /seller/products/{id}`. 현재 내용, 상태 칩, 반려 사유, SKU 표 편집(행마다 `PUT …/skus/{skuId}`: 추가 금액·재고·판매 여부 토글, 행 단위 저장), 판매가 수정, 판매 상태 버튼, 대기 중 변경 요청 카드(내용 요약 + "철회" `DELETE …/change-requests/{id}`), "내용 수정(재검수)" → `/edit`.
- **상품 수정(변경 요청) `/seller/products/[id]/edit`**: `POST /seller/products/{id}/change-requests`(상품명·설명·카테고리·이미지·옵션·정가 — `ChangeRequestDetail` 기준). 등록 폼을 재사용하되 초기값을 현재 내용으로 채우고, 옵션 축 변경 시 "사라지는 조합은 단종, 새 조합은 재고 0" 경고를 띄운다. 409 `CHANGE_REQUEST_ALREADY_PENDING` → 상세로 보내 철회를 안내.

### 9-3. 관리자 (`design/admin-review.html`)

- **검수 `/admin/review`**: 탭 4개(판매자·브랜드·상품·변경 요청, 각 탭 대기 수는 `?status=PENDING&size=1`의 `totalElements`로 가져온다). 좌측 큐(`GET /admin/sellers|brands|products|change-requests?status&page&size`, 기본 `PENDING`/`PENDING_REVIEW`, 정렬 오래된 순), 우측 상세 카드(선택 행, URL `?tab=&id=`로 유지). 승인·반려: 판매자 승인은 **수수료율(bp) 입력** 다이얼로그(`ApproveSellerRequest.commissionRateBp`, 기본값 1000 = 10%), 반려는 사유 필수(`reason`). 변경 요청 상세는 `GET /admin/change-requests/{id}`와 `GET /admin/products/{id}`를 나란히 두고 **변경 전·후 diff**(바뀐 필드만 강조, 이미지 신규 표시, 옵션 변경 시 SKU 영향: 유지/신규/단종을 옵션 값 ID로 계산해 표시). 처리 후 큐에서 제거하고 다음 행 선택.
- **판매자 관리 `/admin/sellers`**: 상태 필터 전체, 정지(사유 필수)·해제. F1에 포함한다(검수 탭에서 APPROVED 판매자는 안 보이기 때문).
- **카테고리 `/admin/categories`**: `GET /categories` 트리 편집. 대분류·소분류 추가(`POST /admin/categories`), 이름·노출 순서 수정(`PUT`), 삭제(`DELETE`, 409 `CATEGORY_HAS_PRODUCTS`/`CATEGORY_HAS_CHILDREN` → 토스트 메시지, 삭제 전 ConfirmDialog). 순서 변경은 `displayOrder` 숫자 입력(드래그 정렬은 하지 않는다).
- **대시보드 `/admin`**: 대기 건수 4개 카드 + 바로 가기. 통계 API는 없으므로 그 이상은 두지 않는다.

### 9-4. 공통

- 404(`not-found.tsx`), 권한 없음(`/forbidden`), 예기치 못한 오류(`error.tsx`, trace ID 표시, "다시 시도").
- 상태 칩 매핑(한 곳 `StatusChip`): 판매자 `PENDING 심사 중 / APPROVED 승인 / REJECTED 반려 / SUSPENDED 정지`, 브랜드 `PENDING/APPROVED/REJECTED`, 상품 `PENDING_REVIEW 검수 대기 / ON_SALE 판매 중 / REJECTED 반려 / PAUSED 일시 중지 / ENDED 판매 종료`, 변경 요청 `PENDING 검수 중 / APPROVED / REJECTED / WITHDRAWN 철회`. 색은 시안 `.chip-*`.

## 10. 에러 처리: `ErrorResponse.code` → UI (Claude 결정)

- `src/api/errors.ts`에 `code → { message, kind }` 표를 둔다. `kind`는 `field`(특정 입력 필드에 붙임, 예: `MEMBER_EMAIL_DUPLICATED → email`, `SELLER_BUSINESS_NUMBER_DUPLICATED → businessNumber`, `BRAND_NAME_DUPLICATED → name`, `PRODUCT_INVALID_PRICE → salePrice`), `form`(폼 상단), `toast`(작업 결과), `page`(페이지 전체 교체: `UNAUTHORIZED`, `FORBIDDEN`, `*_NOT_FOUND` 중 페이지 주체), `banner`(`SELLER_NOT_ACTIVE`).
- 표에 없는 코드는 서버 `message`를 그대로 토스트로 보여준다(백엔드 메시지가 이미 한국어 사용자 문장이다). 표는 **메시지를 바꾸고 싶은 코드와 배치 규칙**만 담는다. 틀렸을 때 비용: 코드가 늘어도 프론트가 깨지지 않고 서버 문구가 보인다.
- 타입 검사: 표의 키는 OpenAPI `ErrorResponse.code` enum에서 생성된 타입으로 제한한다. 백엔드가 코드를 지우면 `check:api` 뒤 타입 오류로 드러난다.
- 400 `INVALID_REQUEST`는 필드 정보가 없으므로 폼 상단에 띄우고, 클라이언트 Zod 검증이 먼저 같은 규칙을 잡게 한다(Zod 스키마는 OpenAPI의 제약(길이, 최소값)을 따라 적는다).
- 네트워크 오류·5xx: 토스트 "잠시 후 다시 시도" + trace ID(있으면). TanStack Query 재시도는 **GET만 1회**, 뮤테이션은 재시도하지 않는다(멱등 키가 없는 C1 POST는 자동 재시도가 중복을 만든다).

## 11. 멱등성과 이중 제출 (사용자 결정 + Claude 세부)

- 모든 **POST 뮤테이션**은 제출마다 `Idempotency-Key`(UUID v4)를 만들어 보낸다. 키는 폼의 "제출 시도" 단위다: 같은 시도의 네트워크 재시도(사용자가 "다시 시도"를 누름)는 같은 키, 사용자가 입력을 고치면 새 키. React Hook Form의 `handleSubmit` 래퍼(`useIdempotentSubmit`)가 관리한다.
- 제출 중에는 버튼을 비활성하고 로딩 표시, 폼을 `fieldset disabled`로 잠근다. 응답 뒤에만 다시 활성화한다.
- 백엔드는 현재 `kairos.idempotency.paths`(주문·결제·취소·쿠폰·후기·반품·정산)에서만 키를 검사한다. C1 등록 경로(입점·브랜드·상품·변경 요청)는 검사하지 않으며 **F1에서 추가하지 않는다**. 이 경로들은 UNIQUE(사업자번호·브랜드명·판매자 1인 1행·변경 요청 1건)와 UI 잠금으로 중복이 막히고, 상품 등록 중복은 검수에서 걸러진다. 프론트가 키를 항상 보내므로 나중에 경로만 추가하면 된다. 틀렸을 때 비용: 상품 등록이 네트워크 중단으로 두 번 들어갈 수 있다(검수 대기 상품 2개) — 발생하면 경로를 추가한다.
- 422 `IDEMPOTENCY_KEY_REUSED`·409 `IDEMPOTENCY_IN_PROGRESS`는 F2(주문)부터 의미가 있지만 에러 표에는 F1에서 넣는다.

## 12. 금액 표시와 계산 (사용자 결정: KRW 정수, 부동소수점 금지)

- API 금액은 원 단위 `int64`다. JS `number`로 받으며 `Number.isSafeInteger`가 아니면 개발 모드에서 throw, 운영에서는 표시를 거부한다("금액 오류"). 커머스 금액이 2^53을 넘을 일은 없지만 검사는 한 줄이다.
- `lib/money.ts`: `formatKrw(n)` → `Intl.NumberFormat('ko-KR')` + "원"(`27,300원`), `<Money value />` 컴포넌트(`font-variant-numeric: tabular-nums`). 계산 함수는 **정수 덧셈·곱셈·`Math.floor` 나눗셈만** 허용하고 `%`·`/`를 직접 쓴 코드는 ESLint `no-restricted-syntax` 규칙으로 `money.ts` 밖에서 막는다. 예: `discountRate = Math.floor(((orig - sale) * 100) / orig)`, `lineTotal = (salePrice + extraPrice) * qty`.
- 서버가 주는 값(`discountRate`, F2의 주문 합계 등)은 **다시 계산하지 않고** 그대로 보여준다. 클라이언트 계산은 서버가 아직 모르는 입력(등록 폼의 할인율 힌트, 상세의 선택 합계)에만 쓴다.
- 입력란은 숫자 문자열을 다루고 제출 직전에 `parseInt`로 바꾼다. 소수점 입력은 막는다. 수수료율은 bp 정수(1000 = 10.00%)로 보내고 표시만 `10%`.

## 13. 로딩·빈·에러 상태와 접근성 기본

- 모든 목록·상세는 세 상태를 가진다: 로딩(스켈레톤, 레이아웃 시프트 없음), 비어 있음(`EmptyState`: 아이콘 없이 한 문장 + 1차 행동, 예: "등록한 상품이 없습니다 → 상품 등록"), 오류(`ErrorState`: 메시지 + 다시 시도 + trace ID). 필터 결과 0건과 데이터 자체 0건은 문구를 구분한다.
- 뮤테이션은 낙관적 업데이트를 **하지 않는다**(검수·상태 전이는 서버 결과를 그대로 보여주는 게 맞다). 성공 시 관련 쿼리를 invalidate한다(`query-keys.ts`).
- 접근성: 시맨틱 요소(`nav`, `main`, `table`, `button`), 모든 입력에 `label`, 아이콘 버튼에 `aria-label`, 상태 칩은 색 + 텍스트, 포커스 링 유지, 다이얼로그 포커스 트랩(shadcn Dialog), 탭·정렬 버튼의 `aria-selected`/`aria-pressed`, 토스트는 `aria-live="polite"`, 키보드만으로 등록·심사 흐름 완료 가능(E2E 1건은 키보드로만 수행). 색 대비는 시안 토큰이 AA를 만족한다.
- 1280px 미만에서는 가로 스크롤을 허용하고 레이아웃을 바꾸지 않는다(`min-width: 1280px` on body, 사용자 결정 데스크톱 전용).

## 14. 데모 편의 기능 (사용자 결정: 부가 기능, 운영에서 반드시 꺼짐)

### 14-1. 방식 (Claude 결정)

- **백엔드**: Spring 프로필 `demo`에서만 뜨는 `DemoDataInitializer`(ApplicationRunner, `AdminInitializer`와 같은 자리)가 시작 시 **고정 계정**(구매자 `buyer@kairos.demo`, 판매자 `seller@kairos.demo`, 관리자는 `.env`의 관리자)과 기본 데이터(카테고리 트리, 승인된 판매자·브랜드, 판매 중 상품 20개, 검수 대기 건 몇 개)를 **없을 때만** 만든다. 재생성은 `POST /api/v1/demo/reseed`(ADMIN, `@Profile("demo")` 컨트롤러)로 데모 데이터를 지우고 다시 만든다. 비밀번호는 `KAIROS_DEMO_PASSWORD` env로 주입한다(커밋하지 않음).
- `demo` 프로필이 아니면 컨트롤러 빈 자체가 없어 `/api/v1/demo/**`는 404다. `application-prod.yaml`은 `demo`를 활성화하지 않고, 기동 시 `prod`와 `demo`가 동시에 활성이면 **기동을 실패**시킨다(한 줄 검사). OpenAPI에는 `Demo` 태그로 문서화하고 "demo 프로필에서만 존재"를 적는다(SDD).
- **프론트**: `NEXT_PUBLIC_DEMO_MODE=true`일 때만 `features/demo`가 번들에 포함된다(`process.env` 상수 조건으로 트리 셰이킹). 화면 하단에 얇은 바: "구매자 / 판매자 / 관리자로 전환"(해당 데모 계정으로 로그인 호출 — 비밀번호는 `NEXT_PUBLIC_DEMO_PASSWORD`로 **데모 배포에서만** 주입), "데모 데이터 재생성"(관리자로 전환 뒤 reseed). 운영 빌드는 두 변수를 두지 않는다. 틀렸을 때 비용: 데모 비밀번호가 데모 서버의 클라이언트 번들에 들어간다 — 데모 계정은 데모 DB에만 있고 권한이 데모 데이터에 한정되므로 수용한다. 운영에 이 변수를 넣는 실수는 CI에서 `NEXT_PUBLIC_DEMO_MODE`가 설정된 빌드를 `prod` 타깃으로 배포하지 못하게 막는다(F1에서는 `.env.example` 주석과 README 경고까지, 배포 파이프라인은 배포 단계에서).
- 대안으로 검토한 "SQL 시드 스크립트"는 FK·해시 비밀번호·파일 레코드를 손으로 맞춰야 해서 깨지기 쉽다. 도메인 코드로 만들면 규칙(상태 전이, 이미지 소유)을 그대로 지킨다.

### 14-2. 이미지

데모 상품 이미지는 `src/main/resources/demo/`의 작은 webp 몇 장을 `file` 모듈로 업로드해 쓴다(실제 업로드 경로를 타므로 `FILE_NOT_OWNED` 규칙과 일치).

## 15. 로컬 실행 (Claude 결정)

- 백엔드: 지금처럼 `docker compose up -d` + `./gradlew bootRun`(8080). 데모 데이터를 쓰려면 `SPRING_PROFILES_ACTIVE=local,demo`.
- 프론트: `cd frontend && pnpm install && pnpm dev`(3000). `frontend/.env.example`:
  ```
  BACKEND_URL=http://localhost:8080        # rewrite 대상 (서버 전용, 브라우저에 노출 안 됨)
  NEXT_PUBLIC_DEMO_MODE=false              # true 는 로컬·데모 서버에서만
  # NEXT_PUBLIC_DEMO_PASSWORD=             # DEMO_MODE 일 때만, 백엔드 KAIROS_DEMO_PASSWORD 와 같은 값
  ```
  루트 `.gitignore`의 `.env.*`/`!.env.example` 규칙이 `frontend/`에도 적용된다.
- 이미지 URL은 백엔드가 `kairos.file.base-url`(기본 `http://localhost:8080`)로 절대 URL을 만든다. 프록시를 거치게 하려면 `.env`에 `KAIROS_FILE_BASE_URL=http://localhost:3000`을 두면 `/files/**` rewrite로 같은 출처에서 받는다. 데모·운영에서는 프론트 도메인 또는 CDN 주소를 넣는다. 백엔드 코드 변경은 없다.
- `compose.yaml`에 프론트 서비스를 **추가하지 않는다**. 백엔드도 호스트에서 띄우는 지금 방식과 같게 둔다. 배포용 `frontend/Dockerfile`(standalone 출력)은 F1에 넣되 compose에는 넣지 않는다.

## 16. 테스트 (사용자 결정: E2E는 핵심 흐름만)

- **단위(Vitest + Testing Library)**: `money.ts`(포맷·할인율·안전 정수), `errors.ts`(코드 → 배치), SKU 조합 생성·옵션 변경 영향 계산, Zod 스키마(OpenAPI 제약과 같은지), `useIdempotentSubmit`(같은 시도 같은 키, 수정 후 새 키), `client.ts` 401 → 단일 reissue → 재시도(fetch mock). 테스트 이름은 백엔드처럼 한국어 문장.
- **E2E(Playwright, 실제 백엔드)**: 백엔드는 `local,demo` 프로필로 떠 있어야 한다(`BACKEND_URL` + `KAIROS_DEMO_PASSWORD`). 각 spec은 고유 이메일로 회원을 새로 만들어 서로 간섭하지 않는다. 흐름 3개만:
  1. `auth.spec`: 가입 → 로그인 → 새로고침 뒤 로그인 유지(쿠키 reissue) → 로그아웃 → 보호 페이지 접근 시 로그인으로.
  2. `c1-seller-onboarding.spec`: 입점 신청 → 관리자 승인(수수료 입력) → 브랜드 신청 → 승인 → 상품 등록(이미지 업로드 포함, 옵션 2축) → 관리자 승인 → 구매자 목록·상세에 보임 → SKU 재고 0 → 품절 표시 → 판매 일시 중지 → 목록에서 사라짐.
  3. `c1-buyer-browse.spec`: 데모 데이터 기준 필터·정렬·검색·페이지가 URL과 결과에 반영되고 뒤로 가기로 복원된다.
- **계약**: `pnpm check:api`(§6). 백엔드 `OpenApiContractTest`가 구현 ↔ 스펙을, 이 검사가 스펙 ↔ 프론트 타입을 묶는다.
- **CI**: 저장소에 워크플로가 아직 없다. F1에서 `.github/workflows/frontend.yml`을 추가한다: `frontend/**`·`docs/api/openapi.yaml` 변경 시 `pnpm install --frozen-lockfile`, `check:api`, `lint`, `typecheck`, `test`, `build`. E2E는 백엔드 컨테이너(MySQL·Redis·bootJar)가 필요해 **CI에서는 돌리지 않고** 로컬 `pnpm e2e`로 PR 전에 돌린다. 틀렸을 때 비용: E2E 회귀를 CI가 못 잡는다 — 백엔드 CI가 생길 때 같은 워크플로에 compose 기반 E2E 잡을 붙인다.
- 명령: `pnpm dev`, `pnpm build`, `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm e2e`, `pnpm gen:api`, `pnpm check:api`.

## 17. F2~F4 범위 (간단히, 각 단계에서 별도 스펙)

- **F2 (C2 구매)**: 장바구니, 주문서(견적 `POST /orders/quote`), 결제(토스 테스트 위젯 + Mock PG 전환), 주문 내역·상세·항목 취소·구매 확정, 판매자 주문 관리(발송·배송 완료), 관리자 주문. Idempotency-Key가 서버에서 검사되기 시작하는 단계(`IDEMPOTENCY_*` 처리 검증). 새 시안 필요(장바구니·주문서·주문 내역·판매자 주문).
- **F3 (C3 혜택·후기)**: 쿠폰 발급·보유·주문 적용, 포인트 잔액·내역·주문 사용, 후기 작성(이미지)·내 후기·판매자 답글·상품 상세 후기 탭, 좋아요 목록. 새 시안 필요.
- **F4 (C4 반품·정산)**: 반품 요청·철회·내역, 판매자 반품 처리·정산 조회(명세), 관리자 반품·정산 생성·확정·지급·조정. 새 시안 필요.
- 모바일 대응, 핫딜(C6) 화면, 프론트 성능 측정은 별도.

## 18. CLAUDE.md 프론트엔드 절 (F1 마지막 작업)

F1의 마지막 작업은 `CLAUDE.md`에 `## 프론트엔드` 절을 추가하는 것이다. 내용(초안):

- 위치 `frontend/`, 스택 한 줄, 명령(`pnpm dev/test/e2e/gen:api/check:api`), 로컬 실행(백엔드 + `pnpm dev`, `BACKEND_URL`).
- 규칙: OpenAPI가 바뀌면 `pnpm gen:api`로 `src/api/schema.d.ts`를 재생성해 **같은 커밋**에 넣는다(`check:api`가 빌드를 막는다). 금액은 `lib/money.ts`로만 계산·표시한다. 모든 POST는 `useIdempotentSubmit`으로 제출한다. 에러 메시지는 `api/errors.ts` 표로만 매핑한다. 데모 기능은 `features/demo`에만 두고 `NEXT_PUBLIC_DEMO_MODE`로 감싼다. `app/`은 조립만, 기능은 `features/`. 데스크톱 전용(1280px).
- 시안 `design/`은 기록이며 고치지 않는다. 새 화면은 시안 → 구현 순서.
- 상단 문서 목록에 이 스펙과 F1 계획을 추가하고, "현재 단계"를 갱신한다.

## 19. 완료 기준 (F1)

- §7-3 백엔드 변경이 OpenAPI → 구현 → 계약 테스트 순서로 끝나고 기존 테스트가 통과한다. 쿠키 reissue, 헤더 없는 쿠키 reissue 거부, logout 쿠키 삭제, `/auth/me`가 통합 테스트로 검증된다.
- `pnpm check:api`·`lint`·`typecheck`·`test`·`build`가 통과하고, CI 워크플로가 같은 것을 돌린다.
- §9의 모든 화면이 실제 백엔드로 동작하고, §16의 E2E 3개가 로컬에서 통과한다.
- `NEXT_PUBLIC_DEMO_MODE` 없이 빌드한 번들에 데모 코드·계정 문자열이 없다(빌드 산출물 grep 검사 1건을 테스트에 둔다). `prod`+`demo` 동시 활성 시 백엔드 기동 실패 테스트.
- 키보드만으로 상품 등록과 검수 승인이 가능하다(E2E 2번 흐름 일부를 키보드로).
- README에 프론트 실행 방법, CLAUDE.md에 §18 절.

## 20. 빌더에게 맡기는 것

- shadcn 컴포넌트 선택과 토큰 매핑의 세부, Tailwind 설정 형식.
- `features/` 안의 파일 분할, 쿼리 키 네이밍, 스켈레톤 모양.
- ImageUploader의 순서 변경 구현(드래그 라이브러리 여부 — 필요 없으면 위/아래 버튼).
- 변경 요청 diff의 표시 방식 세부(필드 단위 전/후 2열은 시안대로).
- 데모 데이터의 구체 내용(상품 수, 카테고리 이름)과 `DemoDataInitializer`의 멱등 판단 기준.
- Playwright 픽스처(회원 생성·로그인·관리자 승인 헬퍼)의 구조.
