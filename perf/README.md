# perf (Phase 2 기준선 측정)

k6 시나리오. 모든 명령은 저장소 루트에서 실행하고, `.env` 값은 먼저 셸로 내보낸다(`set -a; . ./.env; set +a`). 전제: perf 스택이 떠 있어야 한다
(`docker compose -f compose.yaml -f compose.perf.yaml up -d --build`).

## 1. 회원 준비 (측정 구간 밖)
```
PASSWORD="$PERF_USER_PASSWORD" USER_COUNT=1000 CONCURRENCY=30 k6 run perf/k6/setup-users.js
```
`perf-user-0001@kairos.perf` ~ 가입(409는 이미 존재로 무시) 후 로그인, 토큰을 `perf/.tokens.json`(gitignore)에 쓴다.
액세스 토큰 TTL이 30분이라 앱 재시작 후 측정 직전에 다시 실행한다. `timedeal-open.js`는 25분 넘은 토큰 파일을 거부한다.

## 2. 오픈 시나리오
딜은 관리자 API로 만들고 `openAt`을 `VIEW_SECONDS`보다 몇 초 뒤(KST LocalDateTime)로 둔다. 같은 시각을 epoch ms로 넘긴다.
```
mkdir -p perf/results/<run>
DEAL_ID=<id> OPEN_AT_EPOCH_MS=<ms> VIEW_SECONDS=60 USER_COUNT=1000 RUN_NAME=<run> k6 run perf/k6/timedeal-open.js
```
- `OPEN_AT_EPOCH_MS`는 k6 시작 + VU 초기화(1,000 VU는 수 초~수십 초) + 원하는 조회 시간만큼 뒤여야 한다. `VIEW_SECONDS`는 기록용 설정값이고 실제 조회 구간은 OPEN_AT가 정하며 `viewActual`에 기록된다. 조회는 OPEN_AT 3초 전에 멈추고 대기한다.
- 각 VU(=사용자 1명)가 오픈 전까지 1~2초 간격으로 `GET /deals/{id}`, `OPEN_AT_EPOCH_MS`에 `POST /orders`(quantity 1) 1건. 요청 타임아웃 90초.
- 결과: `perf/results/<RUN_NAME>/k6-summary.json` (디렉터리는 미리 만들어 둔다. 토큰/비밀번호는 포함하지 않음).
- 환경변수: `BASE_URL`(기본 http://localhost:8080), `TOKENS_FILE`(기본 perf/.tokens.json, 스크립트 기준 상대경로), `RESULT_DIR`.

드라이런: `USER_COUNT=50`, 재고 5, `VIEW_SECONDS=15`, `RUN_NAME=dryrun-N`(`perf/results/dryrun-*`는 gitignore).

## 3. 한 번에 실행 (권장)
```
perf/scripts/run-baseline.sh <A|B> [runLabel]   # A: Mock PG 100ms, B: 1s
```
앱 재시작 -> 회원 준비 -> 워밍업(`perf/k6/warmup.js`) -> 새 딜 -> 오픈 시나리오 -> `collect-result.sh`(서버 drain 대기 후 집계) 순서.
결과: `perf/results/[label-]<yyyyMMdd-HHmmss>-<A|B>/result.json`(+ `phases.json`, `k6-summary.json`). 끝에 Grafana 대시보드 URL이 출력된다.
- k6 종료 후에도 서버는 타임아웃된 요청을 계속 처리하므로, 수집은 Hikari active 0, Tomcat busy 기준값 이하, 딜 주문 행 수 5초 불변이 될 때까지 기다린다(상한 A 180초 / B 600초, `DRAIN_CAP`로 변경). `drain.seconds`, `drain.timedOut`을 결과에 기록한다.
- 환경변수: `VIEW_SECONDS`(60), `OPEN_MARGIN_SECONDS`(15, k6 시작·VU 초기화 여유), `USER_COUNT`(1000).
- 필요: macOS(`date -r`, `sysctl`, `sw_vers` 사용), python3, jq, k6, docker compose, `.env`(관리자·DB·PERF_USER_PASSWORD). 리허설 산출물 `perf/results/rehearsal-*`는 gitignore.
- perf 스택을 올리면 mysql이 볼륨 없이 재생성되어 로컬 개발 데이터가 지워진다.
- Prometheus 설정을 바꿔 전환할 때는 `docker compose -f compose.yaml -f compose.perf.yaml rm -sfv prometheus`로 컨테이너와 익명 볼륨을 지워야 중복된 옛 시계열이 남지 않는다.
