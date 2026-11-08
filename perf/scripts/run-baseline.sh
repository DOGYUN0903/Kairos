#!/usr/bin/env bash
# 사용: perf/scripts/run-baseline.sh <A|B> [runLabel]
# 조건 A: Mock PG 100ms, 조건 B: Mock PG 1s. 앱만 재시작 -> 회원 준비 -> 워밍업 -> 새 딜 -> 오픈 시나리오 -> 수집.
# 환경변수(선택): VIEW_SECONDS(기본 60), OPEN_MARGIN_SECONDS(기본 15, k6 시작+VU 초기화 여유), USER_COUNT(1000), DRAIN_CAP(A 180 / B 600)
source "$(dirname "$0")/lib.sh"

COND="${1:?조건 A 또는 B}"; LABEL="${2:-}"
case "$COND" in
  A) PG_DELAY=100ms ;;
  B) PG_DELAY=1s ;;
  *) echo "조건은 A 또는 B" >&2; exit 1 ;;
esac
VIEW_SECONDS="${VIEW_SECONDS:-60}"; MARGIN="${OPEN_MARGIN_SECONDS:-15}"; USER_COUNT="${USER_COUNT:-1000}"
STOCK=100; LIMIT=1; PG_FAIL=0.0; PG_TIMEOUT=3s
# k6 주문 타임아웃은 90s 고정(HikariCP 30s보다 길게). 시나리오 maxDuration = VIEW_SECONDS+240s 이므로
# OPEN_AT 까지(VIEW+MARGIN) + 90s 가 항상 그 안에 들어온다. 서버 drain은 k6 종료 후 collect-result.sh가 따로 기다린다.

TS="$(date +%Y%m%d-%H%M%S)"
RUN_NAME="${LABEL:+$LABEL-}$TS-$COND"
RUN_DIR="perf/results/$RUN_NAME"
mkdir -p "$RUN_DIR"
command -v jq >/dev/null && command -v k6 >/dev/null || { echo "jq, k6 필요" >&2; exit 1; }

# 1. 앱만 재생성(조건의 PG 지연 적용)
log "1. 앱 재시작 (PG 지연 $PG_DELAY)"
APP_RESTART_MS="$(now_ms)"
PERF_MOCK_PG_DELAY="$PG_DELAY" PERF_MOCK_PG_FAILURE_RATE="$PG_FAIL" PERF_MOCK_PG_TIMEOUT="$PG_TIMEOUT" \
  "${COMPOSE[@]}" up -d --force-recreate --no-deps app >/dev/null 2>&1
wait_health
HEALTHY_MS="$(now_ms)"

# 2. 회원 준비 (가입/로그인, 측정 구간 밖). 토큰은 워밍업과 측정에 공통 사용(TTL 30분, 25분 가드)
log "2. 회원 준비 ($USER_COUNT 명)"
SETUP_START_MS="$(now_ms)"
PASSWORD="$PERF_USER_PASSWORD" USER_COUNT="$USER_COUNT" CONCURRENCY=30 k6 run --quiet perf/k6/setup-users.js >&2
SETUP_END_MS="$(now_ms)"

# 3. 워밍업: 별도 딜(오픈은 과거, 재고 1000), 소량 주문 50건 동시성 10
log "3. 워밍업"
WARM_START_MS="$(now_ms)"
NOW_S="$(date +%s)"
WARM_DEAL="$(create_deal "perf-warmup-$TS" 1000 1 "$(kst_ldt $((NOW_S + 4)))" "$(kst_ldt $((NOW_S + 7200)))")"
sleep 6 # 워밍업 딜 오픈 대기
DEAL_ID="$WARM_DEAL" WARMUP_ORDERS=50 WARMUP_CONCURRENCY=10 k6 run --quiet perf/k6/warmup.js >&2
WARM_BASE_DRAIN="$(wait_drain "$WARM_DEAL" 60 100000)" # 워밍업 주문이 끝날 때까지(활성 연결 0, 행 수 안정)
# 유휴 상태의 tomcat busy 스레드 기준값 (이후 drain 판정용)
sleep 3
TOMCAT_BASE="$(prom_scalar 'max(tomcat_threads_busy_threads)')"
WARM_END_MS="$(now_ms)"
log "   워밍업 딜 $WARM_DEAL, tomcat busy baseline=$TOMCAT_BASE"

