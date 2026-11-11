# Kairos

판매자가 상품을 올리고 구매자가 타임딜·일반 상품을 사는 마켓플레이스 커머스(놓치면 다시 오지 않는 순간을 위한 타임딜 포함). MSA를 직접 경험하기 위한 개인 프로젝트로, 스펙(OpenAPI)부터 작성하고(SDD) 모놀리식에서 쓸 수 있는 최적화를 단계별로 적용해 수치로 남긴 뒤, 그래도 남는 한계(장애 격리, 독립 확장)를 근거로 MSA로 전환하고 장애 실험으로 검증하는 것이 목표다. MSA는 목적이 아니라 필요해져서 선택하는 결과여야 한다.

## 스택
Kotlin 2.3, Spring Boot 4.1, JDK 21, Gradle Kotlin DSL, MySQL 8.4, Redis 7.4, Flyway, QueryDSL, springdoc-openapi, JUnit5, Testcontainers, ArchUnit.

## 실행 방법
1. `.env.example`을 `.env`로 복사하고 값을 채운다(DB, JWT 비밀키, 관리자 계정 등).
2. `docker compose up -d`
3. `./gradlew bootRun`
4. Swagger UI: http://localhost:8080/swagger-ui/index.html

테스트는 `./gradlew test` (Docker 실행 필요, Testcontainers 사용).

## 모니터링
`docker compose up -d`로 Prometheus와 Grafana도 함께 뜬다. 앱은 호스트에서 실행하므로 Prometheus가 `host.docker.internal:8081`을 5초마다 수집한다.
- Grafana: http://localhost:3000 (사용자 `admin`, 비밀번호는 `.env`의 `GRAFANA_ADMIN_PASSWORD`). 대시보드 **Kairos 타임딜**이 자동 등록된다(`monitoring/grafana/dashboards/kairos-timedeal.json`). 상단 `dealId` 변수로 딜을 고른다.
- Prometheus: http://localhost:9090
- 관리 포트 8081(`/actuator/prometheus`)은 인증이 없으므로 로컬 전용이다. 운영 배포 시 외부에 열지 않는다.
- 로그: `logs/kairos.log`(JSON, ECS). 모든 요청 로그에 `traceId`가 있고 응답 헤더 `X-Trace-Id`로도 받을 수 있다.

## 프론트엔드
`frontend/`에 있다. Next.js 15(App Router) · React 19 · TypeScript strict · TanStack Query · react-hook-form + zod · Tailwind CSS(shadcn/ui), API 타입은 `docs/api/openapi.yaml`에서 생성한다(openapi-typescript + openapi-fetch).

실행:
1. `docker compose up -d`
2. `SPRING_PROFILES_ACTIVE=local,demo KAIROS_DEMO_PASSWORD=… ./gradlew bootRun` (`demo` 프로필이 데모 계정·상품을 시드한다. 데모 계정 `*@kairos.demo`의 비밀번호가 이 값이다)
3. `cd frontend && cp .env.example .env.local && pnpm install && pnpm dev`
4. http://localhost:3000 (Grafana도 기본 3000이라 겹치면 `pnpm dev --port 3001` 처럼 다른 포트를 쓴다)

명령(`frontend/`에서):
- `pnpm dev` / `pnpm build` / `pnpm start`
- `pnpm lint`, `pnpm typecheck`, `pnpm test`(Vitest)
- `pnpm gen:api`(OpenAPI → `src/api/schema.d.ts`), `pnpm check:api`(생성 파일이 최신인지 확인)
- `pnpm check:bundle`(운영 빌드에 데모 코드·관리자 계정 문자열이 없는지 확인)
- `pnpm e2e`(Playwright, 아래 전제 필요)

