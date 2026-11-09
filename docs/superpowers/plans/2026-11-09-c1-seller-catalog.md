# C1 판매자와 상품 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 판매자 입점 → 브랜드 → 상품(옵션/SKU) 등록 → 관리자 심사 → 사용자 상품 찾기(카테고리·브랜드·필터·정렬·검색)까지를 새 모듈 `seller`, `catalog`, `file`로 구현한다.

**Architecture:** 모듈 세 개를 추가한다. `seller`(판매자, 브랜드), `catalog`(카테고리, 상품·옵션·SKU, 변경 요청, 상품 검색), `file`(업로드 파일 테이블, 업로드 API, `ImageStorage`). 모듈 안은 기존과 같은 4계층 DDD, 도메인 모델과 JPA 엔티티 분리, 모듈 간 호출은 호출 측 `application` 인터페이스 → 호출 측 `infrastructure` 구현 → 상대 `application` 서비스. 판매자 상태 확인은 `@AuthenticatedSeller` 인자 해석기 한 곳에서 한다. 상품 검색만 `catalog.infrastructure`가 `brand`·`seller` 테이블을 **읽기 전용 뷰 엔티티**로 매핑해 QueryDSL 조인한다(ADR 0002). 기존 `deal`, `order`, `payment`는 건드리지 않는다.

