# Kairos

놓치면 다시 오지 않는 순간을 위한 타임딜 서비스. MSA를 직접 경험하기 위한 개인 프로젝트로, 스펙(OpenAPI)부터 작성하고(SDD) 모놀리식에서 쓸 수 있는 최적화를 단계별로 적용해 수치로 남긴 뒤, 그래도 남는 한계(장애 격리, 독립 확장)를 근거로 MSA로 전환하고 장애 실험으로 검증하는 것이 목표다. MSA는 목적이 아니라 필요해져서 선택하는 결과여야 한다.

## 스택
Kotlin 2.3, Spring Boot 4.1, JDK 21, Gradle Kotlin DSL, MySQL 8.4, Redis 7.4, Flyway, QueryDSL, springdoc-openapi, JUnit5, Testcontainers, ArchUnit.

## 실행 방법
1. `.env.example`을 `.env`로 복사하고 값을 채운다(DB, JWT 비밀키, 관리자 계정 등).
2. `docker compose up -d`
3. `./gradlew bootRun`
4. Swagger UI: http://localhost:8080/swagger-ui/index.html

테스트는 `./gradlew test` (Docker 실행 필요, Testcontainers 사용).

## 진행 단계
- [x] Phase 0. 스펙과 기반 (OpenAPI, Docker Compose, Flyway)
- [x] Phase 1. 모놀리식 기본 구현
- [ ] Phase 2. 기준선 측정 (k6)
- [ ] Phase 3. 모놀리식 최적화
- [ ] Phase 4. MSA 전환
- [ ] Phase 5. 장애 대응

## 문서
- 설계 문서: `docs/superpowers/specs/2026-11-08-kairos-design.md`
- 구현 계획: `docs/superpowers/plans/2026-11-08-kairos-phase0-1.md`
- API 계약: `docs/api/openapi.yaml` (`OpenApiContractTest`가 구현과의 일치를 검증)
- 아키텍처 결정 기록: `docs/adr/`
