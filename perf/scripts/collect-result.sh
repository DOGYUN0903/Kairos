#!/usr/bin/env bash
# 사용: perf/scripts/collect-result.sh <runDir> <dealId> <startEpochSec> <k6EndEpochSec>
# 서버가 비워질(drain) 때까지 기다린 뒤 DB·Prometheus·k6 요약을 모아 <runDir>/result.json 을 쓴다.
# <runDir>/phases.json(run-baseline.sh가 기록)과 <runDir>/k6-summary.json 이 있어야 한다.
source "$(dirname "$0")/lib.sh"

RUN_DIR="$1"; DEAL="$2"; START="$3"; K6_END="$4"
PH="$RUN_DIR/phases.json"; K6S="$RUN_DIR/k6-summary.json"
[ -f "$PH" ] && [ -f "$K6S" ] || { echo "phases.json 또는 k6-summary.json 없음" >&2; exit 1; }
COND="$(jq -r .condition "$PH")"
BASE="$(jq -r .tomcatBusyBaseline "$PH")"
DRAIN_CAP="${DRAIN_CAP:-$([ "$COND" = B ] && echo 600 || echo 180)}"

log "drain 대기 (cap ${DRAIN_CAP}s, tomcat baseline ${BASE})"
DRAIN="$(wait_drain "$DEAL" "$DRAIN_CAP" "$BASE")"
log "drain: $DRAIN"
sleep 3 # 마지막 샘플이 Prometheus에 반영되도록
QUERY_END="$(( $(date +%s) ))"
WIN=$(( QUERY_END - START + 1 ))

# --- DB ---
read -r PAID_QTY PAID_ORDERS CANCELLED ROWS < <(db_query "SELECT COALESCE(SUM(CASE WHEN order_status='PAID' THEN quantity END),0), SUM(order_status='PAID'), SUM(order_status='CANCELLED'), COUNT(*) FROM orders WHERE deal_id=$DEAL" | awk '{print $1, $2+0, $3+0, $4}')
BY_STATUS="$(db_query "SELECT order_status, COALESCE(cancel_reason,'-'), COUNT(*) FROM orders WHERE deal_id=$DEAL GROUP BY 1,2" |
  jq -R -s -c 'split("\n") | map(select(length>0) | split("\t") | {status:.[0], cancelReason:.[1], count:(.[2]|tonumber)})')"
read -r REMAINING TOTAL < <(db_query "SELECT remaining_quantity, total_quantity FROM stock WHERE deal_id=$DEAL")

# --- Prometheus (실행 구간 [START, QUERY_END], 새 dealId 시리즈만) ---
M_SOLD="$(prom_scalar "kairos_deal_sold{dealId=\"$DEAL\"}" "$QUERY_END")"
M_CAP="$(prom_scalar "kairos_deal_capacity{dealId=\"$DEAL\"}" "$QUERY_END")"
M_OVER="$(prom_scalar "kairos_deal_oversold{dealId=\"$DEAL\"}" "$QUERY_END")"
# increase()는 외삽으로 소수가 나오므로 구간 끝 값 - 시작 값으로 정확히 계산(카운터는 앱 재시작 후 0부터, 워밍업분은 시작 값에 포함)
PLACED_END="$(prom_map 'sum by (result)(kairos_order_placed_total)' result "$QUERY_END")"
PLACED_START="$(prom_map 'sum by (result)(kairos_order_placed_total)' result "$START")"
PLACED="$(jq -n -c --argjson e "$PLACED_END" --argjson s "$PLACED_START" '$e | with_entries(.value -= ($s[.key] // 0))')"
PEND_MAX="$(prom_scalar "max(max_over_time(hikaricp_connections_pending[${WIN}s]))" "$QUERY_END")"
ACT_MAX="$(prom_scalar "max(max_over_time(hikaricp_connections_active[${WIN}s]))" "$QUERY_END")"
BUSY_MAX="$(prom_scalar "max(max_over_time(tomcat_threads_busy_threads[${WIN}s]))" "$QUERY_END")"
CPU_MAX="$(prom_scalar "max(max_over_time(process_cpu_usage[${WIN}s]))" "$QUERY_END")"
HIKARI_MAX="$(prom_scalar 'max(hikaricp_connections_max)' "$QUERY_END")"
TOMCAT_MAX="$(prom_scalar 'max(tomcat_threads_config_max_threads)' "$QUERY_END")"
PAY_P95="$(prom_scalar "histogram_quantile(0.95, sum by (le)(increase(kairos_payment_duration_seconds_bucket[${WIN}s])))" "$QUERY_END")"
PAY_P95_BY="$(prom_map "histogram_quantile(0.95, sum by (le, result)(increase(kairos_payment_duration_seconds_bucket[${WIN}s])))" result "$QUERY_END")"

