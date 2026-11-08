# Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prometheus·Grafana·구조화 로그를 붙여, 오픈 순간 주문 폭주 시 서버 내부(응답 시간, 주문 결과, 순판매량 vs 총수량, 커넥션 풀, 스레드, 결제 지연)를 한 대시보드에서 보이게 한다.

**Architecture:** Actuator를 관리 포트 8081로 분리하고 Prometheus가 수집한다. 비즈니스 메트릭은 application 계층의 인터페이스(`OrderMetrics`, `PaymentMetrics`)로 기록하고 Micrometer 구현은 infrastructure에 둔다. 순판매량은 커밋된 주문만 메모리 카운터에 반영한다.

**Tech Stack:** Spring Boot 4.1 Actuator, Micrometer + `micrometer-registry-prometheus`, Prometheus, Grafana(프로비저닝), Spring Boot 구조화 로깅(ECS).

**Spec:** `docs/superpowers/specs/2026-11-08-observability-design.md` (실행자는 반드시 함께 읽는다). 프로젝트 규칙: `CLAUDE.md`.

## Global Constraints

- 4계층 DDD 규칙 유지: `domain`은 Micrometer·Spring을 import하지 않는다. ArchitectureTest는 항상 통과해야 한다.
- 메트릭 이름은 스펙 5.3 표 그대로: `kairos.order.placed`(tag `result`), `kairos.deal.sold`, `kairos.deal.capacity`, `kairos.deal.oversold`(tag `dealId`), `kairos.payment.duration`(tag `result` = APPROVED/FAILED/TIMEOUT).
- `kairos.order.placed` result 값: PAID, PAYMENT_FAILED, PAYMENT_TIMEOUT, SOLD_OUT, LIMIT_EXCEEDED, DEAL_NOT_ONGOING, OTHER.
- 순판매량은 DB에서 계산하지 않는다. 커밋 후(`afterCommit`)에만 반영한다.
- 관리 포트 `management.server.port=8081`, 노출 엔드포인트 `health,info,metrics,prometheus`. 서비스 포트 8080에는 Actuator가 없다.
- `server.tomcat.mbeanregistry.enabled=true`, `management.metrics.distribution.percentiles-histogram.http.server.requests=true`.
- 이미지 버전은 고정 태그(latest 금지). Grafana 관리자 비밀번호는 `.env`(`GRAFANA_ADMIN_PASSWORD`)로 주입하고 `.env.example`에도 키를 추가한다.
- Phase 1 규율 유지: 락·인덱스·최적화 추가 금지.
- 커밋: 한국어 Conventional Commits, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 의존성 추가 커밋은 본문에 이유.
- 테스트: `./gradlew test` (Docker 필요). JDK 21: `/Users/huuimcompany/Library/Java/JavaVirtualMachines/temurin-21.0.10/Contents/Home`.
- 브랜치 `feature/observability`. push 금지.

## Review Focus

1. **결제 실패로 같은 트랜잭션 안에서 복구된 주문**: 순판매량에 반영되지 않아야 한다. → Task 3 `결제_실패_주문은_순판매량에_반영되지_않는다`.
2. **롤백된 주문**(예: 재고 부족 예외): 카운터 `SOLD_OUT`은 1 증가, 순판매량은 그대로. → Task 3 `롤백된_주문은_순판매량에_반영되지_않는다`.
3. **사용자 취소**: 커밋 후 순판매량이 취소 수량만큼 줄어든다. → Task 3 `취소하면_순판매량이_줄어든다`.
4. **관리 포트 분리**: 8080의 `/actuator/prometheus`는 노출되지 않는다. → Task 1 `서비스_포트에는_actuator가_없다`.
5. **trace 전파**: 들어온 `traceparent`의 traceId를 이어받아 `X-Trace-Id`로 돌려준다. → Task 2 `traceparent_헤더가_있으면_같은_traceId를_이어받는다`.

---

### Task 1: 메트릭 인프라와 관리 포트

**Files:**
- Modify: `build.gradle.kts` (`runtimeOnly("io.micrometer:micrometer-registry-prometheus")`), `src/main/resources/application.yaml`, `compose.yaml`, `.env.example`, `common/security/SecurityConfig.kt`(관리 포트 처리 필요 시)
- Create: `monitoring/prometheus/prometheus.yml`, `monitoring/grafana/provisioning/datasources/prometheus.yml`, `monitoring/grafana/provisioning/dashboards/dashboards.yml`, `src/test/kotlin/com/dogyun/kairos/common/ManagementEndpointTest.kt`

**Interfaces:**
- Produces: 관리 포트 8081의 `/actuator/prometheus`. Grafana 데이터 소스 uid `prometheus`(Task 4 대시보드가 참조). 대시보드 프로비저닝 경로 `monitoring/grafana/dashboards/`(컨테이너 `/var/lib/grafana/dashboards`).

