# 모니터링·로깅 설계 (Phase 2 준비)

> 작성일: 2026-11-08 · 상위 설계: `2026-11-08-kairos-design.md` 8장 Phase 2

## 1. 의도

Phase 2(기준선 측정)는 최적화 전의 상태를 수치로 남기는 단계다. k6가 주는 클라이언트 쪽 숫자만으로는 서버 안에서 무슨 일이 일어났는지 설명할 수 없다. 이 작업은 **오픈 순간 주문이 몰릴 때 서버 내부에서 일어나는 일을 한 화면에서 보이게** 만든다.

목표로 하는 장면(사용자와 합의): k6로 주문을 몰아넣었을 때 Grafana 대시보드 한 화면에서 같은 시간축으로 다음이 함께 보인다.

1. 주문 TPS와 p95/p99 응답 시간
2. 주문 결과별 건수: PAID, CANCELLED(결제 실패/타임아웃), SOLD_OUT, LIMIT_EXCEEDED, DEAL_NOT_ONGOING
3. **딜별 순판매량 vs 총수량** — 순판매량이 총수량을 넘는 순간이 초과 판매다
4. 초과 판매 수량
5. HikariCP 활성/대기 커넥션
6. Tomcat 사용 중 스레드
7. Mock PG 결제 소요 시간(결과별)

이 대시보드는 Phase 3(락 도입 전후 비교)와 Phase 4(결제 지연이 전체를 느리게 만드는 근거)에서 그대로 재사용한다.

## 2. 목표

- Prometheus가 애플리케이션 메트릭을 수집하고, Grafana에서 위 7개 패널이 있는 대시보드를 본다.
- `docker compose up -d` 한 번으로 Prometheus와 Grafana가 뜨고, 데이터 소스와 대시보드가 **자동으로 등록**된다(수동 클릭 없음).
- 대시보드 정의는 JSON 파일로 저장소에 커밋한다(재현 가능).
- 애플리케이션 로그를 JSON 구조화 형식으로 남기고, 모든 요청 로그에 `traceId`가 붙는다.

## 3. 하지 않을 것

- 로그 수집 도구(Loki, ELK) — 서버가 하나라 효과가 작다. Phase 4 이후 검토.
- 트레이스 수집·시각화(Zipkin, Tempo 등 exporter) — Phase 5에서 도입. 이번에는 traceId 생성·로그 기록·헤더 전파까지만.
- 알림(Alertmanager) — 측정이 목적이지 운영이 아니다.
- k6 시나리오 작성과 실제 측정 — Phase 2 본 작업에서 한다.
- 성능 최적화, 락, 인덱스 — Phase 1 규율 유지.

## 4. 실패로 볼 것

- 메트릭 수집 자체가 측정 대상을 왜곡하는 것(예: 요청마다 DB를 조회해 순판매량을 계산).
- 대시보드가 수동 설정에 의존해서 재현할 수 없는 것.
- 비즈니스 메트릭 코드가 도메인 계층을 오염시키는 것(Micrometer를 domain에서 import).
- 메트릭 엔드포인트가 인증 없이 외부에 노출되는 것(로컬 개발 환경에서만 허용).

## 5. 구성

### 5.1 인프라 (compose.yaml에 추가)
| 서비스 | 이미지 | 포트 | 비고 |
|---|---|---|---|
| prometheus | `prom/prometheus` (버전 고정) | 9090 | 앱의 `/actuator/prometheus`를 5초 간격 수집 |
| grafana | `grafana/grafana` (버전 고정) | 3000 | 데이터 소스·대시보드 프로비저닝, 관리자 비밀번호는 `.env` |

- 앱은 호스트에서 `./gradlew bootRun`으로 실행하므로 Prometheus는 `host.docker.internal:8081`(관리 포트)을 수집한다.
- 설정 파일 위치: `monitoring/prometheus/prometheus.yml`, `monitoring/grafana/provisioning/{datasources,dashboards}/`, `monitoring/grafana/dashboards/kairos-timedeal.json`.

### 5.2 애플리케이션 메트릭
- 의존성: `micrometer-registry-prometheus`.
- Actuator 노출: `health, info, metrics, prometheus`.
- **관리 포트 분리(작성자 판단)**: `management.server.port=8081`로 Actuator를 서비스 포트(8080)와 분리하고, 관리 포트는 인증 없이 허용한다. Prometheus는 `host.docker.internal:8081`을 수집한다. 서비스 API 포트에는 메트릭이 노출되지 않는다. 운영 배포 시에는 관리 포트를 외부에 열지 않는다(README와 설정 주석에 명시).
- HTTP 요청 지표에 퍼센타일 히스토그램을 켠다(`http.server.requests`, p95/p99 계산용).
- 기본 제공 지표 사용: HikariCP, JVM. Tomcat 스레드 지표는 `server.tomcat.mbeanregistry.enabled=true`를 켜야 나온다.

### 5.3 비즈니스 메트릭 (작성자 판단 포함)
| 이름 | 종류 | 태그 | 기록 시점 |
|---|---|---|---|
| `kairos.order.placed` | Counter | `result` = PAID / PAYMENT_FAILED / PAYMENT_TIMEOUT / SOLD_OUT / LIMIT_EXCEEDED / DEAL_NOT_ONGOING / OTHER | 주문 생성 요청 하나가 끝날 때마다 1회(성공·거절·예외 모두) |
| `kairos.deal.sold` | Gauge | `dealId` | 딜별 **순판매량**: 커밋된 PAID 주문 수량의 누적 − 커밋된 취소 수량 |
| `kairos.deal.capacity` | Gauge | `dealId` | 딜별 총수량(딜 생성·수정 커밋 시 또는 첫 주문 시 등록) |
| `kairos.deal.oversold` | Gauge | `dealId` | `max(0, sold − total)` — 초과 판매된 수량 |
| `kairos.payment.duration` | Timer | `result` = APPROVED / FAILED / TIMEOUT | Mock PG 호출마다 |

