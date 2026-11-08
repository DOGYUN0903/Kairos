// 타임딜 오픈 시나리오: 오픈 전까지 딜 상세를 반복 조회하다가 OPEN_AT_EPOCH_MS에 사용자별 주문 1건.
// 사용(저장소 루트에서): DEAL_ID=.. OPEN_AT_EPOCH_MS=.. k6 run perf/k6/timedeal-open.js
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';
import { API, JSON_HEADERS, envInt } from './lib/config.js';

const DEAL_ID = parseInt(__ENV.DEAL_ID, 10);
const OPEN_AT = parseInt(__ENV.OPEN_AT_EPOCH_MS, 10);
const VIEW_SECONDS = envInt('VIEW_SECONDS', 60);
const RUN_NAME = __ENV.RUN_NAME || 'manual';
const RESULT_DIR = __ENV.RESULT_DIR || `perf/results/${RUN_NAME}`;
const TOKENS_FILE = __ENV.TOKENS_FILE || '../../perf/.tokens.json'; // open()은 스크립트 위치 기준
const USER_COUNT = envInt('USER_COUNT', 1000); // 사용 인원(토큰 파일 앞에서부터)
const TOKEN_MAX_AGE_MS = 25 * 60 * 1000; // JWT 액세스 TTL 30분보다 짧게

const tokenFile = JSON.parse(open(TOKENS_FILE));
const users = new SharedArray('users', () => tokenFile.users.slice(0, USER_COUNT));

export const options = {
  scenarios: {
    open: {
      executor: 'per-vu-iterations',
      vus: USER_COUNT,
      iterations: 1,
      maxDuration: `${VIEW_SECONDS + 240}s`,
    },
  },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max', 'avg'],
  noConnectionReuse: false,
};

const HTTP_CODES = [201, 400, 401, 403, 404, 409, 500, 502, 503, 504, 0];
const STATUSES = ['PAID', 'CANCELLED', 'NONE'];
const ERROR_CODES = ['INVALID_REQUEST', 'UNAUTHORIZED', 'FORBIDDEN', 'DEAL_NOT_FOUND', 'DEAL_NOT_ONGOING',
  'ORDER_LIMIT_EXCEEDED', 'STOCK_SOLD_OUT', 'INTERNAL_ERROR', 'NONE'];
const CANCEL_REASONS = ['PAYMENT_FAILED', 'PAYMENT_TIMEOUT', 'NONE'];

function counters(prefix, keys) {
  const m = {};
  keys.concat(['OTHER']).forEach((k) => { m[k] = new Counter(`${prefix}_${k}`); });
  return m;
}
const cHttp = counters('order_http', HTTP_CODES);
const cStatus = counters('order_status', STATUSES);
const cError = counters('order_errorcode', ERROR_CODES);
const cCancel = counters('order_cancelreason', CANCEL_REASONS);
function inc(group, keys, key) {
  group[keys.indexOf(key) >= 0 ? key : 'OTHER'].add(1);
}

const orderLatency = new Trend('order_latency', true);
const orderSendOffset = new Trend('order_send_offset_ms'); // 실제 전송 시각 - OPEN_AT (ms)
const viewLatency = new Trend('view_latency', true);
const viewCount = new Counter('view_requests');

export function setup() {
  const now = Date.now();
  if (!DEAL_ID || !OPEN_AT) throw new Error('DEAL_ID, OPEN_AT_EPOCH_MS env 필요');
  if (OPEN_AT - now < 3000) throw new Error(`OPEN_AT까지 ${OPEN_AT - now}ms: 너무 가깝거나 이미 지남`);
  if (now - tokenFile.issuedAtMs > TOKEN_MAX_AGE_MS) throw new Error('토큰이 25분 넘게 지남: setup-users를 다시 실행');
  if (tokenFile.users.length < USER_COUNT) throw new Error(`토큰 ${tokenFile.users.length}개 < USER_COUNT ${USER_COUNT}`);
  return { startedAtMs: now };
}