- [ ] **Step 1: 실패하는 테스트** `ManagementEndpointTest`(`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `management.server.port=0`, `@LocalManagementPort`):
  - `관리_포트에서_prometheus_지표를_노출한다`: 200, 본문에 `http_server_requests` 또는 `jvm_` 포함, `hikaricp_connections` 포함, `tomcat_threads` 포함.
  - `서비스_포트에는_actuator가_없다`: 서비스 포트 `/actuator/prometheus` → 404 또는 401(200이 아님).
- [ ] **Step 2:** 실행 → FAIL.
- [ ] **Step 3:** 구현. Prometheus scrape: job `kairos`, `host.docker.internal:8081`, `metrics_path: /actuator/prometheus`, `scrape_interval: 5s`. compose: prometheus(9090, `extra_hosts: host.docker.internal:host-gateway`), grafana(3000, 익명 접근 끔, admin 비밀번호 `${GRAFANA_ADMIN_PASSWORD}`, provisioning·dashboards 볼륨). `application.yaml`에 관리 포트가 로컬 전용이라는 주석.
- [ ] **Step 4:** `./gradlew test` PASS, `docker compose config` 오류 없음.
- [ ] **Step 5: 커밋** `chore: Prometheus·Grafana 인프라와 관리 포트 분리`.

### Task 2: 구조화 로그와 traceId

**Files:**
- Modify: `build.gradle.kts` (`implementation("io.micrometer:micrometer-tracing-bridge-otel")` — Boot 관리 버전), `src/main/resources/application.yaml`, `.gitignore`(`logs/`)
- Create: `common/presentation/TraceIdResponseFilter.kt`, `src/test/kotlin/com/dogyun/kairos/common/TraceIdTest.kt`

**Interfaces:**
- Produces: MDC 키 `traceId`/`spanId`(Micrometer Tracing 기본), 응답 헤더 `X-Trace-Id`.

- [ ] **Step 1: 실패하는 테스트** `TraceIdTest`(통합, MockMvc 또는 RANDOM_PORT): `응답헤더에_traceId를_돌려준다`(32자리 16진수), `traceparent_헤더가_있으면_같은_traceId를_이어받는다`(예: `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` → `X-Trace-Id: 4bf92f3577b34da6a3ce929d0e0e4736`), `인증_실패_응답에도_traceId가_있다`(401에도 헤더).
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현. 샘플링은 `management.tracing.sampling.probability: 1.0`, exporter 없음. `TraceIdResponseFilter`는 현재 `Tracer`의 traceId를 `X-Trace-Id`로 넣는다(관측 필터 이후, Security 필터보다 먼저 응답 헤더가 설정되도록 순서 확인). 로깅: `logging.file.name: logs/kairos.log`, `logging.structured.format.file: ecs`(ECS에 trace.id 포함 여부 확인, 없으면 MDC 키 포함 설정), 콘솔 패턴에 `[%X{traceId:-},%X{spanId:-}]` 포함(콘솔은 사람이 읽는 형식 유지).
- [ ] **Step 4:** `./gradlew test` PASS. 앱을 잠깐 띄워 `logs/kairos.log` 한 줄이 JSON이고 traceId가 응답 헤더 값과 일치하는지 확인(리포트에 한 줄 인용).
- [ ] **Step 5: 커밋** `feat: JSON 파일 로그와 traceId(Micrometer Tracing) 추가` (의존성 추가 이유: 로그 상관관계 + MSA 전환 시 traceparent 전파).

### Task 3: 비즈니스 메트릭

**Files:**
- Create: `order/application/OrderMetrics.kt`, `order/infrastructure/MicrometerOrderMetrics.kt`, `payment/application/PaymentMetrics.kt`, `payment/infrastructure/MicrometerPaymentMetrics.kt`
- Modify: `order/application/OrderService.kt`(결과 판정·커밋 후 반영), `payment/application/PaymentService.kt` 또는 `payment/infrastructure/MockPgClient.kt`(결제 소요 시간)
- Test: `order/application/OrderServiceTest.kt`(Fake OrderMetrics), `order/infrastructure/MicrometerOrderMetricsTest.kt`(`SimpleMeterRegistry`), `order/presentation/OrderMetricsApiTest.kt`(통합)

**Interfaces:**
- Produces:
  - `interface OrderMetrics { fun recordPlaced(result: PlaceOutcome); fun registerDeal(dealId: Long, totalQuantity: Int); fun addSold(dealId: Long, quantity: Int); fun subtractSold(dealId: Long, quantity: Int) }`
  - `enum class PlaceOutcome { PAID, PAYMENT_FAILED, PAYMENT_TIMEOUT, SOLD_OUT, LIMIT_EXCEEDED, DEAL_NOT_ONGOING, OTHER }` (order.application)
  - `interface PaymentMetrics { fun recordDuration(result: String /* APPROVED|FAILED|TIMEOUT */, nanos: Long) }`
  - Micrometer 구현: 딜별 `AtomicLong` sold/total을 `ConcurrentHashMap`에 두고 Gauge `kairos.deal.sold`·`kairos.deal.capacity`·`kairos.deal.oversold`(=max(0, sold−total))를 딜당 1회 등록.
- 규칙:
  - `place`: 결과마다 `recordPlaced` 정확히 1회(성공·거절·예외 모두; 예외는 기록 후 다시 던진다). PAID면 `registerDeal`(DealReader 스냅샷에 총수량이 없으면 DealSnapshot에 `totalQuantity: Int`를 추가하고 deal 쪽 매핑도 고친다) + `addSold`를 **커밋 후** 실행(`TransactionSynchronizationManager.registerSynchronization` afterCommit, 활성 트랜잭션이 없으면 즉시).
  - `cancel`: 커밋 후 `subtractSold`.
  - 롤백·결제 실패 주문은 sold에 반영하지 않는다.
- [ ] **Step 1: 테스트**
  - `OrderServiceTest`: `주문_결과별로_한번씩_기록된다`(PAID, PAYMENT_FAILED, SOLD_OUT, LIMIT_EXCEEDED, DEAL_NOT_ONGOING, DEAL_NOT_FOUND→OTHER), `결제_실패_주문은_순판매량에_반영되지_않는다`.
  - `MicrometerOrderMetricsTest`: `순판매량이_총수량을_넘으면_oversold가_차이를_보인다`(total 10, add 6+6 → sold 12, oversold 2).
  - `OrderMetricsApiTest`(통합): `주문하면_prometheus에_결과와_순판매량이_보인다`, `롤백된_주문은_순판매량에_반영되지_않는다`(재고 부족 409 → SOLD_OUT +1, sold 불변), `취소하면_순판매량이_줄어든다`.
  - 결제: `결제_소요시간이_결과별로_기록된다`.
- [ ] **Step 2:** FAIL 확인 → **Step 3:** 구현 → **Step 4:** `./gradlew test` PASS(ArchitectureTest 포함).
- [ ] **Step 5: 커밋** `feat: 주문 결과·순판매량·결제 소요시간 비즈니스 메트릭`.

### Task 4: Grafana 대시보드와 문서

**Files:**
- Create: `monitoring/grafana/dashboards/kairos-timedeal.json`
- Modify: `README.md`, `CLAUDE.md`(명령어·모니터링 한 줄)

- [ ] **Step 1:** 대시보드 JSON(데이터 소스 uid `prometheus`, 변수 `dealId`). 패널 7개(스펙 1장 순서):
  1. 주문 TPS: `sum(rate(http_server_requests_seconds_count{uri="/api/v1/orders",method="POST"}[30s]))` + p95/p99: `histogram_quantile(0.95|0.99, sum by (le)(rate(http_server_requests_seconds_bucket{uri="/api/v1/orders",method="POST"}[30s])))`
  2. 주문 결과별: `sum by (result)(rate(kairos_order_placed_total[30s]))`(누적 막대 또는 시계열)
  3. 순판매량 vs 총수량: `kairos_deal_sold{dealId="$dealId"}`, `kairos_deal_capacity{dealId="$dealId"}`
  4. 초과 판매 수량: `kairos_deal_oversold{dealId="$dealId"}` (stat, 0 초과 시 빨강)
  5. HikariCP: `hikaricp_connections_active`, `hikaricp_connections_pending`, `hikaricp_connections_max`
  6. Tomcat: `tomcat_threads_busy_threads`, `tomcat_threads_config_max_threads`
  7. 결제 소요 시간: `histogram_quantile(0.95, sum by (le, result)(rate(kairos_payment_duration_seconds_bucket[30s])))` (Timer에 퍼센타일 히스토그램 활성화 필요 — Task 3 구현이 켜지 않았다면 여기서 `management.metrics.distribution.percentiles-histogram.kairos.payment.duration=true` 추가)
  - 시간 범위 기본 15분, 새로고침 5초.
- [ ] **Step 2: 확인** `docker compose up -d` → `./gradlew bootRun` → 관리자 로그인·딜 생성(진행 중)·회원가입 후 주문 몇 건(curl) → Grafana API(`/api/search?query=Kairos`)로 대시보드 존재 확인, Prometheus API(`/api/v1/query?query=kairos_order_placed_total`)로 값 확인. 결과를 리포트에 남긴다. 앱 종료.
- [ ] **Step 3:** README에 모니터링 섹션(접속 주소 Grafana `localhost:3000`, Prometheus `localhost:9090`, 관리 포트 8081은 로컬 전용, 대시보드 이름, 로그 파일 위치). CLAUDE.md 명령어 섹션에 한 줄.
- [ ] **Step 4: 커밋** `feat: Kairos 타임딜 Grafana 대시보드와 모니터링 문서`.
