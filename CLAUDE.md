# Kairos

타임딜 서비스. 모놀리식에서 최적화를 끝까지 해 본 뒤, 남는 한계를 근거로 MSA로 전환하는 학습용 개인 프로젝트다.

- 설계 문서(근거와 상세 규칙): `docs/superpowers/specs/2026-11-08-kairos-design.md`
- 현재 구현 계획: `docs/superpowers/plans/2026-11-08-kairos-phase0-1.md`
- API 계약: `docs/api/openapi.yaml`
- 현재 단계: **Phase 0~1** (스펙 작성 + 모듈러 모놀리식 기본 구현)

## 스택
Kotlin 2.3, Spring Boot 4.1, JDK 21, Gradle Kotlin DSL, MySQL 8.4, Redis 7.4, Flyway, JUnit5, Testcontainers, ArchUnit.

## 명령어
- 테스트: `./gradlew test` (Docker 실행 필요, Testcontainers 사용)
- 로컬 실행: `.env.example`을 `.env`로 복사해 값을 채운 뒤 `docker compose up -d`, `./gradlew bootRun`
- 설정값(DB, Redis, JWT 비밀키, 관리자 계정)은 `.env`로만 주입한다. `.env`는 커밋하지 않고, 키를 추가하면 `.env.example`도 함께 고친다.

## 아키텍처: 4계층 DDD
모듈(= 바운디드 컨텍스트 = 미래의 서비스): `member`, `deal`, `order`, `payment`, `common`

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
- 소프트 딜리트만 쓴다(`status = DELETED`). 물리 삭제 금지. 엔티티에 `@SQLRestriction("status = 'ACTIVE'")`를 붙인다.
- 도메인 상태 컬럼은 `status`가 아니라 `order_status`, `deal_status`, `payment_status`처럼 이름을 짓는다.
- 연관관계는 같은 애그리거트 안에서만, `LAZY`, 기본 `@ManyToOne` 단방향. 양방향은 꼭 필요할 때만 쓰고 이유를 주석으로 남긴다. `@ManyToMany`, `@OneToOne` 금지.
- 스키마는 Flyway로만 관리한다(`ddl-auto: validate`, `open-in-view: false`). 이미 적용된 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다.

## API 규칙
- **SDD**: API를 바꾸려면 `docs/api/openapi.yaml`을 먼저 고치고 구현한다. 스펙과 구현은 계약 테스트로 맞춘다.
- 성공 응답은 `ApiResponse(message, data, timestamp)`, 에러는 `ErrorResponse(code, message, timestamp)`, 목록은 `?page&size` + `PageResponse`.
- application 계층은 Spring `Page`가 아니라 `PageResult`를 반환한다.
- 에러 코드는 `ErrorCode` enum과 OpenAPI의 `ErrorResponse.code`가 일치해야 한다.

## Phase 규율 (의도적으로 하지 않는 것)
- **Phase 1에서는 재고 차감에 락을 쓰지 않는다**(`@Version`, 비관적 락, 조건부 UPDATE 금지). PK/UNIQUE 외 인덱스도 추가하지 않는다. Phase 2에서 동시성 문제와 성능을 측정하고 Phase 3에서 개선한 뒤 전후를 비교하기 위해서다.
- 결제는 주문 트랜잭션 안에서 Mock PG를 동기 호출한다. PG 지연이 전체를 느리게 만드는 상황이 MSA 전환 근거가 된다.
- 최적화, Kafka, Gateway, Resilience4j는 해당 Phase 전에 미리 넣지 않는다.

## 코드 스타일
- Lombok 사용 안 함. 값 객체는 Kotlin `value class`/`data class`로 만든다.
- 애플리케이션 서비스 테스트는 Mock 라이브러리 대신 인메모리 Fake를 쓴다.
- 테스트 이름은 한국어 백틱 또는 언더스코어 문장으로 쓴다(예: `제한_초과_주문은_거절된다`).

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
