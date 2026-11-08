# Kairos Phase 0~1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OpenAPI 스펙을 먼저 작성하고, 그 스펙대로 동작하는 타임딜 모듈러 모놀리식(회원/인증, 딜/재고, 주문, Mock 결제)을 구현한다.

**Architecture:** 바운디드 컨텍스트별 모듈(`member`, `deal`, `order`, `payment`, `common`), 모듈 안은 `domain ← application ← adapter` 클린 아키텍처. 도메인 모델과 JPA 엔티티를 분리하고, 모듈 간 호출은 상대 모듈의 `port/in`을 `adapter/out/module`에서만 호출한다. 재고 차감은 의도적으로 락 없이 구현하고, 결제는 주문 트랜잭션 안에서 Mock PG를 동기 호출한다.

**Tech Stack:** Kotlin 2.3.21, Spring Boot 4.1.2, JDK 21, Gradle Kotlin DSL, MySQL 8.4, Redis 7, Flyway, Spring Security + jjwt, springdoc-openapi, JUnit5, Testcontainers, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-11-08-kairos-design.md` (실행자는 반드시 함께 읽는다. 특히 6장, 8장 Phase 0~1, 9장)

## Global Constraints

- 루트 패키지: `com.dogyun.kairos`. 모듈 패키지: `com.dogyun.kairos.{member|deal|order|payment|common}`.
- 레이어: `domain/`, `application/port/in/`, `application/port/out/`, `application/service/`, `adapter/in/web/`, `adapter/out/persistence/`, `adapter/out/external/`, `adapter/out/module/`.
- `domain` 패키지는 `org.springframework..`, `jakarta.persistence..`, `tools.jackson..`, `com.fasterxml..` 를 import하지 않는다.
- 다른 모듈의 `domain`/`adapter`를 import하지 않는다. 다른 모듈은 `port/in`만 호출하며, 그 호출은 `adapter/out/module`에만 있다.
- 애그리거트 간 ID 참조만. 연관관계는 같은 애그리거트 안에서만, `LAZY`, 기본 `@ManyToOne` 단방향. `@ManyToMany`, `@OneToOne` 금지.
- 모든 JPA 엔티티는 `BaseEntity`(`created_at`, `created_by`, `modified_at`, `modified_by`, `status`) 상속. `status`는 `ACTIVE`/`DELETED`. 소프트 딜리트만, 물리 `DELETE` 금지. 엔티티에 `@SQLRestriction("status = 'ACTIVE'")`.
- 도메인 상태 컬럼명은 `deal_status`, `order_status`, `payment_status`.
- 감사 `*_by`: 인증 회원 ID, 없으면 `0`.
- 스키마는 Flyway로만. `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`.
- **Phase 1 재고 차감은 락 없이** "조회 → 도메인 검증 → 저장". `@Version`, `PESSIMISTIC_WRITE`, 조건부 UPDATE 사용 금지(Phase 3 비교 대상).
- 결제는 주문 생성 트랜잭션 안에서 동기 호출. 실패/타임아웃이면 같은 트랜잭션 안에서 주문 `CANCELLED` + 재고 복구.
- Lombok 사용 안 함. Mock 라이브러리 추가 안 함(애플리케이션 서비스 테스트는 port의 인메모리 Fake로).
- 커밋 메시지: 한국어 Conventional Commits, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 의존성을 추가하는 커밋에는 추가 이유를 본문에 쓴다.
- 테스트 실행: `./gradlew test` (Docker 필요). JDK 21은 `/Users/huuimcompany/Library/Java/JavaVirtualMachines/temurin-21.0.10`에 설치되어 있다.

## Review Focus

1. **같은 사용자가 같은 딜에 연속 주문**: 이전 주문 수량 + 이번 수량이 1인당 제한을 넘으면 거절(409), 취소된 주문 수량은 합산에서 제외. → Task 8 테스트 `제한_초과_주문은_거절된다`, `취소된_주문은_제한에서_제외된다`.
2. **오픈 전/종료 후/취소된 딜 주문**: 진행 중이 아니면 거절(409) 및 재고 불변. → Task 8 `진행중이_아닌_딜은_주문할_수_없다`.
3. **결제 실패/타임아웃 후 재고**: 주문은 `CANCELLED`로 커밋되고 재고는 원래대로. 예외로 롤백되어 주문 기록이 사라지면 안 된다. → Task 8 `결제_실패시_주문은_취소로_남고_재고는_복구된다`.
4. **이미 취소된 주문 재취소**: 재고가 두 번 복구되지 않는다(409). → Task 9 `이미_취소된_주문은_다시_취소할_수_없다`.
5. **소프트 딜리트된 딜**: 목록/상세/주문 어디에서도 보이지 않는다(404). → Task 6 `삭제된_딜은_조회되지_않는다`.

---

## 파일 구조 (요약)

```
compose.yaml
docs/api/openapi.yaml
src/main/resources/application.yaml
src/main/resources/db/migration/V1__member.sql, V2__deal.sql, V3__order.sql, V4__payment.sql
src/main/kotlin/com/dogyun/kairos/
  common/  domain/(DomainException.kt, ErrorCode.kt)
           adapter/in/web/(ErrorResponse.kt, GlobalExceptionHandler.kt)
           adapter/out/persistence/(BaseEntity.kt, EntityStatus.kt, JpaAuditingConfig.kt)
           security/(SecurityConfig.kt, JwtProvider.kt, JwtAuthenticationFilter.kt, LoginMember.kt)
  member/  domain/(Member.kt, MemberId.kt, Email.kt, Role.kt, MemberRepository.kt, PasswordHasher.kt)
           application/port/in/(SignUpUseCase.kt, LoginUseCase.kt, ReissueTokenUseCase.kt, LogoutUseCase.kt)
           application/port/out/(RefreshTokenStore.kt)
           application/service/(SignUpService.kt, AuthService.kt, AdminInitializer.kt)
           adapter/in/web/(AuthController.kt)
           adapter/out/persistence/(MemberEntity.kt, MemberJpaRepository.kt, MemberPersistenceAdapter.kt)
           adapter/out/external/(BCryptPasswordHasher.kt, RedisRefreshTokenStore.kt)
  deal/    domain/(Deal.kt, DealId.kt, DealStatus.kt, Stock.kt, Money.kt*, DealRepository.kt, StockRepository.kt)
           application/port/in/(ManageDealUseCase.kt, QueryDealUseCase.kt, StockUseCase.kt, DealSnapshot.kt)
           application/service/(DealService.kt, StockService.kt)
           adapter/in/web/(AdminDealController.kt, DealController.kt)
           adapter/out/persistence/(DealEntity.kt, StockEntity.kt, ...JpaRepository.kt, DealPersistenceAdapter.kt)
  payment/ domain/(Payment.kt, PaymentStatus.kt, PaymentRepository.kt)
           application/port/in/(PayUseCase.kt) port/out/(PgClient.kt)
           application/service/(PaymentService.kt)
           adapter/out/external/(MockPgClient.kt, MockPgProperties.kt)
           adapter/out/persistence/(PaymentEntity.kt, ...)
  order/   domain/(Order.kt, OrderId.kt, OrderStatus.kt, OrderRepository.kt)
           application/port/in/(PlaceOrderUseCase.kt, CancelOrderUseCase.kt, QueryMyOrdersUseCase.kt)
           application/port/out/(DealPort.kt, StockPort.kt, PaymentPort.kt)
           application/service/(OrderService.kt)
           adapter/in/web/(OrderController.kt)
           adapter/out/module/(DealModuleAdapter.kt, StockModuleAdapter.kt, PaymentModuleAdapter.kt)
           adapter/out/persistence/(OrderEntity.kt, ...)