E2E 전제: `local,demo` 프로필 백엔드가 떠 있고 `KAIROS_DEMO_PASSWORD`를 같은 값으로 넘긴다(데모 관리자 `admin@kairos.demo`로 승인 단계를 진행한다). 처음 한 번 `pnpm exec playwright install chromium`. 주소는 환경변수로 바꾼다: `E2E_BASE_URL`(기본 http://localhost:3000, 비어 있으면 dev 서버를 직접 띄운다), `BACKEND_URL`(기본 http://localhost:8080). 예: `KAIROS_DEMO_PASSWORD=… pnpm e2e`. 데이터는 실행마다 고유한 이름으로 만들므로 재시드 상태에 기대지 않는다. CI(`.github/workflows/frontend.yml`)는 백엔드가 필요한 E2E를 빼고 check:api·lint·typecheck·test·build·check:bundle을 돌린다.

> 경고: `NEXT_PUBLIC_DEMO_*`는 운영 빌드에 두지 않는다. 빌드 시점에 번들에 들어가 데모 계정이 노출된다.

## 진행 단계
- [x] Phase 0. 스펙과 기반 (OpenAPI, Docker Compose, Flyway)
- [x] Phase 1. 모놀리식 기본 구현
- [x] Phase 2. 기준선 측정 (k6) — [측정 리포트](docs/perf/2026-11-08-phase2-baseline.md)
- [x] C1. 판매자와 상품
- [x] C2. 구매
- [x] C3. 혜택과 후기
- [x] C4. 반품과 정산
- [x] F1. 프론트엔드 기반과 C1 화면
- [ ] C5. 운영 데이터
- [ ] C6. 핫딜 추가
- [ ] Phase 3. 모놀리식 최적화
- [ ] Phase 4. MSA 전환
- [ ] Phase 5. 장애 대응

## 문서
- 설계 문서: `docs/superpowers/specs/2026-11-08-kairos-design.md`
- 구현 계획: `docs/superpowers/plans/2026-11-08-kairos-phase0-1.md`
- 커머스 로드맵: `docs/superpowers/specs/2026-11-09-commerce-roadmap-design.md`
- C1 스펙: `docs/superpowers/specs/2026-11-09-c1-seller-catalog-design.md`
- C2 스펙: `docs/superpowers/specs/2026-11-09-c2-purchase-design.md`
- C2 구현 계획: `docs/superpowers/plans/2026-11-09-c2-purchase.md`
- C3 스펙: `docs/superpowers/specs/2026-11-10-c3-benefits-reviews-design.md`
- C3 구현 계획: `docs/superpowers/plans/2026-11-10-c3-benefits-reviews.md`
- C4 스펙: `docs/superpowers/specs/2026-11-10-c4-returns-settlement-design.md`
- C4 구현 계획: `docs/superpowers/plans/2026-11-10-c4-returns-settlement.md`
- F1 스펙: `docs/superpowers/specs/2026-11-11-frontend-f1-foundation-design.md`
- F1 구현 계획: `docs/superpowers/plans/2026-11-11-frontend-f1-foundation.md`
- ADR 0005(C4 반품 환불과 정산 불변성): `docs/adr/0005-c4-return-refund-and-settlement-immutability.md`
- ADR 0004(C3 혜택 트랜잭션과 락 순서): `docs/adr/0004-c3-benefit-transaction-and-lock-order.md`
- ADR 0003(C2 주문 트랜잭션과 외부 호출 경계): `docs/adr/0003-c2-order-transaction-and-external-calls.md`
- ADR 0002(카탈로그 검색의 읽기 전용 조인): `docs/adr/0002-catalog-search-cross-module-read-join.md`
- 업로드 파일은 로컬 `uploads/`에 저장된다(경로는 `.env`의 `KAIROS_FILE_DIR`).
- Mock PG(기본): 설정 없이 `KAIROS_PG_PROVIDER=mock`으로 동작한다. `paymentKey` 접두사로 동작을 고른다: 승인 `fail-`(거절), `unknown-`(승인됐지만 응답 유실), `lost-`(응답 유실, PG에도 없음), 취소 `cancelfail-`(거절), `cancelunknown-`(취소됐지만 응답 유실), `cancellost-`(첫 시도 미반영·응답 유실, 같은 멱등 키로 재시도하면 성공).
- 토스페이먼츠 테스트 모드: `.env`에 `KAIROS_PG_PROVIDER=toss`와 `TOSS_SECRET_KEY=test_sk_...`(테스트 키만)를 넣는다. 기본값은 `mock`이다.
- API 계약: `docs/api/openapi.yaml` (`OpenApiContractTest`가 구현과의 일치를 검증)
- 아키텍처 결정 기록: `docs/adr/`
