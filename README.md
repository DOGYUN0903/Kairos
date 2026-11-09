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

## 진행 단계
- [x] Phase 0. 스펙과 기반 (OpenAPI, Docker Compose, Flyway)
- [x] Phase 1. 모놀리식 기본 구현
- [x] Phase 2. 기준선 측정 (k6) — [측정 리포트](docs/perf/2026-11-08-phase2-baseline.md)
- [x] C1. 판매자와 상품
- [ ] C2. 구매
- [ ] C3. 혜택과 후기
- [ ] C4. 정산
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
- ADR 0002(카탈로그 검색의 읽기 전용 조인): `docs/adr/0002-catalog-search-cross-module-read-join.md`
- 업로드 파일은 로컬 `uploads/`에 저장된다(경로는 `.env`의 `KAIROS_FILE_DIR`).
- API 계약: `docs/api/openapi.yaml` (`OpenApiContractTest`가 구현과의 일치를 검증)
- 아키텍처 결정 기록: `docs/adr/`