src/test/kotlin/com/dogyun/kairos/
  ArchitectureTest.kt, OpenApiContractTest.kt, IntegrationTestSupport.kt, <모듈별 테스트>
```
`*` `Money`는 `common/domain/Money.kt`에 둔다(공통 값 객체).

---

### Task 1: 로컬 인프라, 공통 설정, 아키텍처 테스트

**Files:**
- Create: `compose.yaml`, `src/test/kotlin/com/dogyun/kairos/ArchitectureTest.kt`, `src/test/kotlin/com/dogyun/kairos/IntegrationTestSupport.kt`
- Modify: `src/main/resources/application.yaml`, `build.gradle.kts`, `src/test/kotlin/com/dogyun/kairos/TestcontainersConfiguration.kt`

**Interfaces:**
- Produces: `abstract class IntegrationTestSupport` (`@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration::class)`, `protected lateinit var mockMvc: MockMvc`), 이후 모든 통합 테스트가 상속.

- [ ] **Step 1:** `compose.yaml` 작성: `mysql:8.4`(DB `kairos`, user/pw `kairos`/`kairos`, 3306), `redis:7.4`(6379). Redis는 Task 4에서 쓰지만 여기서 같이 띄운다.
- [ ] **Step 2:** `application.yaml`: datasource(위 값), `jpa.hibernate.ddl-auto: validate`, `jpa.open-in-view: false`, `flyway.enabled: true`, `management.endpoints.web.exposure.include: health,info,metrics`.
- [ ] **Step 3:** `TestcontainersConfiguration`의 이미지를 `mysql:8.4`로 고정(`latest` 금지).
- [ ] **Step 4:** `build.gradle.kts`에 `testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")` 추가.
- [ ] **Step 5: 실패하는 테스트 작성** — `ArchitectureTest`(ArchUnit `@AnalyzeClasses(packages = ["com.dogyun.kairos"])`):
  - `domain_은_프레임워크에_의존하지_않는다`: `..domain..` → `org.springframework..`, `jakarta.persistence..`, `tools.jackson..`, `com.fasterxml..` 의존 금지
  - `application_은_adapter에_의존하지_않는다`
  - `모듈은_다른_모듈의_domain_adapter에_의존하지_않는다`: 모듈 쌍(member, deal, order, payment)마다 `..{a}..` → `..{b}.domain..`, `..{b}.adapter..` 금지. 단 `common`은 예외.
  - `다른_모듈_port_in_호출은_adapter_out_module에서만`: `..{b}.application.port.in..`에 의존하는 `{a}` 클래스는 `..{a}.adapter.out.module..`에 있어야 한다.
  - 클래스가 아직 없어 규칙이 비면 실패하므로 각 규칙에 `.allowEmptyShould(true)`.