- **순판매량은 DB 값에서 계산하지 않는다.** 락이 없으면 lost update 때문에 DB의 남은 수량이 실제보다 *크게* 남는다. 그래서 `총수량 − 남은 수량`으로는 초과 판매가 드러나지 않는다. 대신 **커밋된 주문을 메모리 카운터(딜별 AtomicLong)에 누적**한다. 동시 주문이 같은 재고를 읽고 모두 성공하면 이 누적값이 총수량을 넘는다 — 이것이 초과 판매의 1차 지표다.
- **커밋 이후에만 반영한다.** 트랜잭션이 롤백된 주문이 카운터를 올리면 가짜 초과 판매가 생긴다. `TransactionSynchronization.afterCommit`(또는 `@TransactionalEventListener(AFTER_COMMIT)`)에서 반영한다. 사용자 취소도 커밋 후 차감한다. 결제 실패로 같은 트랜잭션 안에서 복구된 주문은 PAID가 아니므로 누적하지 않는다.
- 앱 재시작 시 카운터는 0부터 다시 시작한다. 측정 단위가 "새 딜 하나에 대한 부하 테스트 1회"이기 때문이다.
- `kairos.order.placed`의 결과 분류: 결제 실패는 `CancelReason`(PAYMENT_FAILED/PAYMENT_TIMEOUT)을 그대로 태그로 쓴다. 거절은 `DomainException`의 ErrorCode로 분류(STOCK_SOLD_OUT→SOLD_OUT, ORDER_LIMIT_EXCEEDED→LIMIT_EXCEEDED, DEAL_NOT_ONGOING), 나머지 예외(DEAL_NOT_FOUND, INVALID_REQUEST, 500 등)는 OTHER. 대시보드는 PAYMENT_FAILED+PAYMENT_TIMEOUT을 CANCELLED로 합쳐 보여 줄 수 있다. 주문 취소 API는 이 카운터에 포함하지 않는다.
- 기록 위치: `OrderService.place`를 감싸는 application 계층 코드에서 결과를 판정해 `OrderMetrics` 인터페이스를 호출한다. Micrometer 구현은 infrastructure에 둔다. domain은 Micrometer를 모른다.
- 재고 복구 시점의 초과 판매 감지는 기존 WARN 로그로 충분하므로 별도 메트릭을 두지 않는다(결제 실패·취소가 없는 실행에서는 발생하지 않는 2차 신호이기 때문).
- `dealId` 태그는 측정용 딜 수가 적다는 전제다(카디널리티 주의를 주석으로 남긴다).

### 5.4 로깅
- Spring Boot 기본 구조화 로깅: 콘솔은 사람이 읽는 형식 유지, **파일 로그는 JSON(ECS 형식)** 으로 `logs/kairos.log`에 남긴다. `logs/`는 gitignore.
- **traceId(사용자 결정)**: Micrometer Tracing(`micrometer-tracing-bridge-otel`)을 추가해 요청마다 `traceId`/`spanId`가 MDC에 들어가고 콘솔·JSON 로그에 자동으로 찍히게 한다. 응답 헤더 `X-Trace-Id`로 `traceId`를 돌려준다. 들어오는 W3C `traceparent` 헤더가 있으면 그 trace를 이어받는다(MSA 전환 시 서비스 간 전파 기반). 트레이스 수집기(Zipkin/Tempo) 연동은 Phase 5로 미룬다(exporter 없음).
- 기존 로그(초과 판매 WARN, 예외 처리 로그)는 그대로 쓰되 dealId 등 핵심 값을 키-값으로 남긴다.

## 6. 완료 기준

- `docker compose up -d` + `./gradlew bootRun` 후 Grafana(`localhost:3000`)에 Kairos 대시보드가 자동으로 보이고, 7개 패널이 데이터를 그린다.
- 간단한 수동 부하(예: curl 반복이나 짧은 k6 스모크)로 주문을 넣으면 주문 결과 카운터, 순판매량, 응답 시간 패널이 움직인다.
- 테스트: 비즈니스 메트릭 기록(주문 결과별 카운터 증가, 순판매량 갱신)을 단위·통합 테스트로 검증한다. 관리 포트의 `/actuator/prometheus` 응답에 `kairos_order_placed_total`이 포함된다. 롤백된 주문은 순판매량에 반영되지 않는다(테스트).
- JSON 로그 한 줄에 `traceId`가 포함되고, 응답 헤더 `X-Trace-Id` 값과 일치한다.
- ArchitectureTest 통과(domain에 Micrometer 의존 없음).
- README에 모니터링 실행 방법과 대시보드 접속 정보 추가.

## 7. 빌더에게 맡기는 것

- Prometheus/Grafana 이미지의 구체 버전(최신 안정 버전으로 고정).
- 대시보드 레이아웃과 PromQL 세부.
- 메트릭 기록 인터페이스 이름과 배치(위 원칙을 지키는 선에서).
