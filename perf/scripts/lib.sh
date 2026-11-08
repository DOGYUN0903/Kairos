#!/usr/bin/env bash
# 공통 함수. 저장소 루트 기준으로 source 한다. 비밀값(.env)은 출력하지 않는다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
set -a; . "$ROOT/.env"; set +a
export MYSQL_PWD="${DB_PASSWORD}" # docker compose exec -e MYSQL_PWD 로 값 없이 전달(명령행에 노출 안 됨)

COMPOSE=(docker compose -f compose.yaml -f compose.perf.yaml)
API_BASE="${BASE_URL:-http://localhost:8080}/api/v1"
MGMT_URL="${MGMT_URL:-http://localhost:8081}"
PROM_URL="${PROM_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
log() { echo "[$(date +%H:%M:%S)] $*" >&2; }

# KST LocalDateTime 문자열 (epoch 초 -> yyyy-MM-ddTHH:mm:ss)
kst_ldt() { TZ=Asia/Seoul date -r "$1" +%Y-%m-%dT%H:%M:%S; }

# 관리자 로그인 -> 액세스 토큰(표준출력). 비밀번호는 환경에서 jq로 읽어 stdin 으로만 전달한다.
admin_token() {
  jq -n '{email: env.KAIROS_ADMIN_EMAIL, password: env.KAIROS_ADMIN_PASSWORD}' |
    curl -sS -f -X POST "$API_BASE/auth/login" -H 'Content-Type: application/json' --data @- |
    jq -r '.data.accessToken'
}

# create_deal <상품명> <재고> <perMemberLimit> <openAtLDT> <closeAtLDT> -> dealId
create_deal() {
  local hdr body
  hdr="$(mktemp)"; chmod 600 "$hdr"
  printf 'Authorization: Bearer %s\n' "$(admin_token)" > "$hdr"
  body="$(jq -n --arg n "$1" --argjson q "$2" --argjson l "$3" --arg o "$4" --arg c "$5" \
    '{productName:$n, originalPrice:100000, dealPrice:50000, totalQuantity:$q, perMemberLimit:$l, openAt:$o, closeAt:$c}')"
  local resp
  resp="$(curl -sS -X POST "$API_BASE/admin/deals" -H @"$hdr" -H 'Content-Type: application/json' -d "$body")"
  rm -f "$hdr"
  echo "$resp" | jq -er '.data.dealId' || { log "딜 생성 실패: $(echo "$resp" | jq -c '{code,message}')"; return 1; }
}

# DB 조회(탭 구분, 헤더 없음)
db_query() {
  "${COMPOSE[@]}" exec -T -e MYSQL_PWD mysql mysql -u"$DB_USERNAME" -N -B "$DB_NAME" -e "$1"
}

# prom_scalar <promql> [evalTimeEpochSec] -> 첫 결과 값(없으면 null)
prom_scalar() {
  local args=(--data-urlencode "query=$1")
  [ -n "${2:-}" ] && args+=(--data-urlencode "time=$2")
  curl -sS "$PROM_URL/api/v1/query" "${args[@]}" | jq -r '(.data.result[0].value[1] // "null")'
}

# prom_map <promql> <labelName> [evalTime] -> {label값: 숫자} JSON
prom_map() {
  local args=(--data-urlencode "query=$1")
  [ -n "${3:-}" ] && args+=(--data-urlencode "time=$3")
  curl -sS "$PROM_URL/api/v1/query" "${args[@]}" |
    jq -c --arg l "$2" '[.data.result[] | {(.metric[$l] // "none"): (.value[1] | tonumber)}] | add // {}'
}

wait_health() {
  local i
  for i in $(seq 1 120); do
    if curl -sf "$MGMT_URL/actuator/health" 2>/dev/null | grep -q '"UP"'; then return 0; fi
    sleep 2
  done
  log "앱 health UP 대기 시간 초과"; return 1
}

# wait_drain <dealId> <capSeconds> <tomcatBaseline>
# 조건: hikari active == 0 && tomcat busy <= baseline && 딜 주문 행 수가 5초 연속 불변.
# 결과(JSON, 표준출력): {seconds, timedOut, cap, polls, finalOrderRows}
wait_drain() {
  local deal="$1" cap="$2" base="$3" t0 stable=0 last=-1 act busy rows elapsed=0 timed_out=false
  t0=$(date +%s)
  while :; do
    act="$(prom_scalar 'sum(hikaricp_connections_active)')"
    busy="$(prom_scalar 'max(tomcat_threads_busy_threads)')"
    rows="$(db_query "SELECT COUNT(*) FROM orders WHERE deal_id=$deal")"
    if [ "$act" != "null" ] && [ "$busy" != "null" ] &&
       awk -v a="$act" -v b="$busy" -v base="$base" 'BEGIN{exit !(a==0 && b<=base)}' &&
       [ "$rows" = "$last" ]; then
      stable=$((stable + 1))
    else
      stable=0
    fi
    last="$rows"
    elapsed=$(( $(date +%s) - t0 ))
    [ "$stable" -ge 5 ] && break
    if [ "$elapsed" -ge "$cap" ]; then timed_out=true; break; fi
    sleep 1
  done
  local secs=$elapsed
  [ "$timed_out" = false ] && secs=$((elapsed - 5)) && [ "$secs" -lt 0 ] && secs=0
  jq -n --argjson s "$secs" --argjson t "$timed_out" --argjson c "$cap" --argjson r "$last" \
    '{seconds:$s, timedOut:$t, cap:$c, finalOrderRows:$r}'
}