- [ ] **Step 6:** `./gradlew test` → `ArchitectureTest`, `contextLoads` PASS.
- [ ] **Step 7: 커밋** `chore: 로컬 인프라(compose)와 아키텍처 규칙 테스트 추가` (ArchUnit 추가 이유: 9장 의존성 규칙 강제).

### Task 2: OpenAPI 스펙 (SDD 기준 문서)

**Files:**
- Create: `docs/api/openapi.yaml`

이 스펙이 이후 모든 API 작업의 기준이다. 구현이 스펙과 다르면 스펙을 먼저 고치고 커밋한다.

- [ ] **Step 1:** OpenAPI 3.1로 아래 엔드포인트를 정의한다. 인증은 `bearerAuth`(JWT). 모든 에러는 `ErrorResponse { code: string, message: string }`.

| Method | Path | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/api/v1/auth/signup` | 공개 | `{email, password, name}` | 201 `{memberId}`; 409 `MEMBER_EMAIL_DUPLICATED`; 400 |
| POST | `/api/v1/auth/login` | 공개 | `{email, password}` | 200 `{accessToken, refreshToken}`; 401 `AUTH_INVALID_CREDENTIALS` |
| POST | `/api/v1/auth/reissue` | 공개 | `{refreshToken}` | 200 `{accessToken, refreshToken}`; 401 `AUTH_INVALID_TOKEN` |
| POST | `/api/v1/auth/logout` | USER | - | 204 |
| POST | `/api/v1/admin/deals` | ADMIN | `{productName, originalPrice, dealPrice, totalQuantity, perMemberLimit, openAt, closeAt}` | 201 `{dealId}`; 400 `DEAL_INVALID` |
| PUT | `/api/v1/admin/deals/{dealId}` | ADMIN | 위와 같음 | 200; 409 `DEAL_ALREADY_OPENED`; 404 `DEAL_NOT_FOUND` |
| DELETE | `/api/v1/admin/deals/{dealId}` | ADMIN | - | 204 (소프트 딜리트); 409 `DEAL_ALREADY_OPENED` |
| GET | `/api/v1/deals?status=SCHEDULED\|ONGOING\|CLOSED` | 공개 | - | 200 `[DealSummary]` |
| GET | `/api/v1/deals/{dealId}` | 공개 | - | 200 `DealDetail{..., remainingQuantity, status}`; 404 |
| POST | `/api/v1/orders` | USER | `{dealId, quantity}` | 201 `{orderId, status: PAID\|CANCELLED}`; 409 `DEAL_NOT_ONGOING`, `ORDER_LIMIT_EXCEEDED`, `STOCK_SOLD_OUT` |
| POST | `/api/v1/orders/{orderId}/cancel` | USER(본인) | - | 200; 409 `ORDER_ALREADY_CANCELLED`; 404 `ORDER_NOT_FOUND` |
| GET | `/api/v1/orders/me` | USER | - | 200 `[OrderSummary]` |

  - 금액은 원 단위 정수(`int64`), 시각은 ISO-8601 `date-time`(KST 오프셋 포함).
  - 결제 실패는 에러가 아니다: 201 + `status: CANCELLED` + `cancelReason: PAYMENT_FAILED|PAYMENT_TIMEOUT`.
  - 딜 상태 `status`는 저장값이 아니라 조회 시각 기준 계산값(`SCHEDULED`/`ONGOING`/`CLOSED`). 관리자 취소(삭제)는 소프트 딜리트로 목록에서 사라진다.
- [ ] **Step 2:** `npx @redocly/cli lint docs/api/openapi.yaml` → 에러 0.
- [ ] **Step 3: 커밋** `docs: 타임딜 API OpenAPI 스펙 작성`.

### Task 3: 공통 모듈 — BaseEntity, 감사, 예외 처리

**Files:**
- Create: `common/domain/DomainException.kt`, `common/domain/ErrorCode.kt`, `common/domain/Money.kt`, `common/adapter/in/web/ErrorResponse.kt`, `common/adapter/in/web/GlobalExceptionHandler.kt`, `common/adapter/out/persistence/BaseEntity.kt`, `common/adapter/out/persistence/EntityStatus.kt`, `common/adapter/out/persistence/JpaAuditingConfig.kt`
- Test: `src/test/kotlin/com/dogyun/kairos/common/MoneyTest.kt`

**Interfaces:**
- Produces:
  - `enum class ErrorCode(val httpStatus: Int, val message: String)` — Task 2 표의 모든 코드 + `INVALID_REQUEST(400)`, `UNAUTHORIZED(401)`, `FORBIDDEN(403)`.
  - `open class DomainException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)`
  - `@JvmInline value class Money(val amount: Long)` — 음수면 `IllegalArgumentException`, `operator fun times(qty: Int): Money`.
  - `@MappedSuperclass abstract class BaseEntity` — `createdAt: LocalDateTime`, `createdBy: Long`, `modifiedAt: LocalDateTime`, `modifiedBy: Long`(`@CreatedDate`/`@CreatedBy`/`@LastModifiedDate`/`@LastModifiedBy`), `@Enumerated(STRING) status: EntityStatus = ACTIVE`, `fun softDelete()`.
  - `enum class EntityStatus { ACTIVE, DELETED }`
  - `AuditorAware<Long>`: `SecurityContext`의 `LoginMember.id`, 없으면 `0L`.
  - 모든 테이블 공통 컬럼 DDL: `created_at DATETIME(6) NOT NULL, created_by BIGINT NOT NULL, modified_at DATETIME(6) NOT NULL, modified_by BIGINT NOT NULL, status VARCHAR(20) NOT NULL`.

- [ ] **Step 1:** `MoneyTest`: `음수_금액은_생성할_수_없다`, `금액에_수량을_곱한다`(`Money(1000) * 3 == Money(3000)`).
- [ ] **Step 2:** 실행 → FAIL(컴파일 에러).
- [ ] **Step 3:** 위 파일들 구현. `GlobalExceptionHandler`: `DomainException` → `errorCode.httpStatus` + `ErrorResponse(code = errorCode.name, message)`, `MethodArgumentNotValidException` → 400 `INVALID_REQUEST`.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5: 커밋** `feat: 공통 BaseEntity(감사·소프트 딜리트)와 예외 응답 처리`.

### Task 4: 회원가입과 JWT 인증

**Files:**
- Modify: `build.gradle.kts` (`spring-boot-starter-data-redis`, `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl`/`jjwt-jackson` runtimeOnly, `testcontainers` Redis는 `GenericContainer("redis:7.4")` + `@ServiceConnection(name = "redis")`)
- Create: `member/**` (파일 구조 참고), `common/security/**`, `db/migration/V1__member.sql`
- Test: `member/domain/MemberTest.kt`, `member/application/AuthServiceTest.kt`(Fake 사용), `member/adapter/in/web/AuthApiTest.kt`(IntegrationTestSupport)

**Interfaces:**
- Produces:
  - `data class LoginMember(val id: Long, val role: Role)` — `@AuthenticationPrincipal`로 주입.
  - `enum class Role { USER, ADMIN }`
  - `@JvmInline value class MemberId(val value: Long)`, `@JvmInline value class Email(val value: String)`(형식 검증)
  - `SecurityConfig`: `/api/v1/auth/signup|login|reissue`, `GET /api/v1/deals/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` 공개; `/api/v1/admin/**` ADMIN; 나머지 인증. 401/403은 `ErrorResponse` JSON.
  - JWT 설정: `kairos.jwt.secret`(256bit 이상), `access-ttl: 30m`, `refresh-ttl: 14d`. Refresh는 Redis 키 `refresh:{memberId}`에 저장, 재발급 시 회전(새 토큰으로 교체, 옛 토큰은 거절). 로그아웃은 키 삭제.
  - `AdminInitializer`: 시작 시 `kairos.admin.email`/`kairos.admin.password` 계정이 없으면 ADMIN으로 생성.
  - 테이블 `member(id BIGINT AUTO_INCREMENT PK, email VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, name VARCHAR(50) NOT NULL, role VARCHAR(20) NOT NULL, + 공통 컬럼, UNIQUE(email))`.

- [ ] **Step 1: 실패하는 테스트**
  - `MemberTest`: `잘못된_이메일_형식은_거절된다`.
  - `AuthApiTest`: `회원가입_후_로그인하면_토큰을_받는다`, `중복_이메일은_409`, `틀린_비밀번호는_401`, `재발급하면_이전_리프레시_토큰은_무효다`, `로그아웃_후_재발급은_401`, `일반_회원은_관리자_API에_403`, `회원가입시_created_by는_0이다`(DB 조회).
- [ ] **Step 2:** 실행 → FAIL.
- [ ] **Step 3:** 구현. 비밀번호는 `PasswordHasher` 포트(domain) + BCrypt 어댑터.
- [ ] **Step 4:** `./gradlew test` → PASS (ArchitectureTest 포함).
- [ ] **Step 5: 커밋** `feat: 회원가입과 JWT 로그인·재발급·로그아웃` (Redis·jjwt 추가 이유 기재).

### Task 5: 딜·재고 도메인

**Files:**
- Create: `deal/domain/Deal.kt`, `DealId.kt`, `DealStatus.kt`, `Stock.kt`, `DealRepository.kt`, `StockRepository.kt`
- Test: `deal/domain/DealTest.kt`, `deal/domain/StockTest.kt`

**Interfaces:**
- Produces:
  - `class Deal(id: DealId?, productName: String, originalPrice: Money, dealPrice: Money, totalQuantity: Int, perMemberLimit: Int, openAt: LocalDateTime, closeAt: LocalDateTime)`; `fun statusAt(now: LocalDateTime): DealStatus`; `fun update(...same, now)`; `fun ensureDeletable(now)`.
  - `enum class DealStatus { SCHEDULED, ONGOING, CLOSED }` (`openAt <= now < closeAt`이면 ONGOING).
  - `class Stock(dealId: DealId, total: Int, remaining: Int)`; `fun decrease(qty: Int)`; `fun restore(qty: Int)`.
  - `interface DealRepository { save(Deal): Deal; findById(DealId): Deal?; findAll(): List<Deal>; delete(DealId) }`, `interface StockRepository { save(Stock): Stock; findByDealId(DealId): Stock? }`
- [ ] **Step 1: 테스트**
  - `DealTest`: `할인가가_정가보다_크면_DEAL_INVALID`, `수량이나_1인제한이_1미만이면_DEAL_INVALID`, `종료가_오픈보다_빠르면_DEAL_INVALID`, `시각에_따라_상태가_계산된다`, `오픈_후_수정은_DEAL_ALREADY_OPENED`, `오픈_후_삭제는_DEAL_ALREADY_OPENED`.
  - `StockTest`: `재고보다_많이_차감하면_STOCK_SOLD_OUT`, `복구는_총수량을_넘을_수_없다`.
- [ ] **Step 2:** FAIL 확인 → **Step 3:** 구현 → **Step 4:** PASS 확인.
- [ ] **Step 5: 커밋** `feat: 딜·재고 도메인 모델`.

### Task 6: 딜 영속성·유스케이스·API

**Files:**
- Create: `db/migration/V2__deal.sql`, `deal/application/**`, `deal/adapter/**`
- Test: `deal/adapter/in/web/DealApiTest.kt`

**Interfaces:**
- Produces (다른 모듈이 쓰는 `port/in`):
  - `data class DealSnapshot(val dealId: Long, val dealPrice: Long, val perMemberLimit: Int, val status: DealStatus)`
  - `interface QueryDealUseCase { fun getSnapshot(dealId: Long): DealSnapshot  /* 없으면 DEAL_NOT_FOUND */; fun list(status: DealStatus?): List<DealSummary>; fun detail(dealId: Long): DealDetail }`
  - `interface StockUseCase { fun decrease(dealId: Long, quantity: Int); fun restore(dealId: Long, quantity: Int) }` — 구현은 `findByDealId` → `stock.decrease` → `save`. **락 없음.**
  - `interface ManageDealUseCase { create(CreateDealCommand): Long; update(dealId, UpdateDealCommand); delete(dealId) }` — `create`는 Deal과 Stock(remaining=total)을 같은 트랜잭션에서 저장.
  - 현재 시각은 `java.time.Clock` 빈을 주입받아 쓴다(테스트에서 고정).
  - 테이블: `deal(id, product_name, original_price BIGINT, deal_price BIGINT, total_quantity INT, per_member_limit INT, open_at DATETIME(6), close_at DATETIME(6), + 공통)`, `stock(id, deal_id BIGINT NOT NULL, total_quantity INT, remaining_quantity INT, + 공통, UNIQUE(deal_id))`. **Phase 1에서는 인덱스를 PK/UNIQUE 외에 추가하지 않는다**(Phase 3 비교 대상).
- [ ] **Step 1: 테스트** `DealApiTest`: `관리자가_딜을_등록하면_상세에서_남은수량이_총수량이다`, `상태별_목록_조회`, `삭제된_딜은_조회되지_않는다`(목록·상세 404·DB에 row는 `status=DELETED`로 남음), `오픈_후_수정은_409`.
- [ ] **Step 2~4:** FAIL → 구현 → PASS.
- [ ] **Step 5: 커밋** `feat: 딜 등록·수정·삭제·조회 API`.

### Task 7: Mock 결제 모듈

**Files:**
- Create: `db/migration/V4__payment.sql`, `payment/**`
- Test: `payment/application/PaymentServiceTest.kt`, `payment/adapter/out/external/MockPgClientTest.kt`

**Interfaces:**
- Produces:
  - `interface PayUseCase { fun pay(orderId: Long, amount: Long): PayResult; fun refund(orderId: Long) }`
  - `sealed interface PayResult { data class Approved(val paymentId: Long); data class Failed(val reason: FailReason) }`, `enum class FailReason { PAYMENT_FAILED, PAYMENT_TIMEOUT }`
  - `interface PgClient { fun approve(orderId: Long, amount: Long): PgResponse }` (port/out)
  - `MockPgProperties`(`kairos.mock-pg`): `delay: Duration = 100ms`, `failure-rate: Double = 0.0`, `timeout: Duration = 3s`. `MockPgClient`는 `delay`만큼 대기 후 `failure-rate` 확률로 실패. `delay > timeout`이면 `timeout`만큼 대기 후 TIMEOUT. **대기 동안 호출 스레드를 블로킹한다**(Phase 2 측정 의도).
  - `Payment` 상태 `APPROVED`, `FAILED`, `REFUNDED`; 결과와 무관하게 결제 시도 기록을 저장. 테이블 `payment(id, order_id BIGINT, amount BIGINT, payment_status VARCHAR(20), fail_reason VARCHAR(30) NULL, + 공통)`.
- [ ] **Step 1: 테스트** `MockPgClientTest`: `실패율_1이면_항상_실패`, `지연이_타임아웃보다_길면_TIMEOUT이고_타임아웃만큼만_기다린다`. `PaymentServiceTest`(Fake PgClient/Repository): `승인되면_APPROVED로_저장`, `실패해도_FAILED로_기록된다`, `환불하면_REFUNDED`.
- [ ] **Step 2~4:** FAIL → 구현 → PASS.
- [ ] **Step 5: 커밋** `feat: 지연·실패율 설정 가능한 Mock 결제`.

### Task 8: 주문 생성 (재고 차감 + 동기 결제)

**Files:**
- Create: `db/migration/V3__order.sql`, `order/domain/**`, `order/application/**`, `order/adapter/**`
- Test: `order/domain/OrderTest.kt`, `order/application/OrderServiceTest.kt`(Fake 포트), `order/adapter/in/web/OrderApiTest.kt`

**Interfaces:**
- Consumes: `QueryDealUseCase.getSnapshot`, `StockUseCase.decrease/restore` (Task 6), `PayUseCase.pay` (Task 7) — 각각 `order/application/port/out`의 `DealPort`, `StockPort`, `PaymentPort`로 감싸 `adapter/out/module`에서 호출.
- Produces:
  - `class Order(id, memberId: Long, dealId: Long, quantity: Int, amount: Money, status: OrderStatus, cancelReason: CancelReason?)`; `fun markPaid()`, `fun cancel(reason: CancelReason)` — `CANCELLED`에서 다시 cancel이면 `ORDER_ALREADY_CANCELLED`.
  - `enum class OrderStatus { CREATED, PAID, CANCELLED }`, `enum class CancelReason { PAYMENT_FAILED, PAYMENT_TIMEOUT, USER_REQUESTED }`
  - `interface OrderRepository { save(Order): Order; findById(Long): Order?; sumActiveQuantity(memberId: Long, dealId: Long): Int /* CANCELLED 제외 */; findByMemberId(memberId: Long): List<Order> }`
  - `interface PlaceOrderUseCase { fun place(command: PlaceOrderCommand): PlaceOrderResult }`, `data class PlaceOrderCommand(memberId: Long, dealId: Long, quantity: Int)`, `data class PlaceOrderResult(orderId: Long, status: OrderStatus, cancelReason: CancelReason?)`
  - 테이블 `orders(id, member_id BIGINT, deal_id BIGINT, quantity INT, amount BIGINT, order_status VARCHAR(20), cancel_reason VARCHAR(30) NULL, + 공통)`. 추가 인덱스 없음.
- `OrderService.place` 순서(한 `@Transactional`, 9.4 예외 지점이므로 클래스에 `// ADR 예정: Phase 4에서 Saga로 대체` 주석):
  1. `quantity >= 1` 아니면 `INVALID_REQUEST`
  2. `dealPort.getSnapshot` → `ONGOING` 아니면 `DEAL_NOT_ONGOING`
  3. `sumActiveQuantity + quantity > perMemberLimit` → `ORDER_LIMIT_EXCEEDED`
  4. `stockPort.decrease`
  5. `Order(CREATED)` 저장
  6. `paymentPort.pay` → Approved면 `markPaid`, Failed면 `cancel(reason)` + `stockPort.restore`
  7. 저장 후 결과 반환 (결제 실패는 예외를 던지지 않는다 → 트랜잭션 커밋)