**Tech Stack:** Kotlin 2.3.21, Spring Boot 4.1.2, JDK 21, MySQL 8.4, Flyway, QueryDSL 7.7(KSP), Spring Security + JWT, springdoc, JUnit5, Testcontainers, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-11-09-c1-seller-catalog-design.md` (실행자는 반드시 함께 읽는다. 특히 3장, 4장, 12장, **13장·14장은 12장·5장의 일부를 대체한다**). 상위: `docs/superpowers/specs/2026-11-09-commerce-roadmap-design.md`.

## Global Constraints

- 루트 패키지 `com.dogyun.kairos`. 새 모듈 패키지 `…seller`, `…catalog`, `…file`. 계층 `presentation/ application/ domain/ infrastructure/`, 의존 방향 `presentation → application → domain ← infrastructure`.
- `domain`은 순수 Kotlin(`org.springframework..`, `jakarta.persistence..`, `tools.jackson..`, `com.fasterxml..` import 금지). `ArchitectureTest`가 강제하며 규칙을 완화하지 않는다.
- 다른 모듈 호출: 호출하는 쪽 `application`에 인터페이스, 자기 `infrastructure`에서 상대 `application` 서비스를 호출해 구현. 다른 모듈의 `domain`/`presentation`/`infrastructure` import 금지. 모듈 간 DTO에 도메인 타입 노출 금지.
- 모든 JPA 엔티티는 `BaseEntity` 상속, `@SQLRestriction("status = 'ACTIVE'")`, 소프트 딜리트만. **예외: 읽기 전용 뷰 엔티티 `BrandView`/`SellerView`(Task 11)는 `@Immutable`이며 `BaseEntity`를 상속하지 않고, 손으로 쓴 status 조건 대신 `@SQLRestriction("status = 'ACTIVE'")`를 단다. ArchUnit 규칙은 테이블 단위 의존만 보므로 영향이 없다.** 도메인 상태 컬럼명은 `seller_status`, `brand_status`, `product_status`, `sku_status`, `change_status`. **SKU는 소프트 딜리트하지 않는다**(스펙 13장): 사라진 조합은 `sku_status = DISCONTINUED`.
- 판매자·브랜드에는 삭제 경로가 없다(정지·반려만). 그래서 `member_id`, `business_number`, 브랜드 `name` UNIQUE가 소프트 딜리트와 충돌하지 않는다.
- 애그리거트 간 ID 참조만, FK 제약 없음. 같은 애그리거트 안 연관관계만 허용: `LAZY`, `@ManyToOne` 단방향 기본. `@ManyToMany`, `@OneToOne` 금지.
- 스키마는 Flyway(`V5__seller.sql`, `V6__file.sql`, `V7__brand.sql`, `V8__category.sql`, `V9__product.sql`, `V10__product_review_history.sql`, `V11__product_change_request.sql`). Task마다 자기 마이그레이션 파일만 만들고 앞선 Task의 마이그레이션은 절대 고치지 않는다. 적용된 파일 수정 금지. 인덱스는 PK/UNIQUE 외 추가 금지(3-2에서 측정 후).
- QueryDSL은 `{module}.infrastructure`의 Repository 구현에서만. `application`은 `PageResult`를 반환, 목록 API는 `?page&size`, 기본 20, 최대 100(넘으면 400 `INVALID_REQUEST`).
- SDD: 각 Task의 첫 단계에서 `docs/api/openapi.yaml`을 먼저 고친다. `OpenApiContractTest`(method+path 집합)와 Task 1의 `ErrorCodeContractTest`(에러 코드 집합)가 매 커밋에서 통과해야 한다.
- 응답: 성공 `ApiResponse(message, data, timestamp)`, 에러 `ErrorResponse(code, message, timestamp)`, 목록 `PageResponse`. 일시는 오프셋 없는 `LocalDateTime`(KST). 금액은 원 단위 `Long`.
- 상태 규칙(스펙 12장): 허용되지 않는 전이는 409 `*_INVALID_STATE_TRANSITION`. 판매자 상태 때문에 막힌 요청은 409 `SELLER_NOT_ACTIVE`. 판매자가 아닌 회원의 판매자 API 호출은 403 `FORBIDDEN`. 다른 판매자의 상품·브랜드는 404 `PRODUCT_NOT_FOUND`/`BRAND_NOT_FOUND`.
- 가격: `0 < 판매가 ≤ 정가`. 할인율 `(original - sale) * 100 / original`(Long 정수 나눗셈 = floor). 가격 필터·정렬은 상품 판매가 기준, SKU 추가 금액 미반영.
- 이미지(스펙 14장): 모든 업로드는 `file` 테이블 한 곳(저장 키, 원본명, 콘텐츠 타입, 크기, 업로더 회원 ID). URL은 저장하지 않고 저장 키로 만든다. 상품·브랜드는 `product_image`/`brand_image` 연결 테이블에 `file_id`로만 참조한다. jpg/png/webp, 파일당 5MB 이하, 업로더 본인 파일만 연결 가능. 응답은 URL.
- `product.seller_id`를 비정규화해 저장한다(브랜드의 판매자와 같아야 하며 브랜드는 판매자를 옮기지 않는다). 권한 검사·노출 조건은 이 컬럼을 쓴다.
- `product.on_sale_at`은 **처음 승인될 때만** 기록하고 이후 덮어쓰지 않는다. 최신순 정렬 기준이다.
- `sales_count`는 상품 컬럼(0)만 두고 C2에서 올리고 취소·환불 시 내린다. 핫딜 시 같은 행 UPDATE 경합은 일부러 남긴다(3-1 측정 대상).
- 기존 `deal`, `order`, `payment` 코드·API·테스트 수정 금지. `Role`은 `USER`, `ADMIN` 그대로(SELLER 역할 없음).
- Lombok 금지, Mock 라이브러리 금지(애플리케이션 서비스 테스트는 인메모리 Fake). 테스트 이름은 한국어.
- 커밋 메시지: 한국어 Conventional Commits, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 테스트 실행 `./gradlew test`(Docker 필요). 단일 클래스는 `./gradlew test --tests '<FQCN>'`.

## Review Focus

1. **정지된 판매자의 상품이 상세 URL로 직접 접근될 때**: 목록뿐 아니라 `GET /api/v1/products/{id}`도 404여야 한다. → Task 11 `정지된_판매자의_상품은_상세도_404다`.
2. **옵션 값 이름만 바꾼 변경 요청**(블랙 → 검정, 값 ID 유지): 해당 조합 SKU의 ID·재고·추가 금액이 유지되어야 한다(조합은 값 ID로 맞춘다). → Task 7 `옵션_값_이름만_바꾸면_SKU_ID_재고_추가금액이_유지된다`.
3. **대분류 필터**: `categoryId`에 대분류를 주면 그 아래 모든 소분류 상품이 나와야 한다(상품은 소분류에만 속한다). → Task 11 `대분류로_필터하면_하위_소분류_상품이_모두_나온다`.
4. **검색어에 `%`나 `_`가 들어간 경우**: LIKE 와일드카드로 해석되어 전체가 매칭되면 안 된다. → Task 11 `검색어의_퍼센트와_언더스코어는_문자로_취급된다`.
5. **대기 중인 변경 요청이 있는 상품을 판매 종료**: 종료는 되고, 변경 요청을 승인해도 ENDED 상품을 되살리지 않아야 한다(승인 시 상품이 ENDED면 409 `PRODUCT_INVALID_STATE_TRANSITION`, 요청은 PENDING으로 남는다). → Task 10 `종료된_상품의_변경_요청은_승인할_수_없다`.

## 계획자 판단(스펙 11장 "빌더에게 맡기는 것"에 대한 결정)

- **정가(original price) 변경은 변경 요청(재검수)을 거친다.** 즉시 반영은 판매가·SKU 재고·추가 금액·판매 여부뿐이다. 심사 없이 정가를 올려 표시 할인율을 부풀리는 것을 막기 위해서다. 판매가 변경 API는 판매가만 받아 `0 < 판매가 ≤ 현재 정가`를 검증하고, 변경 요청은 콘텐츠와 함께 `originalPrice`를 담으며 제출·승인 시 `0 < 현재 판매가 ≤ 새 정가`를 검증한다(위반 `PRODUCT_INVALID_PRICE`).
- **품절 판정**: `ACTIVE` SKU 중 `onSale && stock > 0`인 것이 하나도 없으면 품절. 판매 여부를 끈 SKU는 살 수 없으므로 제외한다.
- **SKU 조합 매칭(스펙 13장)**: 조합 키는 옵션 값 ID를 오름차순 정렬해 `|`로 이은 문자열(`12|57`, 축 0개면 빈 문자열). 새 옵션 값은 저장 시 ID가 생기므로, 재구성은 저장소 구현(`ProductRepositoryImpl.save`)에서 값 ID가 확정된 뒤 수행한다. 도메인은 "유지할 값은 기존 ID를 그대로 보낸다"는 규칙만 검증한다(남의 값 ID → `PRODUCT_INVALID`). 콘텐츠 수정·변경 요청 API의 옵션 값은 `{valueId?, name}` 형태다.
- **콘텐츠 수정 경로를 상태로 나눈다**: `PENDING_REVIEW`·`REJECTED` 상품은 `PUT /seller/products/{id}`로 같은 행을 직접 고치고 `PENDING_REVIEW`로 돌아간다(스펙 12장은 REJECTED만 말하지만, 아직 검수 전인 PENDING_REVIEW도 같은 경로로 둔다). `ON_SALE`·`PAUSED` 상품은 `POST .../change-requests`로만 바꾼다. `ENDED`는 둘 다 409.
- **변경 요청은 수정 불가**: 바꾸려면 철회(`DELETE`) 후 다시 올린다.
- **심사 이력은 모듈별 테이블**(`seller_review_history`, `product_review_history`)에 남기고 조회 API는 두지 않는다.
- **수수료율**은 basis point 정수 `commissionRateBp`(0~10000). C4 정산에서 소수 수수료율이 필요하기 때문이다. C2 주문 항목 스냅샷(상품명, 브랜드명, 옵션 표시명, 판매가, 추가 금액, 판매자 ID, 수수료율)은 C1 응답·스냅샷 DTO에 그 값들이 있으면 충분하다(스펙 13장 "C2 인수 조건" — C1에서 별도 작업 없음).
- **`@AuthenticatedSeller`(스펙 13장)**: 애너테이션·데이터 클래스·`SellerResolver` 인터페이스·`HandlerMethodArgumentResolver`는 `common/security`에 두고, `SellerResolver` 구현은 `seller.infrastructure`가 `seller.application.SellerQueryService`를 호출해 제공한다(common은 ArchUnit 모듈 목록 밖이라 역방향 의존이 없다). 속성 `requireManage`(기본 true): 판매자 없음 → 403 `FORBIDDEN`, `requireManage && !canManage` → 409 `SELLER_NOT_ACTIVE`, `!requireManage && !canView` → 409 `SELLER_NOT_ACTIVE`.
- **파일 업로드 API**는 `POST /api/v1/files/images`(인증 회원 누구나). "판매자 본인 파일" 검증은 연결하는 쪽(seller·catalog)이 업로더 회원 ID == 로그인 회원 ID로 확인한다. 로컬 저장소: `kairos.file.local-dir`(기본 `uploads`), 정적 서빙 `/files/**`, URL `${kairos.file.base-url}/files/{storageKey}`. 고아 파일 정리는 하지 않는다(스펙 12장).
- **공개 브랜드 페이지**는 브랜드 정보만 돌려주고 상품 목록은 `GET /api/v1/products?brandId=`로 받는다.
- **판매 종료(ENDED)** 는 `ON_SALE`·`PAUSED`에서만. `PENDING_REVIEW`·`REJECTED` 상품을 없애는 API는 두지 않는다.
- **브랜드 정보 수정**은 `REJECTED` 상태의 재신청만 지원한다.
- **개발용 초기 데이터는 넣지 않는다**(C5).
- **SELLER_NOT_ACTIVE 판정**: `PENDING`·`REJECTED` → 신청 조회/수정 외 모두 차단, `SUSPENDED` → 조회와 상품 `pause`/`end`만 허용, `APPROVED` → 전부 허용.

---

## 파일 구조

```
docs/api/openapi.yaml                                  (Task마다 경로 추가)
docs/adr/0002-catalog-search-cross-module-read-join.md (Task 11)
src/main/resources/db/migration/V5__seller.sql … V11__product_change_request.sql
src/main/kotlin/com/dogyun/kairos/
  common/domain/ErrorCode.kt                          (Task마다 코드 추가)
  common/security/SecurityConfig.kt                   (Task 1), AuthenticatedSeller.kt, SellerResolver.kt,
                  AuthenticatedSellerArgumentResolver.kt, WebMvcArgumentResolverConfig.kt (Task 2)
  seller/
    domain/        Seller.kt, SellerId.kt, SellerStatus.kt, SettlementAccount.kt, BusinessNumber.kt, SellerRepository.kt,
                   ReviewHistory.kt, ReviewHistoryRepository.kt, Brand.kt, BrandId.kt, BrandStatus.kt, BrandRepository.kt
    application/   SellerService.kt, SellerAdminService.kt, SellerQueryService.kt, SellerCommands.kt, SellerResults.kt,
                   BrandService.kt, BrandAdminService.kt, BrandQueryService.kt, BrandCommands.kt, BrandResults.kt,
                   ImageFileReader.kt (file 모듈 호출 인터페이스)
    infrastructure/ SellerEntity.kt, SellerJpaRepository.kt, SellerRepositoryImpl.kt, SellerResolverImpl.kt,
                   ReviewHistoryEntity.kt, ReviewHistoryJpaRepository.kt, ReviewHistoryRepositoryImpl.kt,
                   BrandEntity.kt, BrandImageEntity.kt, BrandJpaRepository.kt, BrandImageJpaRepository.kt, BrandRepositoryImpl.kt,
                   ImageFileReaderImpl.kt
    presentation/  SellerController.kt, AdminSellerController.kt, SellerRequests.kt,
                   SellerBrandController.kt, AdminBrandController.kt, BrandController.kt, BrandRequests.kt
  file/
    domain/        StoredFile.kt, ImageFormat.kt, StoredFileRepository.kt
    application/   ImageStorage.kt, FileService.kt, FileQueryService.kt, FileResults.kt
    infrastructure/ StoredFileEntity.kt, StoredFileJpaRepository.kt, StoredFileRepositoryImpl.kt,
                   FileProperties.kt, LocalFileImageStorage.kt, FileStaticResourceConfig.kt
    presentation/  FileController.kt
  catalog/
    domain/        Category.kt, CategoryId.kt, CategoryRepository.kt,
                   Product.kt, ProductId.kt, ProductStatus.kt, ProductContent.kt, ProductOption.kt, OptionValue.kt,
                   Sku.kt, SkuId.kt, SkuStatus.kt, ProductRepository.kt,
                   ProductChangeRequest.kt, ChangeRequestStatus.kt, ProductChangeRequestRepository.kt,
                   ProductReviewHistory.kt, ProductReviewHistoryRepository.kt,
                   ProductSearch.kt, ProductSearchRepository.kt
    application/   CategoryService.kt, CategoryQueryService.kt, CategoryUsageChecker.kt, CategoryCommands.kt, CategoryResults.kt,
                   SellerReader.kt, ImageFileReader.kt (다른 모듈 호출 인터페이스),
                   ProductService.kt, ProductAdminService.kt, ProductQueryService.kt, ProductCommands.kt, ProductResults.kt,
                   ChangeRequestService.kt, ChangeRequestAdminService.kt, ProductSearchService.kt, ProductSearchResults.kt
    infrastructure/ CategoryEntity.kt, CategoryJpaRepository.kt, CategoryRepositoryImpl.kt,
                   ProductEntity.kt, ProductImageEntity.kt, ProductOptionEntity.kt, ProductOptionValueEntity.kt, SkuEntity.kt,
                   ProductJpaRepository.kt, ProductImageJpaRepository.kt, ProductOptionJpaRepository.kt,
                   ProductOptionValueJpaRepository.kt, SkuJpaRepository.kt, ProductRepositoryImpl.kt,
                   ProductChangeRequestEntity.kt, ProductChangeRequestJpaRepository.kt, ProductChangeRequestRepositoryImpl.kt,
                   ProductReviewHistoryEntity.kt, ProductReviewHistoryJpaRepository.kt, ProductReviewHistoryRepositoryImpl.kt,
                   BrandView.kt, SellerView.kt, ProductSearchRepositoryImpl.kt,
                   SellerReaderImpl.kt, ImageFileReaderImpl.kt, ProductCategoryUsageChecker.kt
    presentation/  CategoryController.kt, AdminCategoryController.kt, CategoryRequests.kt,
                   SellerProductController.kt, AdminProductController.kt, ProductRequests.kt,
                   SellerChangeRequestController.kt, AdminChangeRequestController.kt, ProductController.kt
src/test/kotlin/com/dogyun/kairos/
  ErrorCodeContractTest.kt, CommerceTestSupport.kt, CommerceAuthorizationApiTest.kt, ArchitectureRules.kt(수정)
  seller/…, file/…, catalog/… (domain 단위, application Fake, presentation ApiTest)
```

---

### Task 1: 모듈 등록, 보안 경로, 에러 코드 계약 테스트

**Files:**
- Modify: `src/test/kotlin/com/dogyun/kairos/ArchitectureRules.kt:9`, `src/main/kotlin/com/dogyun/kairos/common/security/SecurityConfig.kt:67-69`, `src/main/resources/application.yaml`, `docs/api/openapi.yaml:1-27`
- Create: `src/test/kotlin/com/dogyun/kairos/ErrorCodeContractTest.kt`

**Interfaces:**
- Produces: 공개 GET 경로 `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/brands/**`, `/files/**`; `/api/v1/seller/**`, `/api/v1/files/**`는 인증만, `/api/v1/admin/**`은 ADMIN(기존).

- [ ] **Step 1: 실패하는 테스트 작성** — `ErrorCodeContractTest`(순수 단위): `docs/api/openapi.yaml`의 `components.schemas.ErrorResponse.properties.code.enum` 집합 == `ErrorCode.entries.map { it.name }` 집합. 이름 `에러_코드_enum과_OpenAPI_code_enum이_일치한다`. YAML 파싱은 Spring Boot에 이미 포함된 SnakeYAML을 쓴다(새 의존성 없음). 먼저 `ErrorCode`에 `SELLER_NOT_ACTIVE(409, "판매자 상태 때문에 처리할 수 없습니다.")`를 추가해 실패를 만든다.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests 'com.dogyun.kairos.ErrorCodeContractTest'` → FAIL.
- [ ] **Step 3: OpenAPI 갱신** — `info.title` `Kairos Commerce API`, `info.version` `0.2.0`, description에 "C1: 판매자·브랜드·카테고리·상품·파일" 한 줄. `tags` 추가: `Seller`, `Admin Seller`, `Brand`, `Seller Brand`, `Admin Brand`, `File`, `Category`, `Admin Category`, `Product`, `Seller Product`, `Admin Product`, `Seller Change Request`, `Admin Change Request`. `ErrorResponse.code.enum`에 `SELLER_NOT_ACTIVE`.
- [ ] **Step 4: 모듈 등록** — `ArchitectureRules.modules = listOf("member", "deal", "order", "payment", "seller", "catalog", "file")`.
- [ ] **Step 5: 보안·설정** — `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/categories/**", "/api/v1/brands/**", "/files/**").permitAll()`(기존 deals 줄 아래). `application.yaml`: `spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 6MB`.
- [ ] **Step 6: 통과 확인** — `./gradlew test` 전체 PASS.
- [ ] **Step 7: 커밋** `chore: seller·catalog·file 모듈 등록과 에러 코드 계약 테스트 추가`

### Task 2: 판매자 도메인, 입점 신청 API, `@AuthenticatedSeller`

**Files:**
- Create: `V5__seller.sql`, `seller/domain/{Seller,SellerId,SellerStatus,SettlementAccount,BusinessNumber,SellerRepository,ReviewHistory,ReviewHistoryRepository}.kt`, `seller/application/{SellerService,SellerQueryService,SellerCommands,SellerResults}.kt`, `seller/infrastructure/{SellerEntity,SellerJpaRepository,SellerRepositoryImpl,SellerResolverImpl,ReviewHistoryEntity,ReviewHistoryJpaRepository,ReviewHistoryRepositoryImpl}.kt`, `seller/presentation/{SellerController,SellerRequests}.kt`, `common/security/{AuthenticatedSeller,SellerResolver,AuthenticatedSellerArgumentResolver,WebMvcArgumentResolverConfig}.kt`
- Test: `seller/domain/SellerTest.kt`, `seller/application/SellerServiceTest.kt`, `seller/presentation/SellerApiTest.kt`, `common/security/AuthenticatedSellerArgumentResolverTest.kt`, `src/test/kotlin/com/dogyun/kairos/CommerceTestSupport.kt`
- Modify: `ErrorCode.kt`, `openapi.yaml`

**Interfaces:**
- Produces(domain): `enum SellerStatus { PENDING, APPROVED, REJECTED, SUSPENDED }`; `@JvmInline value class SellerId(val value: Long)`; `@JvmInline value class BusinessNumber(val value: String)`(`^\d{3}-\d{2}-\d{5}$` 아니면 `DomainException(SELLER_INVALID)`); `data class SettlementAccount(bank: String, accountNumber: String, holder: String)`;
  `class Seller(id: SellerId?, memberId: Long, companyName, businessNumber, representativeName, phone, settlementAccount, commissionRateBp: Int, status, rejectReason: String?, suspendReason: String?)` — `companion fun apply(memberId, companyName, businessNumber, representativeName, phone, settlementAccount): Seller`(PENDING, bp 0), `fun restore(...)`; `fun updateApplication(...)`(PENDING·REJECTED → PENDING, 사유 null), `fun approve(commissionRateBp)`(PENDING→APPROVED, bp 0..10000 아니면 SELLER_INVALID), `fun reject(reason)`(PENDING→REJECTED), `fun suspend(reason)`(APPROVED→SUSPENDED), `fun unsuspend()`(SUSPENDED→APPROVED); 위반 `SELLER_INVALID_STATE_TRANSITION`. `val canManage get() = status == APPROVED`, `val canView get() = status == APPROVED || status == SUSPENDED`.
  `enum ReviewTarget { SELLER, BRAND }`, `enum ReviewResult { APPROVED, REJECTED, SUSPENDED, UNSUSPENDED }`, `class ReviewHistory(id: Long?, target, targetId: Long, result, reason: String?, reviewerId: Long, reviewedAt: LocalDateTime)`.
  `interface SellerRepository { save; findById(SellerId): Seller?; findByMemberId(memberId: Long): Seller?; existsByBusinessNumber(BusinessNumber): Boolean; findPage(status: SellerStatus?, page, size): PageResult<Seller> }`, `interface ReviewHistoryRepository { save; findAllByTarget(target, targetId): List<ReviewHistory> }`.
- Produces(application): `SellerApplicationCommand(companyName, businessNumber, representativeName, phone, bank, accountNumber, holder)`; `SellerDetail(sellerId, memberId, companyName, businessNumber, representativeName, phone, bank, accountNumber, holder, commissionRateBp, status: String, rejectReason, suspendReason)`;
  `SellerService(sellerRepository)`: `@Transactional fun apply(memberId, cmd): Long`(이미 있으면 `SELLER_ALREADY_EXISTS`, 사업자번호 중복 `SELLER_BUSINESS_NUMBER_DUPLICATED`), `fun updateApplication(memberId, cmd)`(없으면 `SELLER_NOT_FOUND`).
  `SellerQueryService(sellerRepository)`: `getMine(memberId): SellerDetail`, **`findSnapshotByMemberId(memberId): SellerSnapshot?`**(`data class SellerSnapshot(sellerId: Long, memberId: Long, canManage: Boolean, canView: Boolean)`, 다른 모듈·리졸버용), `listForAdmin(status?, page, size): PageResult<SellerDetail>`.
- Produces(common/security): `@Target(VALUE_PARAMETER) annotation class AuthenticatedSeller(val requireManage: Boolean = true)`; `data class SellerPrincipal(val sellerId: Long, val memberId: Long, val canManage: Boolean, val canView: Boolean)`; `interface SellerResolver { fun resolve(memberId: Long): SellerPrincipal? }`; `class AuthenticatedSellerArgumentResolver(sellerResolver) : HandlerMethodArgumentResolver`(인증 없음 → `UNAUTHORIZED`, 판매자 없음 → `FORBIDDEN`, 상태 부적합 → `SELLER_NOT_ACTIVE`; 모두 `DomainException`), `WebMvcArgumentResolverConfig : WebMvcConfigurer`. `SellerResolverImpl`(`seller.infrastructure`)은 `SellerQueryService.findSnapshotByMemberId`를 번역한다.
- Produces(test): `abstract class CommerceTestSupport : IntegrationTestSupport()` — `objectMapper`, `jdbcTemplate`, `json(result)`, `adminToken()`, `newUserToken(): String`(랜덤 이메일 가입+로그인, `OrderApiTest.userToken` 방식), `post/put/patch/get/delete(path, token?, body?)` → `ResultActionsDsl`; `@BeforeTest cleanUp()`은 `seller_review_history, seller`를 비운다(이후 Task가 테이블을 늘린다).

- [ ] **Step 1: OpenAPI** — `POST /api/v1/seller/application`(201 `{sellerId}`; 400 `SELLER_INVALID`; 409 `SELLER_ALREADY_EXISTS`, `SELLER_BUSINESS_NUMBER_DUPLICATED`), `PUT /api/v1/seller/application`(200; 404 `SELLER_NOT_FOUND`; 409 `SELLER_INVALID_STATE_TRANSITION`), `GET /api/v1/seller/me`(200 `SellerDetail`; 404). 스키마 `SellerApplicationRequest`, `SellerDetail`, `SellerStatus`. 에러 enum에 `SELLER_INVALID`, `SELLER_NOT_FOUND`, `SELLER_ALREADY_EXISTS`, `SELLER_BUSINESS_NUMBER_DUPLICATED`, `SELLER_INVALID_STATE_TRANSITION`.
- [ ] **Step 2: ErrorCode** 다섯 개(400/404/409/409/409). `ErrorCodeContractTest` PASS.
- [ ] **Step 3: 도메인 실패 테스트** — `SellerTest`: `신청하면_PENDING이고_수수료율은_0이다`, `사업자등록번호_형식이_틀리면_SELLER_INVALID`, `PENDING에서_승인하면_APPROVED_수수료율_저장`, `승인된_판매자를_다시_승인하면_INVALID_STATE_TRANSITION`, `반려된_판매자가_신청을_고치면_PENDING으로_돌아가고_사유가_지워진다`, `APPROVED에서_정지_SUSPENDED에서_해제`, `PENDING에서_정지는_INVALID_STATE_TRANSITION`, `수수료율_10001은_SELLER_INVALID`, `canManage는_APPROVED만_canView는_APPROVED와_SUSPENDED`.
- [ ] **Step 4: 실패 확인** → 컴파일 오류.
- [ ] **Step 5: 도메인 구현** — 전이 규칙은 `private fun transit(from: Set<SellerStatus>, to: SellerStatus)` 하나로.
- [ ] **Step 6: 도메인 PASS** — `./gradlew test --tests 'com.dogyun.kairos.seller.domain.SellerTest'`.
- [ ] **Step 7: 마이그레이션** — `V5__seller.sql`: `seller(id, member_id UNIQUE uk_seller_member_id, company_name VARCHAR(100), business_number VARCHAR(12) UNIQUE uk_seller_business_number, representative_name VARCHAR(50), phone VARCHAR(20), bank VARCHAR(50), account_number VARCHAR(50), account_holder VARCHAR(50), commission_rate_bp INT, seller_status VARCHAR(20), reject_reason VARCHAR(500) NULL, suspend_reason VARCHAR(500) NULL, + BaseEntity 5컬럼)`; `seller_review_history(id, target_type VARCHAR(20), target_id BIGINT, result VARCHAR(20), reason VARCHAR(500) NULL, reviewer_id BIGINT, reviewed_at DATETIME(6), + BaseEntity)`. 타입 규약은 `V2__deal.sql`과 같다.
- [ ] **Step 8: 애플리케이션·리졸버 실패 테스트** — `SellerServiceTest`(`FakeSellerRepository` 인메모리 Map): `입점_신청하면_PENDING_판매자가_저장된다`, `이미_판매자인_회원의_신청은_SELLER_ALREADY_EXISTS`, `사업자등록번호가_중복되면_SELLER_BUSINESS_NUMBER_DUPLICATED`, `승인된_판매자가_신청을_고치면_INVALID_STATE_TRANSITION`, `스냅샷은_상태를_canManage_canView로_번역한다`(APPROVED→true/true, SUSPENDED→false/true, PENDING→false/false, 없으면 null). `AuthenticatedSellerArgumentResolverTest`(Fake `SellerResolver`, `MethodParameter`는 테스트용 더미 컨트롤러 메서드에서 얻는다): `판매자가_없으면_FORBIDDEN`, `requireManage인데_canManage가_아니면_SELLER_NOT_ACTIVE`, `requireManage_false면_canView만_있으면_통과`.
- [ ] **Step 9: 구현** — `SellerService`, `SellerQueryService`, `SellerEntity`(`@Table("seller")`, `seller_status`), `SellerRepositoryImpl`(`findPage` QueryDSL, `id desc`), `ReviewHistory*` 인프라(저장소만; Task 3이 쓴다), `SellerResolverImpl`, common/security 네 파일, `SellerController`(`/api/v1/seller`, `@AuthenticationPrincipal LoginMember` — 신청·내 정보는 판매자가 아닐 수 있으므로 `@AuthenticatedSeller`를 쓰지 않는다). 요청 DTO `@field:NotBlank`, 사업자번호 `@field:Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$")`.
- [ ] **Step 10: 단위 PASS**.
- [ ] **Step 11: API 통합 테스트** — `CommerceTestSupport` 작성 후 `SellerApiTest`: `입점_신청하면_201과_내_정보가_PENDING이다`, `비로그인_신청은_401`, `두_번_신청하면_409_SELLER_ALREADY_EXISTS`, `판매자가_아닌_회원의_내_정보는_404_SELLER_NOT_FOUND`, `사업자등록번호_형식_오류는_400`.
- [ ] **Step 12: 전체 테스트** `./gradlew test` PASS.
- [ ] **Step 13: 커밋** `feat: 판매자 도메인·입점 신청 API·AuthenticatedSeller 인자 해석기`

### Task 3: 관리자 판매자 심사(승인·반려·정지·해제)와 심사 이력

**Files:**
- Create: `seller/application/SellerAdminService.kt`, `seller/presentation/AdminSellerController.kt`
- Modify: `SellerRequests.kt`, `openapi.yaml`, `CommerceTestSupport.kt`
- Test: `seller/application/SellerAdminServiceTest.kt`, `seller/presentation/AdminSellerApiTest.kt`

**Interfaces:**
- Consumes: Task 2 `Seller`, `SellerRepository`, `ReviewHistoryRepository`, `SellerQueryService.listForAdmin`.
- Produces: `SellerAdminService(sellerRepository, reviewHistoryRepository, clock)`: `approve(sellerId: Long, commissionRateBp: Int, reviewerId: Long)`, `reject(sellerId, reason, reviewerId)`, `suspend(sellerId, reason, reviewerId)`, `unsuspend(sellerId, reviewerId)` — 전이 후 `ReviewHistory(SELLER, sellerId, result, reason, reviewerId, now)` 저장; 없으면 `SELLER_NOT_FOUND`.
- Produces(test): `CommerceTestSupport.approvedSeller(token, businessNumber = 랜덤): Long`(신청 → 관리자 승인 bp 1000 → sellerId), `suspendSeller(sellerId)`, `unsuspendSeller(sellerId)`.

- [ ] **Step 1: OpenAPI** — `GET /api/v1/admin/sellers?status&page&size`(200 `PageResponse<SellerDetail>`), `POST /api/v1/admin/sellers/{sellerId}/approve`(`{commissionRateBp}`), `/reject`(`{reason}`), `/suspend`(`{reason}`), `/unsuspend`. 404 `SELLER_NOT_FOUND`, 409 `SELLER_INVALID_STATE_TRANSITION`, 403.
- [ ] **Step 2: 실패 테스트** — `SellerAdminServiceTest`(Fake 저장소 둘): `승인하면_APPROVED가_되고_이력이_남는다`, `반려_사유가_판매자와_이력에_남는다`, `정지_후_해제하면_APPROVED이고_이력이_두_건이다`, `없는_판매자_승인은_SELLER_NOT_FOUND`.
- [ ] **Step 3: 실패 확인**, **Step 4: 구현** — `AdminSellerController`(`/api/v1/admin/sellers`, `reviewerId = loginMember.id`), `ApproveSellerRequest(@field:Min(0) @field:Max(10000) commissionRateBp)`, `ReasonRequest(@field:NotBlank @field:Size(max = 500) reason)`.
- [ ] **Step 5: 단위 PASS**, **Step 6: API 테스트** — `AdminSellerApiTest`: `관리자가_승인하면_판매자_내_정보가_APPROVED_수수료율_1000`, `상태_필터로_심사_목록을_조회한다`, `일반_회원이_관리자_API를_부르면_403`, `승인된_판매자_재승인은_409_SELLER_INVALID_STATE_TRANSITION`, `정지_해제_후_내_정보가_APPROVED다`.
- [ ] **Step 7: 전체 PASS, 커밋** `feat: 관리자 판매자 심사 API와 심사 이력`

### Task 4: file 모듈(업로드 테이블, 이미지 업로드 API, 로컬 저장소, 정적 서빙)

**Files:**
- Create: `V6__file.sql`, `file/domain/{StoredFile,ImageFormat,StoredFileRepository}.kt`, `file/application/{ImageStorage,FileService,FileQueryService,FileResults}.kt`, `file/infrastructure/{StoredFileEntity,StoredFileJpaRepository,StoredFileRepositoryImpl,FileProperties,LocalFileImageStorage,FileStaticResourceConfig}.kt`, `file/presentation/FileController.kt`
- Modify: `application.yaml`, `application-test.yaml`, `.env.example`, `.gitignore`(`uploads/`), `ErrorCode.kt`, `openapi.yaml`, `CommerceTestSupport.kt`(cleanUp `file`)
- Test: `file/domain/StoredFileTest.kt`, `file/application/FileServiceTest.kt`, `file/presentation/FileApiTest.kt`

**Interfaces:**
- Produces(domain): `enum ImageFormat(val contentType: String, val extension: String) { JPEG("image/jpeg","jpg"), PNG("image/png","png"), WEBP("image/webp","webp") }` + `companion fun fromContentType(ct: String?): ImageFormat`(아니면 `FILE_UNSUPPORTED_FORMAT`); `class StoredFile(id: Long?, storageKey: String, originalName: String, contentType: String, sizeBytes: Long, uploaderMemberId: Long)`(`sizeBytes in 1..5_242_880` 아니면 `FILE_INVALID`; `val isImage get() = ImageFormat.entries.any { it.contentType == contentType }`); `interface StoredFileRepository { save; findAllByIds(ids: Collection<Long>): List<StoredFile> }`.
- Produces(application): `interface ImageStorage { fun store(storageKey: String, bytes: ByteArray, contentType: String) }`; `data class ImageFileInfo(val fileId: Long, val url: String, val uploaderMemberId: Long, val isImage: Boolean)`;
  `FileService(repository, imageStorage, fileQueryService)`: `fun uploadImage(memberId: Long, originalName: String?, contentType: String?, bytes: ByteArray): UploadResult(fileId, url)`(storageKey = `images/${UUID}.${ext}`); 
  `FileQueryService(repository, properties)`: **`fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileInfo>`**(없는 ID는 빠진다), **`fun urlOf(storageKey: String): String`** = `${baseUrl}/files/${storageKey}`.
  `@ConfigurationProperties("kairos.file") data class FileProperties(val localDir: String = "uploads", val baseUrl: String = "http://localhost:8080")`; `LocalFileImageStorage`(`Files.createDirectories` + `Files.write(localDir/storageKey)`); `FileStaticResourceConfig : WebMvcConfigurer`(`/files/**` → `file:${localDir}/`).
- Produces(test): `CommerceTestSupport.uploadImage(token): Long /* fileId */`(1x1 PNG 바이트 multipart).

- [ ] **Step 1: OpenAPI** — `POST /api/v1/files/images`(multipart `file`, 인증 필요, 201 `{fileId, url}`; 400 `FILE_UNSUPPORTED_FORMAT`, `FILE_INVALID`; 413 `INVALID_REQUEST`). 에러 enum에 `FILE_UNSUPPORTED_FORMAT`, `FILE_INVALID`, `FILE_NOT_OWNED`(Task 5·8에서 사용).
- [ ] **Step 2: ErrorCode** — `FILE_UNSUPPORTED_FORMAT(400, "jpg, png, webp만 업로드할 수 있습니다.")`, `FILE_INVALID(400, "파일이 올바르지 않습니다.")`, `FILE_NOT_OWNED(400, "이 서버에 업로드한 본인 이미지 파일만 사용할 수 있습니다.")`.
- [ ] **Step 3: 실패 테스트** — `StoredFileTest`: `gif는_FILE_UNSUPPORTED_FORMAT`, `0바이트는_FILE_INVALID`, `5MB_초과는_FILE_INVALID`. `FileServiceTest`(Fake 저장소 + `FakeImageStorage`가 저장 키·bytes 기록): `업로드하면_저장_키가_images_UUID_확장자이고_URL이_baseUrl_files_키다`, `findImages는_없는_ID를_뺀다`.
- [ ] **Step 4: 실패 확인**, **Step 5: 구현** — `V6__file.sql`: `file(id, storage_key VARCHAR(255) UNIQUE uk_file_storage_key, original_name VARCHAR(255), content_type VARCHAR(100), size_bytes BIGINT, uploader_member_id BIGINT, + BaseEntity)`. `FileController`(`@RequestPart("file") MultipartFile`, `@AuthenticationPrincipal LoginMember`). `application.yaml`: `kairos.file.local-dir: ${KAIROS_FILE_DIR:uploads}`, `base-url: ${KAIROS_FILE_BASE_URL:http://localhost:8080}`; `.env.example`에 두 키 주석; `.gitignore`에 `uploads/`; `application-test.yaml`에 `kairos.file.local-dir: build/test-uploads`.
- [ ] **Step 6: 단위 PASS**, **Step 7: API 테스트** — `FileApiTest`: `PNG를_올리면_201과_URL을_받고_비로그인_GET으로_내려받을_수_있다`(Content-Type `image/png`), `텍스트_파일은_400_FILE_UNSUPPORTED_FORMAT`, `비로그인_업로드는_401`, `6MB_파일은_413`(기존 `GlobalExceptionHandler`가 `MaxUploadSizeExceededException`을 413 `INVALID_REQUEST`로 처리).
- [ ] **Step 8: 전체 PASS, 커밋** `feat: file 모듈(이미지 업로드, 로컬 저장소, 정적 서빙)`

### Task 5: 브랜드(판매자 신청, 관리자 심사, 공개 브랜드 페이지, 로고 연결)

**Files:**
- Create: `V7__brand.sql`, `seller/domain/{Brand,BrandId,BrandStatus,BrandRepository}.kt`, `seller/application/{BrandService,BrandAdminService,BrandQueryService,BrandCommands,BrandResults,ImageFileReader}.kt`, `seller/infrastructure/{BrandEntity,BrandImageEntity,BrandJpaRepository,BrandImageJpaRepository,BrandRepositoryImpl,ImageFileReaderImpl}.kt`, `seller/presentation/{SellerBrandController,AdminBrandController,BrandController,BrandRequests}.kt`
- Modify: `ErrorCode.kt`, `openapi.yaml`, `CommerceTestSupport.kt`(cleanUp `brand_image, brand`)
- Test: `seller/domain/BrandTest.kt`, `seller/application/BrandServiceTest.kt`, `seller/presentation/BrandApiTest.kt`

**Interfaces:**
- Consumes: `@AuthenticatedSeller SellerPrincipal`, `ReviewHistoryRepository`(target BRAND), `file.application.FileQueryService.findImages`.
- Produces(domain): `enum BrandStatus { PENDING, APPROVED, REJECTED }`; `class Brand(id: BrandId?, sellerId: Long, name: String, englishName: String?, logoFileId: Long?, description: String?, status, rejectReason: String?)` — `companion fun apply(...)`(name 1..50자 아니면 `BRAND_INVALID`), `fun reapply(name, englishName, logoFileId, description)`(REJECTED→PENDING만), `fun approve()`, `fun reject(reason)`(PENDING에서만; 위반 `BRAND_INVALID_STATE_TRANSITION`); `interface BrandRepository { save; findById(BrandId): Brand?; existsByName(name): Boolean; findAllBySellerId(sellerId): List<Brand>; findPage(status?, page, size): PageResult<Brand>; findAllByIds(ids): List<Brand> }`. 로고는 `brand_image(brand_id, file_id, purpose='LOGO')` 한 행으로 저장하고, `BrandRepositoryImpl.save`가 기존 LOGO 행을 소프트 딜리트한 뒤 새 행을 넣는다.
- Produces(application): `interface ImageFileReader { fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> }` + `data class ImageFileRef(fileId: Long, url: String, uploaderMemberId: Long, isImage: Boolean)`(seller.application 소유); `ImageFileReaderImpl`(seller.infrastructure)은 `FileQueryService.findImages`를 번역한다.
  `BrandApplicationCommand(name, englishName?, logoFileId?, description?)`; `BrandDetail(brandId, sellerId, name, englishName, logoUrl: String?, description, status: String, rejectReason)`; `BrandPublic(brandId, name, englishName, logoUrl, description)`;
  `BrandService(brandRepository, imageFileReader)`: `apply(seller: SellerPrincipal, cmd): Long`(이름 중복 `BRAND_NAME_DUPLICATED`; logoFileId가 있으면 `findImages`에 존재하고 `isImage`이고 `uploaderMemberId == seller.memberId` 아니면 `FILE_NOT_OWNED`), `reapply(seller, brandId, cmd)`(남의 것 → `BRAND_NOT_FOUND`), `listMine(seller): List<BrandDetail>`.
  `BrandAdminService(brandRepository, reviewHistoryRepository, imageFileReader, clock)`: `approve(brandId, reviewerId)`, `reject(brandId, reason, reviewerId)`, `listForAdmin(status?, page, size): PageResult<BrandDetail>`.
  `BrandQueryService(brandRepository, sellerRepository, imageFileReader)`: `getPublic(brandId): BrandPublic`(브랜드 APPROVED이고 판매자 APPROVED 아니면 `BRAND_NOT_FOUND`), **`findSnapshot(brandId): BrandSnapshot?`**(`data class BrandSnapshot(brandId: Long, sellerId: Long, name: String, approved: Boolean)`), **`getNames(brandIds): Map<Long, String>`**.
- Produces(test): `CommerceTestSupport.approvedBrand(sellerToken, name = 랜덤, logoFileId: Long? = null): Long`.

- [ ] **Step 1: OpenAPI** — `POST /api/v1/seller/brands`(201 `{brandId}`), `GET /api/v1/seller/brands`(200 `[BrandDetail]`), `PUT /api/v1/seller/brands/{brandId}`, `GET /api/v1/admin/brands?status&page&size`, `POST /api/v1/admin/brands/{brandId}/approve`, `/reject {reason}`, `GET /api/v1/brands/{brandId}`(공개, 200 `BrandPublic`; 404). 요청 스키마의 로고는 `logoFileId`(int64, nullable), 응답은 `logoUrl`. 에러 enum에 `BRAND_INVALID`, `BRAND_NOT_FOUND`, `BRAND_NAME_DUPLICATED`, `BRAND_INVALID_STATE_TRANSITION`.
- [ ] **Step 2: ErrorCode** 네 개(400/404/409/409).
- [ ] **Step 3: 실패 테스트** — `BrandTest`: `신청하면_PENDING`, `승인_후_반려는_INVALID_STATE_TRANSITION`, `반려된_브랜드만_재신청할_수_있다`, `이름이_비면_BRAND_INVALID`. `BrandServiceTest`(Fake 저장소, `FakeImageFileReader(files: Map<Long, ImageFileRef>)`): `이름_중복은_BRAND_NAME_DUPLICATED`, `다른_회원이_올린_로고는_FILE_NOT_OWNED`, `없는_파일_ID는_FILE_NOT_OWNED`, `다른_판매자의_브랜드_재신청은_BRAND_NOT_FOUND`, `공개_조회는_판매자가_정지되면_BRAND_NOT_FOUND`, `상세의_logoUrl은_파일_모듈이_준_URL이다`.
- [ ] **Step 4: 실패 확인**, **Step 5: 구현** — `V7__brand.sql`: `brand(id, seller_id, name VARCHAR(50) UNIQUE uk_brand_name, english_name VARCHAR(100) NULL, description VARCHAR(1000) NULL, brand_status VARCHAR(20), reject_reason VARCHAR(500) NULL, + BaseEntity)`, `brand_image(id, brand_id, file_id, purpose VARCHAR(20), + BaseEntity)`. 컨트롤러: 판매자용은 `@AuthenticatedSeller seller: SellerPrincipal`(목록은 `requireManage = false`).
- [ ] **Step 6: 단위 PASS**, **Step 7: API 테스트** — `BrandApiTest`: `로고와_함께_신청_승인_후_공개_페이지에_logoUrl이_보인다`, `승인_전_공개_페이지는_404`, `판매자_정지_후_공개_페이지는_404`, `PENDING_판매자의_브랜드_신청은_409_SELLER_NOT_ACTIVE`, `다른_판매자의_브랜드_수정은_404`, `반려_후_재신청하면_PENDING`.
- [ ] **Step 8: 전체 PASS, 커밋** `feat: 브랜드 신청·심사·공개 조회 API와 로고 파일 연결`

### Task 6: 카테고리(2단계 트리) 관리자 CRUD와 공개 조회

**Files:**
- Create: `V8__category.sql`, `catalog/domain/{Category,CategoryId,CategoryRepository}.kt`, `catalog/application/{CategoryService,CategoryQueryService,CategoryUsageChecker,CategoryCommands,CategoryResults}.kt`, `catalog/infrastructure/{CategoryEntity,CategoryJpaRepository,CategoryRepositoryImpl,NoProductCategoryUsageChecker}.kt`, `catalog/presentation/{CategoryController,AdminCategoryController,CategoryRequests}.kt`
- Modify: `ErrorCode.kt`, `openapi.yaml`, `CommerceTestSupport.kt`(cleanUp `category`)
- Test: `catalog/domain/CategoryTest.kt`, `catalog/application/CategoryServiceTest.kt`, `catalog/presentation/CategoryApiTest.kt`

**Interfaces:**
- Produces(domain): `class Category(id: CategoryId?, parentId: CategoryId?, name: String, displayOrder: Int)` — `companion fun root(name, displayOrder)`, `fun child(parent: Category, name, displayOrder)`(parent가 소분류면 `CATEGORY_INVALID`), `val isLeaf get() = parentId != null`, `fun rename(name, displayOrder)`; name 1..50자 아니면 `CATEGORY_INVALID`. `interface CategoryRepository { save; findById(CategoryId): Category?; findAll(): List<Category>; findChildren(parentId): List<Category>; delete(id) /* soft */ }`.
- Produces(application): `interface CategoryUsageChecker { fun hasProducts(categoryId: Long): Boolean }`(이번 Task 구현 `NoProductCategoryUsageChecker`는 항상 false; Task 8이 교체); `CreateCategoryCommand(parentId: Long?, name, displayOrder)`, `UpdateCategoryCommand(name, displayOrder)`, `CategoryNode(categoryId, name, displayOrder, children: List<CategoryNode>)`;
  `CategoryService`: `create(cmd): Long`(parent 없음 `CATEGORY_NOT_FOUND`), `update(id, cmd)`, `delete(id)`(자식 있으면 `CATEGORY_HAS_CHILDREN`, 상품 있으면 `CATEGORY_HAS_PRODUCTS`); `CategoryQueryService`: `tree(): List<CategoryNode>`(displayOrder asc, id asc), **`leafIdsUnder(categoryId): List<Long>`**(소분류면 자기 자신, 대분류면 자식 전부, 없으면 `CATEGORY_NOT_FOUND`), **`requireLeaf(categoryId)`**(없으면 `CATEGORY_NOT_FOUND`, 대분류면 `CATEGORY_NOT_LEAF`).
- Produces(test): `CommerceTestSupport.leafCategory(): Long`.

- [ ] **Step 1: OpenAPI** — `GET /api/v1/categories`(공개, 200 `[CategoryNode]`), `POST /api/v1/admin/categories`(201 `{categoryId}`), `PUT /api/v1/admin/categories/{categoryId}`, `DELETE /api/v1/admin/categories/{categoryId}`(204; 409 `CATEGORY_HAS_CHILDREN`, `CATEGORY_HAS_PRODUCTS`). 에러 enum에 `CATEGORY_INVALID`, `CATEGORY_NOT_FOUND`, `CATEGORY_NOT_LEAF`, `CATEGORY_HAS_CHILDREN`, `CATEGORY_HAS_PRODUCTS`.
- [ ] **Step 2: ErrorCode** 다섯 개(400/404/400/409/409).
- [ ] **Step 3: 실패 테스트** — `CategoryTest`: `소분류_아래에_자식을_만들면_CATEGORY_INVALID`, `대분류는_isLeaf가_false다`. `CategoryServiceTest`(Fake): `트리는_displayOrder_순으로_자식을_품는다`, `자식이_있는_대분류_삭제는_CATEGORY_HAS_CHILDREN`, `상품이_있는_소분류_삭제는_CATEGORY_HAS_PRODUCTS`(Fake checker true), `leafIdsUnder는_대분류면_자식_ID_목록이다`, `requireLeaf는_대분류면_CATEGORY_NOT_LEAF`.
- [ ] **Step 4: 실패 확인**, **Step 5: 구현** — `V8__category.sql`: `category(id, parent_id BIGINT NULL, name VARCHAR(50), display_order INT, + BaseEntity)`.
- [ ] **Step 6: 단위 PASS**, **Step 7: API 테스트** — `CategoryApiTest`: `관리자가_만든_2단계_트리를_비로그인으로_조회한다`, `일반_회원의_카테고리_생성은_403`, `삭제된_카테고리는_트리에_없다`.
- [ ] **Step 8: 전체 PASS, 커밋** `feat: 카테고리 2단계 트리 관리자 CRUD와 공개 조회`

### Task 7: 상품 도메인(옵션·SKU 단종 상태·상태 전이·가격)

**Files:**
- Create: `catalog/domain/{Product,ProductId,ProductStatus,ProductContent,ProductOption,OptionValue,Sku,SkuId,SkuStatus,ProductRepository}.kt`
- Test: `catalog/domain/ProductTest.kt`

**Interfaces:**
- Produces: `enum ProductStatus { PENDING_REVIEW, ON_SALE, REJECTED, PAUSED, ENDED }`; `enum SkuStatus { ACTIVE, DISCONTINUED }`;
  `data class OptionValue(val id: Long?, val name: String)`; `data class ProductOption(val id: Long?, val name: String, val values: List<OptionValue>)`(값 이름 중복·빈 값 → `PRODUCT_INVALID`);
  `data class ProductContent(val name: String, val description: String, val categoryId: Long, val imageFileIds: List<Long>, val options: List<ProductOption>)` — `init`: name 1..100자, imageFileIds 1..10개·중복 없음(첫 번째가 대표), options 0..2개, 축 이름 중복 금지; 위반 `PRODUCT_INVALID`. `fun existingValueIds(): Set<Long>`.
  `class Sku(id: SkuId?, optionValueIds: List<Long> /* 정렬됨 */, extraPrice: Long, stock: Int, onSale: Boolean, skuStatus: SkuStatus)` — `val combinationKey get() = optionValueIds.sorted().joinToString("|")`, `fun update(extraPrice, stock, onSale)`(`extraPrice >= 0 && stock >= 0` 아니면 `SKU_INVALID`; DISCONTINUED면 `SKU_NOT_FOUND`), `fun discontinue()`, `fun revive()`(ACTIVE, stock 0), `val available get() = skuStatus == ACTIVE && onSale && stock > 0`.
  `class Product(id: ProductId?, sellerId: Long, brandId: Long, content: ProductContent, originalPrice: Long, salePrice: Long, status, rejectReason: String?, onSaleAt: LocalDateTime?, salesCount: Long, skus: List<Sku>)` —
  `companion fun register(sellerId, brandId, content, originalPrice, salePrice): Product`(PENDING_REVIEW, 모든 값 `id == null`이어야 함 아니면 `PRODUCT_INVALID`, `skus` 비어 있음 — 저장소가 값 ID 확정 후 `rebuildSkus`), `fun restore(...)`;
  `fun editContent(content)`(PENDING_REVIEW·REJECTED만 → PENDING_REVIEW, 사유 null), `fun approve(now)`(PENDING_REVIEW→ON_SALE; `onSaleAt`이 null일 때만 now 기록), `fun reject(reason)`, `fun pause()`(ON_SALE→PAUSED), `fun resume()`(PAUSED→ON_SALE), `fun end()`(ON_SALE·PAUSED→ENDED), `fun applyChange(content, originalPrice)`(ON_SALE·PAUSED만, 상태·onSaleAt 유지; `0 < salePrice <= originalPrice` 아니면 `PRODUCT_INVALID_PRICE`); 콘텐츠를 바꾸는 두 메서드는 `content.existingValueIds() ⊆ 현재 값 ID 집합` 아니면 `PRODUCT_INVALID`; 위반 전이는 `PRODUCT_INVALID_STATE_TRANSITION`;
  `fun changeSalePrice(salePrice)`(ENDED면 전이 오류; `0 < sale <= 현재 originalPrice` 아니면 `PRODUCT_INVALID_PRICE`; 정가는 이 메서드로 바꿀 수 없다), `fun updateSku(skuId, extraPrice, stock, onSale)`(ENDED면 전이 오류; 없으면 `SKU_NOT_FOUND`), `val discountRate: Int get() = ((originalPrice - salePrice) * 100 / originalPrice).toInt()`, `val activeSkus get() = skus.filter { it.skuStatus == ACTIVE }`, `val soldOut get() = activeSkus.none { it.available }`.
  **`fun rebuildSkus(valueIdsByCombination: List<List<Long>>)`**(저장소가 값 ID를 확정한 뒤 호출): 조합마다 `combinationKey`가 같은 기존 Sku가 ACTIVE면 그대로, DISCONTINUED면 `revive()`, 없으면 새 Sku(0, 0, true, ACTIVE); 새 조합 목록에 없는 ACTIVE Sku는 `discontinue()`. 축 0개면 조합은 `listOf(emptyList())` 하나.
  `interface ProductRepository { save(p: Product): Product; findById(ProductId): Product?; findByIdAndSellerId(ProductId, sellerId): Product?; findPageBySellerId(sellerId, status?, page, size): PageResult<Product>; findPageByStatus(status?, page, size): PageResult<Product>; existsByCategoryId(categoryId): Boolean }`.

- [ ] **Step 1: ErrorCode·OpenAPI enum** — `PRODUCT_INVALID(400)`, `PRODUCT_INVALID_PRICE(400)`, `PRODUCT_NOT_FOUND(404)`, `PRODUCT_INVALID_STATE_TRANSITION(409)`, `SKU_INVALID(400)`, `SKU_NOT_FOUND(404)`; `ErrorResponse.code.enum`에도 추가.
- [ ] **Step 2: 실패 테스트** — `ProductTest`(값 ID는 테스트가 1, 2, …로 직접 준다): `rebuildSkus는_조합마다_ACTIVE_SKU를_재고_0으로_만든다`(색상 2 × 사이즈 2 → 4개), `옵션이_없으면_SKU_하나`, `옵션_3개는_PRODUCT_INVALID`, `이미지_11장은_PRODUCT_INVALID`, `판매가가_정가보다_크면_PRODUCT_INVALID_PRICE`, `할인율은_내림이다`(10000/6666 → 33), `승인하면_ON_SALE이고_onSaleAt이_기록된다`, `일시중지_재개해도_onSaleAt은_바뀌지_않는다`(approve는 한 번만 호출되므로 restore로 ON_SALE 상태를 만들어 pause/resume 후 확인), `ON_SALE_일시중지_재개_종료`, `종료_후_재개는_INVALID_STATE_TRANSITION`, `반려된_상품_내용을_고치면_PENDING_REVIEW`, `ON_SALE_상품은_직접_수정할_수_없다`, `모든_ACTIVE_SKU_재고가_0이면_품절`, `판매_여부를_끈_SKU는_품절_계산에서_제외된다`, `재구성_시_같은_조합은_유지_새_조합은_0으로_생성_사라진_조합은_DISCONTINUED`, `DISCONTINUED_조합이_다시_생기면_같은_행이_ACTIVE로_되살아나고_재고는_0이다`, `옵션_값_이름만_바꾸면_SKU_ID_재고_추가금액이_유지된다`(값 ID 동일), `남의_값_ID로_콘텐츠를_바꾸면_PRODUCT_INVALID`, `DISCONTINUED_SKU_갱신은_SKU_NOT_FOUND`, `종료된_상품의_판매가_변경은_INVALID_STATE_TRANSITION`, `판매가_변경으로_정가를_바꿀_수_없고_정가_초과_판매가는_PRODUCT_INVALID_PRICE`, `변경_적용_시_새_정가가_현재_판매가보다_작으면_PRODUCT_INVALID_PRICE`.
- [ ] **Step 3: 실패 확인**, **Step 4: 구현**, **Step 5: PASS** — `./gradlew test --tests 'com.dogyun.kairos.catalog.domain.ProductTest'`, `ArchitectureTest`.
- [ ] **Step 6: 커밋** `feat: 상품 도메인(옵션 값 ID 기반 SKU 재구성, 단종 상태, 가격 규칙)`

### Task 8: 판매자 상품 API(등록·조회·직접 수정·가격·SKU·중지/재개/종료)

**Files:**
- Create: `V9__product.sql`, `catalog/application/{SellerReader,ImageFileReader,ProductService,ProductQueryService,ProductCommands,ProductResults}.kt`, `catalog/infrastructure/{ProductEntity,ProductImageEntity,ProductOptionEntity,ProductOptionValueEntity,SkuEntity,ProductJpaRepository,ProductImageJpaRepository,ProductOptionJpaRepository,ProductOptionValueJpaRepository,SkuJpaRepository,ProductRepositoryImpl,SellerReaderImpl,ImageFileReaderImpl,ProductCategoryUsageChecker}.kt`, `catalog/presentation/{SellerProductController,ProductRequests}.kt`
- Modify: `openapi.yaml`, `CommerceTestSupport.kt`(cleanUp: `sku, product_option_value, product_option, product_image, product`); 삭제: `NoProductCategoryUsageChecker`
- Test: `catalog/application/ProductServiceTest.kt`, `catalog/infrastructure/ProductRepositoryImplTest.kt`, `catalog/presentation/SellerProductApiTest.kt`

**Interfaces:**
- Consumes: Task 7 도메인, Task 6 `CategoryQueryService.requireLeaf`, Task 5 `BrandQueryService.findSnapshot/getNames`, Task 4 `FileQueryService.findImages`, `@AuthenticatedSeller SellerPrincipal`.
- Produces(application): `interface SellerReader { fun findBrand(brandId: Long): BrandSnapshot?; fun getBrandNames(brandIds: Collection<Long>): Map<Long, String> }` + `data class BrandSnapshot(brandId, sellerId, name, approved)`(catalog.application 소유); `interface ImageFileReader { fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> }` + `data class ImageFileRef(fileId, url, uploaderMemberId, isImage)`(catalog.application 소유; seller 것과 별개 타입).
  `OptionValueCommand(valueId: Long?, name)`, `OptionCommand(optionId: Long?, name, values)`, `ProductContentCommand(name, description, categoryId, imageFileIds: List<Long>, options: List<OptionCommand>)`, `RegisterProductCommand(brandId, content, originalPrice, salePrice)`, `ChangeSalePriceCommand(salePrice)`, `SubmitChangeRequestCommand(content: ProductContentCommand, originalPrice: Long)`(Task 10), `UpdateSkuCommand(extraPrice, stock, onSale)`.
  `ProductImageResult(fileId, url, isMain)`, `OptionResult(optionId, name, values: List<OptionValueResult(valueId, name)>)`, `SellerSkuResult(skuId, optionValueIds, displayName /* "블랙 / L" */, extraPrice, stock, onSale, skuStatus)`, `SellerProductDetail(productId, sellerId, brandId, brandName, categoryId, name, description, images: List<ProductImageResult>, originalPrice, salePrice, discountRate, status, rejectReason, onSaleAt, salesCount, soldOut, options, skus /* ACTIVE·DISCONTINUED 모두 */, pendingChangeRequestId: Long? /* Task 10에서 채움 */)`, `SellerProductSummary(productId, name, thumbnailUrl, brandName, salePrice, status, soldOut)`.
  `ProductService(productRepository, sellerReader, categoryQueryService, imageFileReader)`: `register(seller: SellerPrincipal, cmd): Long`(브랜드 없음·`brand.sellerId != seller.sellerId` → `BRAND_NOT_FOUND`, `!approved` → `BRAND_NOT_APPROVED`, `requireLeaf`, 이미지는 전부 존재·`isImage`·`uploaderMemberId == seller.memberId` 아니면 `FILE_NOT_OWNED`; `Product.register(seller.sellerId, …)`), `editContent(seller, productId, cmd)`, `changeSalePrice(seller, productId, cmd)`, `updateSku(seller, productId, skuId, cmd)`, `pause(seller, productId)`, `resume`, `end`. 상품은 항상 `findByIdAndSellerId(productId, seller.sellerId)`, 없으면 `PRODUCT_NOT_FOUND`.
  `ProductQueryService(productRepository, sellerReader, imageFileReader)`: `getMine(seller, productId): SellerProductDetail`, `listMine(seller, status?, page, size): PageResult<SellerProductSummary>`.
  `ProductCategoryUsageChecker : CategoryUsageChecker`(`productRepository.existsByCategoryId`).
- 컨트롤러 권한: `register`, `editContent`, `changeSalePrice`, `updateSku`, `resume`는 `@AuthenticatedSeller`(기본 requireManage), `pause`, `end`, `getMine`, `listMine`은 `@AuthenticatedSeller(requireManage = false)`.
- 저장소 구현 결정(`ProductRepositoryImpl.save`): 새 상품은 product → image → option → option_value 순으로 insert해 값 ID를 얻고, 조합(`values` 데카르트 곱)의 ID 목록으로 `product.rebuildSkus(...)`를 호출한 뒤 SKU insert. 기존 상품은 product 필드 갱신; 이미지는 기존 행 소프트 딜리트 후 재생성(`display_order`, `is_main` = 첫 번째); 옵션·값은 `id`가 있는 것은 이름 갱신, 없는 것은 insert, 콘텐츠에 없는 기존 것은 소프트 딜리트; 그다음 `rebuildSkus` 호출 → SKU는 `id`로 갱신(`sku_status` 포함), 새 것은 insert. 저장 후 `findById`로 다시 읽어 돌려준다. `sku.option_value_ids`는 정렬된 ID를 `|`로 이은 VARCHAR.
- Produces(test): `CommerceTestSupport.registerProduct(sellerToken, brandId, categoryId, fileIds, options = 색상[블랙,화이트] × 사이즈[M,L]): Long`, `sellerProductDetail(token, productId): JsonNode`.

- [ ] **Step 1: OpenAPI** — `POST /api/v1/seller/products`(201 `{productId}`), `GET /api/v1/seller/products?status&page&size`, `GET /api/v1/seller/products/{productId}`, `PUT /api/v1/seller/products/{productId}`(직접 수정; 409 `PRODUCT_INVALID_STATE_TRANSITION`), `PATCH /api/v1/seller/products/{productId}/price`(판매가만 `{salePrice}`; 정가는 변경 요청으로), `PATCH /api/v1/seller/products/{productId}/skus/{skuId}`, `POST .../pause`, `.../resume`, `.../end`. 스키마 `ProductContentRequest{name, description, categoryId, imageFileIds[1..10], options[0..2]{optionId?, name, values[]{valueId?, name}}}`, `RegisterProductRequest`, `ChangeSalePriceRequest`, `UpdateSkuRequest`, `SellerProductDetail`, `SellerProductSummary`, `ProductStatus`, `SkuStatus`. 에러 enum에 `BRAND_NOT_APPROVED`.
- [ ] **Step 2: ErrorCode** `BRAND_NOT_APPROVED(409, "승인되지 않은 브랜드입니다.")`.
- [ ] **Step 3: 실패 테스트** — `ProductServiceTest`(Fake: `FakeProductRepository`는 save 시 값 ID·SKU ID를 순번으로 채우고 `rebuildSkus`를 호출해 실제 저장소와 같은 계약을 흉내 낸다; `FakeSellerReader(brands)`, `FakeImageFileReader(files)`, `CategoryQueryService`는 Fake `CategoryRepository`로 실제 인스턴스): `등록하면_PENDING_REVIEW_상품이_sellerId와_함께_저장되고_SKU_4개가_생긴다`, `남의_브랜드로_등록하면_BRAND_NOT_FOUND`, `미승인_브랜드는_BRAND_NOT_APPROVED`, `대분류_카테고리는_CATEGORY_NOT_LEAF`, `다른_회원이_올린_이미지는_FILE_NOT_OWNED`, `다른_판매자의_상품은_PRODUCT_NOT_FOUND`, `판매가_변경은_즉시_반영된다`, `직접_수정_시_valueId를_유지한_값의_SKU는_그대로다`.
- [ ] **Step 4: 실패 확인**, **Step 5: 구현** — `V9__product.sql`: `product(id, seller_id, brand_id, category_id, name VARCHAR(100), description TEXT, original_price BIGINT, sale_price BIGINT, product_status VARCHAR(20), reject_reason VARCHAR(500) NULL, on_sale_at DATETIME(6) NULL, sales_count BIGINT NOT NULL DEFAULT 0, + BaseEntity)`, `product_image(id, product_id, file_id, display_order INT, is_main BIT, + BaseEntity)`, `product_option(id, product_id, name VARCHAR(50), sort_order INT, + BaseEntity)`, `product_option_value(id, option_id, name VARCHAR(50), sort_order INT, + BaseEntity)`, `sku(id, product_id, option_value_ids VARCHAR(100), extra_price BIGINT, stock INT, on_sale BIT, sku_status VARCHAR(20), + BaseEntity)`. 엔티티: `ProductImageEntity`, `ProductOptionEntity`, `SkuEntity`는 `@ManyToOne(fetch = LAZY) product`, `ProductOptionValueEntity`는 `@ManyToOne(LAZY) option`(같은 애그리거트). `SellerReaderImpl`, `ImageFileReaderImpl`(각각 seller·file의 application 서비스 호출). 컨트롤러 `/api/v1/seller/products`.
- [ ] **Step 6: 단위 PASS**, **Step 7: 저장소 통합 테스트** — `ProductRepositoryImplTest : IntegrationTestSupport`: `저장_후_읽으면_이미지_순서_옵션_값_ID_SKU가_그대로다`, `콘텐츠_변경_저장_시_사라진_조합은_DISCONTINUED로_남고_유지된_SKU_ID는_같고_되살아난_조합은_같은_행이다`(jdbcTemplate로 `sku_status`, 행 수 확인; `status='DELETED'` SKU는 0행).
- [ ] **Step 8: API 테스트** — `SellerProductApiTest`: `상품을_등록하면_201이고_내_상세에_SKU_4개가_재고_0_ACTIVE로_있다`, `SKU_재고를_바꾸면_즉시_상세에_반영된다`, `판매가_변경_후_할인율이_바뀐다`, `정가는_가격_변경_API로_바꿀_수_없다`, `다른_판매자의_상품_상세는_404`, `판매자가_아닌_회원의_상품_등록은_403`, `정가보다_큰_판매가는_400_PRODUCT_INVALID_PRICE`, `상품이_있는_소분류_삭제는_409_CATEGORY_HAS_PRODUCTS`, `상세_이미지_URL은_files_경로다`.
- [ ] **Step 9: 전체 PASS, 커밋** `feat: 판매자 상품 등록·수정·가격·SKU·판매 상태 API`

### Task 9: 관리자 상품 심사(목록·승인·반려·이력)

**Files:**
- Create: `catalog/domain/{ProductReviewHistory,ProductReviewHistoryRepository}.kt`, `catalog/application/ProductAdminService.kt`, `catalog/infrastructure/{ProductReviewHistoryEntity,ProductReviewHistoryJpaRepository,ProductReviewHistoryRepositoryImpl}.kt`, `catalog/presentation/AdminProductController.kt`, `V10__product_review_history.sql`
- Modify: `openapi.yaml`, `CommerceTestSupport.kt`(cleanUp `product_review_history`)
- Test: `catalog/application/ProductAdminServiceTest.kt`, `catalog/presentation/AdminProductApiTest.kt`

**Interfaces:**
- Produces: `enum ProductReviewTarget { PRODUCT, CHANGE_REQUEST }`, `enum ProductReviewResult { APPROVED, REJECTED }`, `class ProductReviewHistory(id, target, targetId, result, reason, reviewerId, reviewedAt)`, `interface ProductReviewHistoryRepository { save; findAllByTarget(target, targetId) }`.
  `ProductAdminService(productRepository, historyRepository, sellerReader, imageFileReader, clock)`: `approve(productId, reviewerId)`, `reject(productId, reason, reviewerId)`, `listForAdmin(status?, page, size): PageResult<AdminProductSummary(productId, sellerId, brandName, name, thumbnailUrl, originalPrice, salePrice, status)>`, `getForAdmin(productId): SellerProductDetail`.
- Produces(test): `CommerceTestSupport.approveProduct(productId)`, `onSaleProduct(sellerToken, brandId, categoryId, fileIds, originalPrice = 10000, salePrice = 8000, stock = 5): Long`(등록 → 승인 → 모든 SKU 재고 stock).

- [ ] **Step 1: OpenAPI** — `GET /api/v1/admin/products?status&page&size`, `GET /api/v1/admin/products/{productId}`, `POST /api/v1/admin/products/{productId}/approve`, `/reject {reason}`.
- [ ] **Step 2: 실패 테스트** — `ProductAdminServiceTest`(Fake): `승인하면_ON_SALE_onSaleAt_이력`, `반려하면_REJECTED_사유_이력`, `onSaleAt은_첫_승인_시각이고_이후_변경_요청_승인에서도_유지된다`(Task 10 `ChangeRequestAdminService`가 아닌 `Product.applyChange`로 직접 확인), `ON_SALE_상품_재승인은_INVALID_STATE_TRANSITION`, `없는_상품은_PRODUCT_NOT_FOUND`.
- [ ] **Step 3: 실패 확인**, **Step 4: 구현** — `V10__product_review_history.sql`에 `product_review_history(id, target_type VARCHAR(20), target_id BIGINT, result VARCHAR(20), reason VARCHAR(500) NULL, reviewer_id BIGINT, reviewed_at DATETIME(6), + BaseEntity)`. `AdminProductController`.
- [ ] **Step 5: 단위 PASS**, **Step 6: API 테스트** — `AdminProductApiTest`: `승인하면_판매자_상세가_ON_SALE이고_onSaleAt이_있다`, `반려_후_판매자가_고치면_다시_PENDING_REVIEW다`, `상태_필터_심사_목록`, `판매자_토큰으로_승인하면_403`.
- [ ] **Step 7: 전체 PASS, 커밋** `feat: 관리자 상품 심사 API와 심사 이력`

### Task 10: 상품 변경 요청(생성·철회·승인·반려, SKU 재구성)

**Files:**
- Create: `V11__product_change_request.sql`, `catalog/domain/{ProductChangeRequest,ChangeRequestStatus,ProductChangeRequestRepository}.kt`, `catalog/application/{ChangeRequestService,ChangeRequestAdminService}.kt`, `catalog/infrastructure/{ProductChangeRequestEntity,ProductChangeRequestJpaRepository,ProductChangeRequestRepositoryImpl,ProductContentJson}.kt`, `catalog/presentation/{SellerChangeRequestController,AdminChangeRequestController}.kt`
- Modify: `ErrorCode.kt`, `openapi.yaml`, `ProductResults.kt`, `ProductQueryService.kt`(`pendingChangeRequestId`), `CommerceTestSupport.kt`(cleanUp `product_change_request`)
- Test: `catalog/domain/ProductChangeRequestTest.kt`, `catalog/application/ChangeRequestServiceTest.kt`, `catalog/presentation/ChangeRequestApiTest.kt`

**Interfaces:**
- Produces(domain): `enum ChangeRequestStatus { PENDING, APPROVED, REJECTED, WITHDRAWN }`; `class ProductChangeRequest(id: Long?, productId: Long, sellerId: Long, content: ProductContent, originalPrice: Long, status, rejectReason: String?)` — `companion fun submit(productId, sellerId, content, originalPrice)`, `approve()`, `reject(reason)`, `withdraw()`(모두 PENDING에서만; 위반 `CHANGE_REQUEST_INVALID_STATE_TRANSITION`); `interface ProductChangeRequestRepository { save; findById(id): ProductChangeRequest?; findPendingByProductId(productId): ProductChangeRequest?; findPage(status?, page, size): PageResult<ProductChangeRequest> }`.
- Produces(application): `ChangeRequestDetail(changeRequestId, productId, sellerId, status, rejectReason, originalPrice, content: ProductContentResult(name, description, categoryId, images: List<ProductImageResult>, options))`;
  `ChangeRequestService(productRepository, changeRequestRepository, categoryQueryService, imageFileReader)`: `submit(seller: SellerPrincipal, productId, cmd: SubmitChangeRequestCommand): Long`(상품 ON_SALE·PAUSED 아니면 `PRODUCT_INVALID_STATE_TRANSITION`, 대기 중 있으면 `CHANGE_REQUEST_ALREADY_PENDING`, 카테고리·이미지·값 ID 검증은 Task 8과 동일), `withdraw(seller, productId, changeRequestId)`(남의 것 → `CHANGE_REQUEST_NOT_FOUND`).
  `ChangeRequestAdminService(productRepository, changeRequestRepository, historyRepository, imageFileReader, clock)`: `approve(changeRequestId, reviewerId)`(상품 `applyChange(content, originalPrice)` → 저장(저장소가 SKU 재구성), 요청 APPROVED, 이력 `CHANGE_REQUEST/APPROVED`; 상품이 ENDED면 `PRODUCT_INVALID_STATE_TRANSITION`이고 요청은 PENDING으로 남는다), `reject(changeRequestId, reason, reviewerId)`, `listForAdmin(status?, page, size)`, `get(changeRequestId): ChangeRequestDetail`.
- 비고: 승인은 상품과 변경 요청 두 애그리거트를 한 트랜잭션에서 바꾼다. 같은 모듈 안의 의도적 예외로 `ChangeRequestAdminService.approve`에 주석을 남긴다(ADR은 두지 않는다).

- [ ] **Step 1: OpenAPI** — `POST /api/v1/seller/products/{productId}/change-requests`(201 `{changeRequestId}`; 409 `CHANGE_REQUEST_ALREADY_PENDING`, `PRODUCT_INVALID_STATE_TRANSITION`), `DELETE /api/v1/seller/products/{productId}/change-requests/{changeRequestId}`(204), `GET /api/v1/admin/change-requests?status&page&size`, `GET /api/v1/admin/change-requests/{changeRequestId}`, `POST .../approve`, `.../reject {reason}`. 에러 enum에 `CHANGE_REQUEST_NOT_FOUND`, `CHANGE_REQUEST_ALREADY_PENDING`, `CHANGE_REQUEST_INVALID_STATE_TRANSITION`.
- [ ] **Step 2: ErrorCode** 세 개(404/409/409).
- [ ] **Step 3: 실패 테스트** — `ProductChangeRequestTest`: `승인된_요청은_철회할_수_없다`. `ChangeRequestServiceTest`(Fake): `ON_SALE_상품의_변경_요청은_PENDING으로_저장되고_상품은_그대로다`, `대기_중인_요청이_있으면_CHANGE_REQUEST_ALREADY_PENDING`, `PENDING_REVIEW_상품은_변경_요청을_낼_수_없다`, `철회하면_다시_낼_수_있다`, `승인하면_상품_내용이_바뀌고_같은_값_ID_조합의_SKU_재고가_유지된다`(블랙/M 재고 5 유지, 새 값 레드 조합 0, 사라진 화이트 조합 DISCONTINUED), `승인해도_onSaleAt은_바뀌지_않는다`, `정가를_바꾼_변경_요청은_승인_전까지_정가가_그대로고_승인하면_바뀐다`, `새_정가가_판매가보다_작으면_PRODUCT_INVALID_PRICE`, `반려하면_상품은_그대로다`, `종료된_상품의_변경_요청은_승인할_수_없다`.
- [ ] **Step 4: 실패 확인**, **Step 5: 구현** — `V11__product_change_request.sql`: 테이블 `product_change_request(id, product_id, seller_id, original_price BIGINT, content_json TEXT, change_status VARCHAR(20), reject_reason VARCHAR(500) NULL, + BaseEntity)`; `content_json`은 도메인 `ProductContent`를 직접 직렬화하지 않는다. infrastructure 전용 DTO `ProductContentJson`(imageFileIds, 옵션·값 ID 포함; 옵션·값용 중첩 DTO 포함)을 두고 `ProductChangeRequestRepositoryImpl`에서 `ProductContent` ↔ `ProductContentJson` 양방향 매핑 후 Jackson으로 JSON 변환한다(도메인 클래스가 직렬화 형식에 묶이지 않게 한다). `ProductQueryService.getMine`은 `findPendingByProductId`로 `pendingChangeRequestId`를 채운다.
- [ ] **Step 6: 단위 PASS**, **Step 7: API 테스트** — `ChangeRequestApiTest`: `변경_요청_중에도_판매자_상세는_기존_내용이고_pendingChangeRequestId가_있다`, `승인_후_판매자_상세_이름과_SKU가_바뀐다`(유지 SKU ID 동일, 사라진 SKU `DISCONTINUED`), `두_번째_요청은_409`, `다른_판매자의_요청_철회는_404`.
- [ ] **Step 8: 전체 PASS, 커밋** `feat: 상품 변경 요청과 승인 시 SKU 재구성`

### Task 11: 사용자 상품 목록·검색·상세(노출 조건, 필터, 정렬) + ADR 0002

**Files:**
- Create: `catalog/domain/{ProductSearch,ProductSearchRepository}.kt`, `catalog/application/{ProductSearchService,ProductSearchResults}.kt`, `catalog/infrastructure/{BrandView,SellerView,ProductSearchRepositoryImpl}.kt`, `catalog/presentation/ProductController.kt`, `docs/adr/0002-catalog-search-cross-module-read-join.md`
- Modify: `docs/adr/README.md`, `openapi.yaml`
- Test: `catalog/presentation/ProductSearchApiTest.kt`

**Interfaces:**
- Consumes: `CategoryQueryService.leafIdsUnder`, `SellerReader.getBrandNames`, `ImageFileReader.findImages`(URL), Task 9 `onSaleProduct`, Task 3 `suspendSeller/unsuspendSeller`.
- Produces(domain): `enum ProductSort { LATEST, PRICE_ASC, PRICE_DESC, POPULAR }`; `data class ProductSearchCondition(leafCategoryIds: List<Long>?, brandId: Long?, minPrice: Long?, maxPrice: Long?, keyword: String?, sort: ProductSort)`; `data class ProductListRow(productId, mainFileId: Long, brandName, name, originalPrice, salePrice, soldOut)`;
  `interface ProductSearchRepository { fun search(cond, page, size): PageResult<ProductListRow>; fun findExposedDetail(productId): Product? /* ON_SALE + 브랜드 APPROVED + 판매자 APPROVED 아니면 null */ }`.
- Produces(application): `ProductSummary(productId, thumbnailUrl, brandName, name, originalPrice, salePrice, discountRate, soldOut)`, `ProductDetail(productId, brandId, brandName, categoryId, name, description, imageUrls: List<String>, originalPrice, salePrice, discountRate, soldOut, options, skus: List<PublicSkuResult(skuId, optionValueIds, displayName, extraPrice, soldOut)> /* ACTIVE만, 재고 숫자 없음 */)`;
  `ProductSearchService(searchRepository, categoryQueryService, sellerReader, imageFileReader)`: `list(categoryId?, brandId?, minPrice?, maxPrice?, keyword?, sort, page, size): PageResult<ProductSummary>`(`page < 0 || size !in 1..100` → INVALID_REQUEST; `minPrice > maxPrice` → INVALID_REQUEST; keyword trim 후 빈 문자열이면 null), `detail(productId): ProductDetail`(없으면 PRODUCT_NOT_FOUND).
- 구현 결정(`ProductSearchRepositoryImpl`, QueryDSL): `@Entity @Immutable @Table(name = "brand") class BrandView(id, sellerId, name, brandStatus: String)`와 `@Table(name = "seller") class SellerView(id, sellerStatus: String)` — 둘 다 `@SQLRestriction("status = 'ACTIVE'")`, `BaseEntity` 비상속(status 컬럼은 매핑하지 않음), `catalog.infrastructure` 소유, 읽기 전용, 검색에 필요한 컬럼만. `from(product).join(brandView).on(brandView.id.eq(product.brandId)).join(sellerView).on(sellerView.id.eq(product.sellerId))` + `where(product.productStatus.eq(ON_SALE), brandView.brandStatus.eq("APPROVED"), sellerView.sellerStatus.eq("APPROVED"), 조건…)`(ACTIVE 조건은 뷰 엔티티의 `@SQLRestriction`이 건다). 키워드: `product.name like %kw% escape '\'` or `brandView.name like …`; `%`, `_`, `\`는 `\`로 이스케이프. 품절: `notExists(sku where sku.product.id = product.id and sku.skuStatus = ACTIVE and sku.onSale and sku.stock > 0 and sku.status = ACTIVE)`. 대표 이미지: `product_image` 중 `is_main = true` 서브쿼리로 `file_id`. 정렬: LATEST `onSaleAt desc, id desc`; PRICE_ASC `salePrice asc, id desc`; PRICE_DESC `salePrice desc, id desc`; POPULAR `salesCount desc, onSaleAt desc, id desc`. 총 건수는 같은 where로 `count`. 인덱스 추가 금지.

- [ ] **Step 1: OpenAPI** — `GET /api/v1/products?categoryId&brandId&minPrice&maxPrice&keyword&sort=LATEST|PRICE_ASC|PRICE_DESC|POPULAR&page&size`(200 `PageResponse<ProductSummary>`), `GET /api/v1/products/{productId}`(200 `ProductDetail`; 404). 설명에 노출 조건 세 가지, "재고 숫자는 노출하지 않는다", "SKU는 ACTIVE만".
- [ ] **Step 2: ADR 작성** — `docs/adr/0002-catalog-search-cross-module-read-join.md`(0001 형식: 상태 채택 / 날짜 2026-11-09 / 맥락 / 결정 / 결과). 결정: catalog.infrastructure가 `brand`·`seller` 테이블을 읽기 전용 뷰 엔티티로 매핑해 조인한다(코드 의존 없음, 테이블 의존만). 결과: 뷰 엔티티는 `@Immutable`이고 `BaseEntity`를 상속하지 않으며 `@SQLRestriction("status = 'ACTIVE'")`로 소프트 딜리트를 거른다("모든 엔티티는 BaseEntity 상속" 규칙의 명시적 예외, ArchUnit은 테이블 단위 의존만 보므로 영향 없음); 두 테이블 스키마 변경 시 catalog도 함께 바뀐다; MSA 분리 시 상품 테이블에 브랜드명·노출 여부를 비정규화하고 이벤트로 동기화하는 것으로 대체한다; 3-2에서 이 쿼리가 측정 대상이다. `docs/adr/README.md`에 항목 추가.
- [ ] **Step 3: 실패 테스트** — `ProductSearchApiTest : CommerceTestSupport`: `목록에는_ON_SALE이고_브랜드와_판매자가_승인된_상품만_나온다`, `정지된_판매자의_상품은_목록과_상세에서_사라지고_해제하면_다시_보인다`, `정지된_판매자의_상품은_상세도_404다`, `브랜드나_판매자_상태를_JdbcTemplate으로_바꾸면_상품이_목록과_상세에서_사라진다`(`brand.brand_status`를 REJECTED로, 원복 후 `seller.seller_status`를 SUSPENDED로 바꿔 각각 목록 0건과 상세 404 확인), `대분류로_필터하면_하위_소분류_상품이_모두_나온다`, `브랜드와_가격대_필터`(min 5000 max 9000), `검색어는_상품명과_브랜드명_부분_일치다`, `검색어의_퍼센트와_언더스코어는_문자로_취급된다`(`%` 검색 → 0건), `정렬_최신순_가격순_인기순`(POPULAR는 jdbcTemplate로 `sales_count` 갱신 후, 동률은 최신순), `최신순은_onSaleAt_기준이라_일시중지_해제해도_순서가_바뀌지_않는다`, `페이징_size_20_기본과_hasNext`(25개 → page 0 20건 hasNext true, page 1 5건), `size_101은_400`, `모든_ACTIVE_SKU_재고가_0이면_soldOut_true이고_판매_여부를_끈_SKU는_제외된다`, `상세에는_재고_숫자가_없고_DISCONTINUED_SKU는_빠진다`(`$.data.skus[0].stock` doesNotExist), `할인율은_내림이다`, `목록_썸네일은_대표_이미지_URL이다`.
- [ ] **Step 4: 실패 확인**, **Step 5: 구현** — `ProductController`(`/api/v1/products`, `@RequestParam(defaultValue = "LATEST") sort: ProductSort`).
- [ ] **Step 6: PASS** — `./gradlew test --tests '*ProductSearchApiTest'`, 이어서 전체 `./gradlew test`.
- [ ] **Step 7: 커밋** `feat: 사용자 상품 목록·검색·상세와 ADR 0002(검색 조회 전용 조인)`

### Task 12: 권한 교차 검증 테스트와 문서 갱신

**Files:**
- Create: `src/test/kotlin/com/dogyun/kairos/CommerceAuthorizationApiTest.kt`
- Modify: `README.md`, `CLAUDE.md`

**Interfaces:**
- Consumes: `CommerceTestSupport` 헬퍼 전부.

- [ ] **Step 1: 테스트 작성** — `CommerceAuthorizationApiTest`: `판매자_토큰으로_관리자_API_전부_403`(sellers/brands/products/change-requests/categories 대표 엔드포인트 하나씩), `판매자가_아닌_회원의_판매자_API는_403`(brands, products), `PENDING_판매자의_브랜드_신청은_409_SELLER_NOT_ACTIVE`, `정지된_판매자는_상품_등록_409_일시중지_200_종료_200`, `다른_판매자의_상품_판매가_변경과_SKU_변경은_404`, `다른_회원이_올린_파일을_상품_이미지로_쓰면_400_FILE_NOT_OWNED`, `공개_API는_비로그인으로_200`(categories, products, brands/{id}, files/{key}).
- [ ] **Step 2: 실행** — `./gradlew test --tests '*CommerceAuthorizationApiTest'` PASS(구현이 끝난 뒤이므로 바로 통과해야 한다. 실패하면 해당 Task 코드를 고치고 `fix:` 커밋).
- [ ] **Step 3: 문서** — `CLAUDE.md`: 첫 문단을 "마켓플레이스 커머스" 스토리로, "아키텍처" 모듈 목록을 `member`, `deal`, `order`, `payment`, `seller`, `catalog`, `file`, `common`으로, 도메인 상태 컬럼 예시에 `seller_status`, `product_status`, `sku_status` 추가, "현재 단계"를 "C1 판매자와 상품(커머스 로드맵)"으로, 문서 링크에 로드맵·C1 스펙·이 계획 추가, JPA 규칙에 "SKU는 소프트 딜리트 대신 `sku_status = DISCONTINUED`(스펙 13장)" 한 줄, 결정 기록에 ADR 0002 언급. `README.md`: 첫 문단을 마켓플레이스 스토리 한 문장으로, "진행 단계"에 `- [x] C1. 판매자와 상품` 및 C2~C6 미완료 항목, "문서"에 로드맵·C1 스펙·ADR 0002 링크, 업로드 로컬 경로(`uploads/`, `.env`의 `KAIROS_FILE_DIR`) 한 줄.
- [ ] **Step 4: 전체 테스트** — `./gradlew test` PASS. 기존 `DealApiTest`, `OrderApiTest`, `PaymentServiceTest` 등이 그대로 통과하는지 결과에서 확인한다.
- [ ] **Step 5: 커밋** `test: 커머스 권한 교차 검증과 모듈 문서 갱신`

---

## 스펙과의 차이 (실행 전 확인)

- 스펙 6장 "조회 전용으로 조인하는 것을 허용"은 ArchUnit 모듈 격리 규칙 때문에 상대 모듈 엔티티를 import할 수 없다. 그래서 `catalog.infrastructure`에 읽기 전용 뷰 엔티티(`BrandView`, `SellerView`)를 두는 방식으로 구현한다(ADR 0002). 규칙 완화는 하지 않는다.
- 스펙 12장 "상품은 반려된 행을 고쳐 다시 PENDING_REVIEW"를 `PENDING_REVIEW` 상태에도 똑같이 적용한다(검수 전이므로 직접 수정 허용).
- 스펙 13장 `@AuthenticatedSeller`는 모듈 격리를 지키기 위해 애너테이션·해석기를 `common/security`에, 판매자 조회 구현을 `seller.infrastructure`에 둔다.
- 스펙 14장 업로드 API 경로는 `POST /api/v1/files/images`(7장의 `/api/v1/seller/...` 이미지 업로드를 대체). 인증 회원이면 누구나 올릴 수 있고, "판매자 본인" 검증은 연결하는 쪽이 업로더 회원 ID로 한다.
- 정가(original price) 변경은 스펙의 즉시 반영 범위가 아니라 변경 요청(재검수)으로 처리한다. 즉시 반영은 판매가·SKU 재고·추가 금액·판매 여부뿐이다(심사 없이 표시 할인율을 부풀리는 것을 막기 위해).
- 승인된 브랜드는 C1에서 로고·설명을 바꿀 수 없다(REJECTED 상태에서 재신청으로만 수정).
- 스펙 문서 자체는 고치지 않았다.
