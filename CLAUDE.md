# Kairos

마켓플레이스 커머스(판매자가 상품을 올리고 구매자가 타임딜·일반 상품을 사는 서비스). 모놀리식에서 최적화를 끝까지 해 본 뒤, 남는 한계를 근거로 MSA로 전환하는 학습용 개인 프로젝트다.

- 설계 문서(근거와 상세 규칙): `docs/superpowers/specs/2026-11-08-kairos-design.md`
- 커머스 로드맵: `docs/superpowers/specs/2026-11-09-commerce-roadmap-design.md`
- C1 스펙: `docs/superpowers/specs/2026-11-09-c1-seller-catalog-design.md`
- C2 스펙: `docs/superpowers/specs/2026-11-09-c2-purchase-design.md`
- C3 스펙: `docs/superpowers/specs/2026-11-10-c3-benefits-reviews-design.md`
- C4 스펙: `docs/superpowers/specs/2026-11-10-c4-returns-settlement-design.md`
- 현재 구현 계획: `docs/superpowers/plans/2026-11-10-c4-returns-settlement.md` (이전: `docs/superpowers/plans/2026-11-10-c3-benefits-reviews.md`, `docs/superpowers/plans/2026-11-09-c2-purchase.md`, `docs/superpowers/plans/2026-11-09-c1-seller-catalog.md`)
- API 계약: `docs/api/openapi.yaml`
- 현재 단계: **C4 반품·정산**(커머스 로드맵, `docs/superpowers/specs/2026-11-09-commerce-roadmap-design.md`). Phase 0~2, C1, C2, C3는 완료

## 스택
Kotlin 2.3, Spring Boot 4.1, JDK 21, Gradle Kotlin DSL, MySQL 8.4, Redis 7.4, Flyway, QueryDSL 7.7 (OpenFeign fork, KSP), JUnit5, Testcontainers, ArchUnit.

## 명령어
- 테스트: `./gradlew test` (Docker 실행 필요, Testcontainers 사용)
- 로컬 실행: `.env.example`을 `.env`로 복사해 값을 채운 뒤 `docker compose up -d`, `./gradlew bootRun`
- 모니터링: `docker compose up -d` 후 Grafana(localhost:3000, 대시보드 "Kairos 타임딜"), Prometheus(localhost:9090). 메트릭은 관리 포트 8081, 로그는 `logs/kairos.log`(traceId 포함).
- 설정값(DB, Redis, JWT 비밀키, 관리자 계정, `TOSS_SECRET_KEY`, `SWEETTRACKER_API_KEY`)은 `.env`로만 주입한다. `.env`는 커밋하지 않고, 키를 추가하면 `.env.example`도 함께 고친다.

## 아키텍처: 4계층 DDD
모듈(= 바운디드 컨텍스트 = 미래의 서비스): `member`, `deal`, `order`, `cart`, `payment`, `seller`, `catalog`, `file`, `benefit`, `review`, `settlement`, `common`

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
- 에러 코드는 `ErrorCode` enum과 OpenAPI의 `ErrorResponse.code`가 일치해야 한다.

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
- 반품 환불은 `OrderReturnService`가 C2 취소와 같은 tx1 → PG → tx2 패턴으로 한다. 락 순서는 `orders → member_point → coupon → sku → product`(ADR 0005). PG 불확실이면 `RETURN_REFUNDING`으로 두고 대사가 맞춘다.
- 정산은 생성 시점 스냅샷이며 생성 뒤 불변이다. 금액은 저장된 스냅샷(수수료율, `line_amount`, 쿠폰 할인, 배송비, 반품 부담액)에서만 계산하고 상품·정책·판매자의 현재 값은 읽지 않는다. 판매자별 한 트랜잭션으로 만들고, UNIQUE 세 개(판매자·기간, 판매자 주문, `settlement_run` 기간)로 멱등을 보장하며, 주문 테이블에는 쓰지 않는다. 음수 정산은 지급 0과 `CARRY_OVER` 조정으로 다음 정산에 넘기고(소프트 딜리트 금지), 기간은 오래된 것부터 엄격히 생성한다. Spring Batch는 Phase 3-2 측정 뒤에 검토한다.
- `sweepByKeyset`·`TransactionRunner`는 `common.application`에 두고 구현은 `common.infrastructure`에 둔다(다른 모듈 application이 order.application을 import하지 않도록).
- 최적화, Kafka, Gateway, Resilience4j는 해당 Phase 전에 미리 넣지 않는다.

## 코드 스타일
- Lombok 사용 안 함. 값 객체는 Kotlin `value class`/`data class`로 만든다.
- 애플리케이션 서비스 테스트는 Mock 라이브러리 대신 인메모리 Fake를 쓴다.
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