- [ ] **Step 1: 테스트**
  - `OrderTest`: `취소된_주문은_다시_취소할_수_없다`, `결제완료로_전이`.
  - `OrderServiceTest`: `결제_승인시_PAID_재고_차감`, `결제_실패시_주문은_취소로_남고_재고는_복구된다`, `제한_초과_주문은_거절된다`(제한 2, 기존 1 + 신규 2 → 409, 재고 불변), `취소된_주문은_제한에서_제외된다`(기존 취소 2 + 신규 2 → 성공), `진행중이_아닌_딜은_주문할_수_없다`, `재고_부족이면_STOCK_SOLD_OUT`.
  - `OrderApiTest`(Mock PG `failure-rate` 테스트 프로퍼티 0): `주문하면_201_PAID_남은수량_감소`, `실패율_1이면_201_CANCELLED_남은수량_원복`(`@TestPropertySource`), `비로그인_주문은_401`.
- [ ] **Step 2~4:** FAIL → 구현 → PASS.
- [ ] **Step 5: 커밋** `feat: 주문 생성(락 없는 재고 차감 + 동기 결제)`.

### Task 9: 주문 취소와 내 주문 조회

**Files:**
- Modify: `order/application/service/OrderService.kt`, `order/adapter/in/web/OrderController.kt`, `order/application/port/out/PaymentPort.kt`(+`refund`)
- Create: `order/application/port/in/CancelOrderUseCase.kt`, `QueryMyOrdersUseCase.kt`
- Test: `order/application/OrderServiceTest.kt`, `order/adapter/in/web/OrderApiTest.kt`