# 4. 측정 딜: 재고 100, 1인 1개, openAt = 지금 + VIEW_SECONDS + MARGIN (정각 초)
NOW_S="$(date +%s)"
OPEN_S=$((NOW_S + VIEW_SECONDS + MARGIN))
DEAL_ID="$(create_deal "perf-timedeal-$RUN_NAME" "$STOCK" "$LIMIT" "$(kst_ldt "$OPEN_S")" "$(kst_ldt $((OPEN_S + 3600)))")"
DEAL_CREATED_MS="$(now_ms)"
log "4. 측정 딜 $DEAL_ID openAt=$(kst_ldt "$OPEN_S") KST"

# 5. 오픈 시나리오
START_S="$(date +%s)"
K6_START_MS="$(now_ms)"
DEAL_ID="$DEAL_ID" OPEN_AT_EPOCH_MS=$((OPEN_S * 1000)) VIEW_SECONDS="$VIEW_SECONDS" USER_COUNT="$USER_COUNT" \
  RUN_NAME="$RUN_NAME" RESULT_DIR="$RUN_DIR" k6 run --quiet perf/k6/timedeal-open.js >&2
K6_END_MS="$(now_ms)"
K6_END_S="$(( (K6_END_MS + 999) / 1000 + 5 ))"

jq -n --arg label "$LABEL" --arg cond "$COND" --arg d "$PG_DELAY" --arg f "$PG_FAIL" --arg t "$PG_TIMEOUT" \
  --argjson base "${TOMCAT_BASE/null/0}" --argjson vs "$VIEW_SECONDS" --argjson uc "$USER_COUNT" \
  --argjson stock "$STOCK" --argjson lim "$LIMIT" --argjson warm "$WARM_DEAL" \
  --argjson a "$APP_RESTART_MS" --argjson b "$HEALTHY_MS" --argjson c "$SETUP_START_MS" --argjson e "$SETUP_END_MS" \
  --argjson g "$WARM_START_MS" --argjson h "$WARM_END_MS" --argjson i "$DEAL_CREATED_MS" --argjson j "$K6_START_MS" --argjson k "$K6_END_MS" \
  '{runLabel:$label, condition:$cond, mockPgDelay:$d, mockPgFailureRate:$f, mockPgTimeout:$t, tomcatBusyBaseline:$base,
    viewSeconds:$vs, userCount:$uc, stock:$stock, perMemberLimit:$lim, warmupDealId:$warm,
    phasesEpochMs:{appRestartEpochMs:$a, appHealthyEpochMs:$b, usersSetupStartEpochMs:$c, usersSetupEndEpochMs:$e,
      warmupStartEpochMs:$g, warmupEndEpochMs:$h, dealCreatedEpochMs:$i, k6StartEpochMs:$j, k6EndEpochMs:$k}}' > "$RUN_DIR/phases.json"

# 6. 수집 (drain 대기 포함)
log "6. 결과 수집"
perf/scripts/collect-result.sh "$RUN_DIR" "$DEAL_ID" "$START_S" "$K6_END_S"

# 7. Grafana
QEND="$(jq -r .window.queryEndEpochSec "$RUN_DIR/result.json")"
echo "Grafana: $GRAFANA_URL/d/kairos-timedeal?orgId=1&from=$(( (START_S - 5) * 1000 ))&to=$(( (QEND + 5) * 1000 ))&var-dealId=$DEAL_ID"
echo "결과: $RUN_DIR/result.json"
