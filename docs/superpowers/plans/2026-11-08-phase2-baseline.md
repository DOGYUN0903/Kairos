# Phase 2 Baseline Measurement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 재고 100개 딜에 1,000명이 오픈 순간 1개씩 주문하는 장면을 자원 제한된 로컬 환경에서 재현 가능하게 측정하고(조건 A: PG 100ms, B: PG 1s, 각 3회), 결과를 리포트로 남긴다.

**Architecture:** 앱을 Docker 이미지로 만들어 `perf` compose 프로필에서 자원 제한과 함께 띄운다. 호스트의 k6가 준비(로그인)·오픈 직전 조회·오픈 스파이크를 실행하고, 셸 스크립트가 재시작·워밍업·딜 생성·k6·결과 수집(DB + Prometheus + k6 요약)을 순서대로 묶는다.

**Tech Stack:** Docker / Compose(profiles, `deploy.resources.limits`), k6 v2.2, Bash, `mysql` CLI(컨테이너 exec), Prometheus HTTP API, `jq`.

**Spec:** `docs/superpowers/specs/2026-11-08-phase2-baseline-design.md` (실행자는 반드시 함께 읽는다, 특히 6장·7장·9장). 프로젝트 규칙: `CLAUDE.md`.

## Global Constraints

- **애플리케이션 코드(src/main) 변경 금지.** Phase 1 규율(락·인덱스·튜닝 없음) 유지. 측정 대상이 바뀌면 안 된다. 필요한 설정은 perf 프로필 환경변수로만 준다.
- 자원 제한(스펙 6.1): app CPU 2 / 메모리 1.5GB(JVM `-XX:MaxRAMPercentage` 또는 `-Xmx1g`), mysql CPU 2 / 2GB, redis CPU 0.5 / 256MB.
- 앱 기본 설정 유지: HikariCP 최대 10, Tomcat 최대 스레드 200. 실제 값은 결과 JSON과 리포트에 기록.
- Mock PG: 조건 A `delay=100ms`, B `delay=1s`, 둘 다 `failure-rate=0`, `timeout=3s`(기본값).
- perf Prometheus 수집 간격 1초. 실행별 지표는 시작·종료 시각 구간으로 조회.
- 측정 장면: 딜 재고 100, `perMemberLimit` 1, 사용자 1,000명(`perf-user-0001`~`perf-user-1000`, 고정 비밀번호는 perf 전용 값으로 스크립트 상수 또는 `.env`의 `PERF_USER_PASSWORD`), 오픈 직전 조회 60초, 오픈 스파이크 1,000건이 약 1~2초 안에 도착.
- 가입·로그인 동시성 20~50, 측정 구간 밖.
- 실행 전 앱만 재시작, 워밍업(별도 딜, 소량 주문) 후 측정. 매 실행 새 딜.
- 결과 JSON에 토큰·비밀번호 금지. `perf/.tokens*` 등 임시 파일은 gitignore.
- API 일시는 오프셋 없는 LocalDateTime(KST).
- 커밋: 한국어 Conventional Commits + `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 브랜치 `feature/phase2-baseline`, push 금지.

## Review Focus

1. **준비 단계가 측정 구간에 섞임**: 로그인·가입이 오픈 직전 조회 시작 전에 끝나야 한다. → Task 3 스크립트 단계 순서 + 결과 JSON에 단계별 타임스탬프.
2. **이전 실행 데이터 혼입**: 새 딜 id로만 집계하고, Prometheus 조회는 실행 구간으로 제한. → Task 3 `collect-result.sh`.
3. **DB와 메트릭의 순판매량 불일치**: 둘 다 기록하고 일치 여부 필드(`soldMatches`)를 남긴다. → Task 3.
4. **조건 B 타임아웃**: k6 요청 타임아웃이 HikariCP 30초보다 길어야(예: 90초) 서버 쪽 결과를 그대로 본다. 상태 코드별 건수를 기록. → Task 2.
5. **k6 자체가 병목**: k6의 `dropped_iterations`, `http_req_failed`(연결 오류)와 호스트 CPU를 기록해 측정값이 k6 한계가 아님을 확인. → Task 2·4.

---

### Task 1: 앱 컨테이너화와 perf 프로필

**Files:**
- Create: `Dockerfile`, `.dockerignore`, `monitoring/prometheus/prometheus-perf.yml`(또는 기존 파일에 perf job 추가)
- Modify: `compose.yaml`(profile `perf`: `app` 서비스 + mysql/redis 자원 제한 + perf용 prometheus 대상), `.env.example`(perf 키), `.gitignore`

- [ ] **Step 1:** 멀티 스테이지 Dockerfile(빌드: JDK 21 + Gradle `bootJar -x test`, 런타임: JRE 21). 컨테이너 안에서 앱이 compose 네트워크의 `mysql`/`redis`를 보도록 perf 프로필 환경변수(`DB_HOST=mysql`, `REDIS_HOST=redis`)를 준다. 앱 포트 8080, 관리 포트 8081 노출.
- [ ] **Step 2:** compose `perf` 프로필: `app`(CPU 2, 1.5GB, `JAVA_TOOL_OPTIONS`로 힙 1GB, Mock PG 지연은 `KAIROS_MOCK_PG_DELAY` 같은 환경변수로 주입), mysql·redis 자원 제한은 perf 프로필에서만 적용되게 구성(기본 개발 흐름에 영향 없게). Prometheus는 perf 시 `app:8081`을 1초 간격으로 수집하는 job 추가(기존 `host.docker.internal:8081` job은 유지).
- [ ] **Step 3: 검증** `docker compose --profile perf up -d --build` → `curl -s localhost:8081/actuator/health` = UP, `docker stats --no-stream`에서 제한값 확인, Prometheus `/api/v1/targets`에서 perf job `up`. 결과를 리포트 파일에 기록.
- [ ] **Step 4:** `./gradlew test` 통과(빌드 설정 변경이 테스트를 깨지 않음). 
- [ ] **Step 5: 커밋** `chore: 측정용 앱 컨테이너와 자원 제한 perf 프로필`.

### Task 2: k6 시나리오

**Files:**
- Create: `perf/k6/setup-users.js`, `perf/k6/timedeal-open.js`, `perf/k6/lib/config.js`(공통 BASE_URL 등), `perf/README.md`(사용법)

**Interfaces:**
- `setup-users.js`: 입력 env `USER_COUNT`(기본 1000), `PASSWORD`; 가입(409 무시) 후 로그인 → 토큰을 `perf/.tokens.json`(gitignore)에 쓴다. 동시성 `CONCURRENCY`(기본 30).
- `timedeal-open.js`: 입력 env `DEAL_ID`, `OPEN_AT_EPOCH_MS`, `TOKENS_FILE`, `VIEW_SECONDS`(기본 60). 1,000 VU가 각자 토큰 하나를 맡아 오픈 전까지 딜 상세를 1~2초 간격으로 조회하다가 `OPEN_AT_EPOCH_MS`(+0~수십 ms 지터 없이)에 주문 1건(`quantity: 1`). 요청 타임아웃 90초. `handleSummary`로 `perf/results/<run>/k6-summary.json` 저장: 주문 요청 상태 코드별 건수, 응답의 `status`(PAID/CANCELLED)별 건수, `errorCode`별 건수, 주문 지연 p50/p95/p99/max, 주문 요청의 실제 도착 폭(첫 요청~마지막 요청 시작 시각 차), `dropped_iterations`.
- [ ] **Step 1:** 작은 규모 드라이런 지원(env로 `USER_COUNT=50`, 재고 5). 드라이런으로 스크립트가 끝까지 동작하는지 확인(Task 1 perf 환경 사용, 딜은 관리자 API로 수동 생성).
- [ ] **Step 2:** 드라이런 결과 요약(상태 코드 분포, 도착 폭)을 리포트에 기록.
- [ ] **Step 3: 커밋** `feat: 타임딜 오픈 k6 시나리오(회원 준비, 오픈 직전 조회, 주문 스파이크)`.

### Task 3: 실행·수집 스크립트

**Files:**
- Create: `perf/scripts/run-baseline.sh`, `perf/scripts/collect-result.sh`, `perf/scripts/lib.sh`(공통: 관리자 로그인, 딜 생성, Prometheus 조회)

**Interfaces:**
- `run-baseline.sh <A|B> [runLabel]`: 
  1. 조건에 맞는 PG 지연으로 `app`만 재생성/재시작 → 관리 포트 health UP 대기
  2. 워밍업: 워밍업 딜(오픈 과거, 재고 1000) 생성 → 소량 주문(예: 50건, 동시성 10)
  3. 회원 로그인(`setup-users.js`) → 토큰 파일
  4. 측정 딜 생성: 재고 100, perMemberLimit 1, `openAt = now + VIEW_SECONDS + 5초 여유`, closeAt = openAt + 1시간 (LocalDateTime KST)
  5. 타임스탬프 기록(start) → `timedeal-open.js` 실행 → 타임스탬프 기록(end, 마지막 응답 + 5초)
  6. `collect-result.sh` 호출 → `perf/results/<yyyyMMdd-HHmmss>-<A|B>/result.json`
  7. 실행 구간이 맞춰진 Grafana 대시보드 URL 출력(`dealId` 변수 포함)
- `collect-result.sh <runDir> <dealId> <startEpoch> <endEpoch>`: 
  - DB(`docker compose exec mysql mysql ...`): 딜의 PAID 주문 수량 합, CANCELLED 수, 주문 행 수, `stock.remaining_quantity`, `stock.total_quantity`
  - Prometheus(구간 조회): `kairos_deal_sold`/`capacity`/`oversold` 최종값, `kairos_order_placed_total` result별 increase, `max_over_time(hikaricp_connections_pending[구간])`, `max_over_time(hikaricp_connections_active[...])`, `max_over_time(tomcat_threads_busy_threads[...])`, 결제 p95
  - k6 요약 병합, 환경 정보(조건, PG 지연, 자원 제한, Hikari max, Tomcat max, 호스트 CPU/메모리, k6 버전, git 커밋)
  - 파생값: `oversoldUnits = max(0, paidQty − total)`, `soldMatches = (paidQty == metricSold)`, `lostUpdateGap = paidQty − (total − remaining)`
- [ ] **Step 1:** 조건 A 1회 실행으로 끝까지 검증(이 실행 결과는 리허설로 `perf/results/rehearsal-*`에 두거나 버림).
- [ ] **Step 2:** `result.json` 예시를 리포트에 기록, 리뷰 포커스 1~3 확인.
- [ ] **Step 3: 커밋** `feat: 기준선 측정 실행·결과 수집 스크립트`.

### Task 4: 측정 실행과 리포트

**Files:**
- Create: `perf/results/<run>/result.json` ×6(+ k6-summary.json), `docs/perf/2026-11-08-phase2-baseline.md`, `docs/perf/images/.gitkeep`
- Modify: `README.md`(Phase 2 체크, 측정 방법 링크)

- [ ] **Step 1:** 측정 전 확인: 다른 무거운 프로세스 없음, Docker 자원 할당 그대로. 조건 A 3회, 조건 B 3회 실행(조건 사이 앱 재시작은 스크립트가 처리).
- [ ] **Step 2:** 리포트 작성(한국어):
  - 측정 환경과 조건(자원 제한, 기본 설정 값, PG 지연, 측정 오버헤드: 추적 샘플링 100%, JSON 파일 로그)
  - 조건별 3회 결과 표 + 중앙값: PAID 수, **초과 판매 수량**, 재고 최종값과 lostUpdateGap, SOLD_OUT 수, 상태 코드별(500/타임아웃 포함), 주문 p50/p95/p99/max, HikariCP 대기 최대, Tomcat 스레드 최대, 결제 p95, 도착 폭, k6 dropped
  - 해석: 초과 판매가 왜 생겼는가(조회-차감-저장 사이 경합, lost update로 재고가 덜 줄어든 이유), DB와 메트릭 일치 여부, 조건 B의 병목(커넥션 풀 10 × 결제 1초 → 대기·타임아웃·스레드 고갈)과 MSA 전환 근거로서의 의미, 결과 변동성(비결정적 경합)
  - 한계: 로컬 측정, 절대 수치 아님
  - 스크린샷 자리: 각 조건 대표 실행의 Grafana URL(스크립트 출력)과 `images/` 경로 표시 — 사람이 캡처해 넣는다
  - Phase 3에서 같은 명령으로 다시 측정하는 방법
- [ ] **Step 3:** README 갱신, 커밋 `docs: Phase 2 기준선 측정 결과 리포트`.