**Interfaces:**
- Produces: `CancelOrderUseCase.cancel(memberId: Long, orderId: Long)`; `QueryMyOrdersUseCase.findMine(memberId: Long): List<OrderSummary>`
- 규칙: 본인 주문이 아니면 `ORDER_NOT_FOUND`(존재 노출 안 함). `PAID`면 `paymentPort.refund` 후 `cancel(USER_REQUESTED)` + 재고 복구. 기간 제한 없음. 딜 종료 후 취소도 재고 수치는 복구(스펙 6장).
- [ ] **Step 1: 테스트** `이미_취소된_주문은_다시_취소할_수_없다`(재고 1회만 복구 확인), `결제완료_주문_취소시_환불되고_재고_복구`, `남의_주문_취소는_404`, `내_주문만_최신순으로_조회된다`.
- [ ] **Step 2~4:** FAIL → 구현 → PASS.
- [ ] **Step 5: 커밋** `feat: 주문 취소(환불·재고 복구)와 내 주문 조회`.

### Task 10: 스펙-구현 계약 테스트와 Phase 1 마무리

**Files:**
- Modify: `build.gradle.kts` (`org.springdoc:springdoc-openapi-starter-webmvc-ui` — Spring Boot 4 호환 최신 3.x)
- Create: `src/test/kotlin/com/dogyun/kairos/OpenApiContractTest.kt`, `README.md`