# --- 환경 ---
cinfo() { docker inspect "kairos-api-$1-1" --format '{"cpus":{{.HostConfig.NanoCpus}},"memBytes":{{.HostConfig.Memory}}}' 2>/dev/null | jq -c '{cpus:(.cpus/1e9), memMB:(.memBytes/1048576)}'; }
GIT_COMMIT="$(git rev-parse --short HEAD)"; GIT_DIRTY="$([ -n "$(git status --porcelain -- src build.gradle.kts compose.yaml compose.perf.yaml Dockerfile)" ] && echo true || echo false)"

jq -n \
  --slurpfile ph "$PH" --slurpfile k6 "$K6S" \
  --arg deal "$DEAL" --arg cond "$COND" \
  --argjson start "$START" --argjson k6End "$K6_END" --argjson qEnd "$QUERY_END" --argjson drain "$DRAIN" \
  --argjson paidQty "$PAID_QTY" --argjson paidOrders "$PAID_ORDERS" --argjson cancelled "$CANCELLED" --argjson rows "$ROWS" \
  --argjson byStatus "$BY_STATUS" --argjson remaining "$REMAINING" --argjson total "$TOTAL" \
  --arg mSold "$M_SOLD" --arg mCap "$M_CAP" --arg mOver "$M_OVER" --argjson placed "$PLACED" \
  --arg pend "$PEND_MAX" --arg act "$ACT_MAX" --arg busy "$BUSY_MAX" --arg cpu "$CPU_MAX" \
  --arg hikari "$HIKARI_MAX" --arg tomcat "$TOMCAT_MAX" --arg pay "$PAY_P95" --argjson payBy "$PAY_P95_BY" \
  --argjson appRes "$(cinfo app)" --argjson dbRes "$(cinfo mysql)" --argjson redisRes "$(cinfo redis)" \
  --arg k6v "$(k6 version | head -1)" --arg commit "$GIT_COMMIT" --argjson dirty "$GIT_DIRTY" \
  --arg hostCpu "$(sysctl -n hw.ncpu)" --arg hostMem "$(sysctl -n hw.memsize)" --arg hostOs "$(sw_vers -productVersion)" \
  '
  def num: if . == "null" then null else tonumber end;
  ($ph[0]) as $p | ($k6[0]) as $k |
  ($paidQty) as $paid | ($mSold|num) as $sold |
  {
    runLabel: $p.runLabel, condition: $cond, dealId: ($deal|tonumber),
    generatedAtEpochSec: now|floor,
    environment: {
      mockPgDelay: $p.mockPgDelay, mockPgFailureRate: $p.mockPgFailureRate, mockPgTimeout: $p.mockPgTimeout,
      resources: {app: $appRes, mysql: $dbRes, redis: $redisRes},
      hikariMaxPoolSize: ($hikari|num), tomcatMaxThreads: ($tomcat|num),
      host: {cpus: ($hostCpu|tonumber), memBytes: ($hostMem|tonumber), macOS: $hostOs},
      k6Version: $k6v, gitCommit: $commit, gitDirtyInAppSources: $dirty,
      viewSecondsConfigured: $p.viewSeconds, userCount: $p.userCount, stock: $p.stock, perMemberLimit: $p.perMemberLimit
    },
    phases: ($p.phasesEpochMs + {
      k6FirstViewAtEpochMs: $k.viewActual.firstViewAtEpochMs, k6OpenAtEpochMs: $k.openAtEpochMs
    } | . + {
      setupFinishedBeforeFirstView: (.usersSetupEndEpochMs < .k6FirstViewAtEpochMs),
      warmupFinishedBeforeFirstView: (.warmupEndEpochMs < .k6FirstViewAtEpochMs),
      setupToFirstViewSeconds: ((.k6FirstViewAtEpochMs - .usersSetupEndEpochMs) / 1000)
    }),
    window: {startEpochSec: $start, k6EndEpochSec: $k6End, queryEndEpochSec: $qEnd, windowSeconds: ($qEnd - $start + 1)},
    drain: $drain,
    db: {
      paidQuantity: $paid, paidOrders: $paidOrders, cancelledOrders: $cancelled, orderRows: $rows,
      byStatusAndReason: $byStatus, stockRemaining: $remaining, stockTotal: $total
    },
    metrics: {
      dealSold: $sold, dealCapacity: ($mCap|num), dealOversold: ($mOver|num),
      orderPlacedByResult: $placed,
      hikariPendingMax: ($pend|num), hikariActiveMax: ($act|num), tomcatBusyThreadsMax: ($busy|num),
      appProcessCpuUsageMax: ($cpu|num), paymentP95Seconds: ($pay|num), paymentP95SecondsByResult: $payBy
    },
    derived: {
      oversoldUnits: ([0, ($paid - $total)] | max),
      soldMatches: ($sold != null and $paid == $sold),
      lostUpdateGap: ($paid - ($total - $remaining))
    },
    k6: $k
  }' > "$RUN_DIR/result.json"
log "result: $RUN_DIR/result.json"