export default function () {
  const user = users[exec.vu.idInTest - 1];
  const headers = { Authorization: `Bearer ${user.accessToken}` };
  const getParams = { headers, timeout: '30s', tags: { name: 'deal_detail' } };

  // 시작 시 몰림 방지: 최대 1초 지터 후 첫 조회(연결도 이때 맺는다)
  sleep(Math.random());
  while (true) {
    const remaining = OPEN_AT - Date.now();
    if (remaining <= 1200) break;
    const r = http.get(`${API}/deals/${DEAL_ID}`, getParams);
    viewCount.add(1);
    viewLatency.add(r.timings.duration);
    const pause = Math.min(1000 + Math.random() * 1000, Math.max(0, OPEN_AT - Date.now() - 1100));
    sleep(pause / 1000);
  }

  // 오픈 시각까지 대기(남은 시간 전체를 한 번에 sleep, 마지막 수 ms는 짧은 sleep으로 보정)
  let wait = OPEN_AT - Date.now();
  if (wait > 5) sleep((wait - 2) / 1000);
  while (Date.now() < OPEN_AT) sleep(0.001);

  const sentAt = Date.now();
  orderSendOffset.add(sentAt - OPEN_AT);
  const res = http.post(`${API}/orders`, JSON.stringify({ dealId: DEAL_ID, quantity: 1 }), {
    headers: Object.assign({}, JSON_HEADERS, headers),
    timeout: '90s',
    tags: { name: 'order' },
  });
  orderLatency.add(res.timings.duration);

  inc(cHttp, HTTP_CODES, res.status);
  let status = 'NONE';
  let errorCode = 'NONE';
  let cancel = 'NONE';
  try {
    const body = res.json();
    if (body && body.data && body.data.status) status = body.data.status;
    if (body && body.data && body.data.cancelReason) cancel = body.data.cancelReason;
    if (body && body.code) errorCode = body.code;
  } catch (e) { /* 본문 없음/비JSON(타임아웃, 연결 오류) */ }
  inc(cStatus, STATUSES, status);
  inc(cError, ERROR_CODES, errorCode);
  inc(cCancel, CANCEL_REASONS, cancel);
}

function cnt(m, name) {
  const x = m[name];
  return x ? x.values.count : 0;
}
function group(m, prefix, keys) {
  const out = {};
  keys.concat(['OTHER']).forEach((k) => { const c = cnt(m, `${prefix}_${k}`); if (c > 0) out[k] = c; });
  return out;
}
function trend(m, name) {
  const v = m[name] && m[name].values;
  if (!v) return null;
  return { min: v.min, p50: v.med, p95: v['p(95)'], p99: v['p(99)'], max: v.max, avg: v.avg };
}

export function handleSummary(data) {
  const m = data.metrics;
  const offs = m.order_send_offset_ms ? m.order_send_offset_ms.values : null;
  const summary = {
    run: RUN_NAME,
    dealId: DEAL_ID,
    openAtEpochMs: OPEN_AT,
    userCount: USER_COUNT,
    viewSeconds: VIEW_SECONDS,
    finishedAtEpochMs: Date.now(),
    testDurationMs: data.state.testRunDurationMs,
    orderRequests: Object.values(group(m, 'order_http', HTTP_CODES)).reduce((a, b) => a + b, 0),
    orderHttpStatusCounts: group(m, 'order_http', HTTP_CODES),
    orderResponseStatusCounts: group(m, 'order_status', STATUSES), // PAID / CANCELLED / NONE(상태 없음=에러·타임아웃)
    orderErrorCodeCounts: group(m, 'order_errorcode', ERROR_CODES),
    orderCancelReasonCounts: group(m, 'order_cancelreason', CANCEL_REASONS),
    orderLatencyMs: trend(m, 'order_latency'),
    // 주문 요청 시작 시각의 OPEN_AT 대비 오프셋(ms). 도착 폭 = 마지막 - 첫 요청 시작
    orderSendOffsetMs: offs ? { first: offs.min, last: offs.max, spread: offs.max - offs.min, p50: offs.med, p95: offs['p(95)'] } : null,
    viewRequests: cnt(m, 'view_requests'),
    viewLatencyMs: trend(m, 'view_latency'),
    // per-vu-iterations 실행기에서는 dropped_iterations 메트릭이 없을 수 있다(없으면 0)
    droppedIterations: cnt(m, 'dropped_iterations'),
    iterationsCompleted: cnt(m, 'iterations'),
    httpReqFailed: m.http_req_failed ? { rate: m.http_req_failed.values.rate, failed: m.http_req_failed.values.passes } : null,
    interruptedIterations: cnt(m, 'iterations') < USER_COUNT ? USER_COUNT - cnt(m, 'iterations') : 0,
  };
  const text = `orders=${summary.orderRequests} http=${JSON.stringify(summary.orderHttpStatusCounts)} status=${JSON.stringify(summary.orderResponseStatusCounts)}\n` +
    `errorCodes=${JSON.stringify(summary.orderErrorCodeCounts)} latency=${JSON.stringify(summary.orderLatencyMs)}\n` +
    `sendOffset=${JSON.stringify(summary.orderSendOffsetMs)} dropped=${summary.droppedIterations}\n`;
  const out = { stdout: text };
  out[`${RESULT_DIR}/k6-summary.json`] = JSON.stringify(summary, null, 2);
  return out;
}