- [ ] **Step 1: 테스트** `OpenApiContractTest`: `docs/api/openapi.yaml`의 (method, path) 집합 == `/v3/api-docs`가 생성한 (method, path) 집합. YAML 파싱은 Jackson YAML(Boot에 포함) 사용.
- [ ] **Step 2:** 실행 → 차이가 있으면 **스펙을 기준으로** 구현을 고친다. 스펙 자체가 틀렸다면 스펙을 먼저 고치고 별도 커밋.
- [ ] **Step 3:** `./gradlew test` 전체 PASS, `docker compose up -d` 후 `./gradlew bootRun`으로 수동 확인: 관리자 로그인 → 딜 등록 → 회원가입/로그인 → 주문 → 내 주문 → 취소.
- [ ] **Step 4:** `README.md`: 프로젝트 소개(스펙 1장 한 문단), 실행 방법, 진행 단계 체크리스트(Phase 0~5), 문서 위치.
- [ ] **Step 5: 커밋** `test: OpenAPI 스펙-구현 계약 테스트와 README` (springdoc 추가 이유 기재).

---

## 스펙과의 차이 (실행 전 확인)

- 스펙 6장의 "결제 대기 시간 초과 시 자동 취소(기본 10분)"는 동기 결제 결정과 맞지 않는다. 주문과 결제가 한 트랜잭션에서 끝나므로 CREATED 주문이 커밋된 채 남지 않는다. Phase 1에서는 이를 **Mock PG 타임아웃(`kairos.mock-pg.timeout`, 기본 3초) → `PAYMENT_TIMEOUT` 취소**로 대체하고, 스케줄러 기반 자동 취소는 비동기 결제가 되는 Phase 4로 미룬다. 설계 문서도 같은 내용으로 고친다(Task 2 커밋에 포함).
- 결제 승인과 취소의 경합 규칙(스펙 6장)도 동기 결제에서는 발생하지 않으므로 Phase 4에서 구현한다. Phase 1의 상태 전이는 도메인 메서드 검증으로 충분하다.
