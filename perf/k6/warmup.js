// 워밍업: 별도 딜에 소량 주문(기본 50건, 동시성 10). 사용자별 1건. 토큰은 출력하지 않는다.
import http from 'k6/http';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { API, JSON_HEADERS, envInt } from './lib/config.js';

const DEAL_ID = parseInt(__ENV.DEAL_ID, 10);
const COUNT = envInt('WARMUP_ORDERS', 50);
const TOKENS_FILE = __ENV.TOKENS_FILE || '../../perf/.tokens.json';
const users = new SharedArray('tokens', () => JSON.parse(open(TOKENS_FILE)).users.slice(0, COUNT));

export const options = {
  scenarios: { warmup: { executor: 'shared-iterations', vus: envInt('WARMUP_CONCURRENCY', 10), iterations: COUNT, maxDuration: '5m' } },
  summaryTrendStats: ['med', 'p(95)', 'max'],
};

export default function () {
  const u = users[exec.scenario.iterationInTest];
  const r = http.post(`${API}/orders`, JSON.stringify({ dealId: DEAL_ID, quantity: 1 }), {
    headers: Object.assign({ Authorization: `Bearer ${u.accessToken}` }, JSON_HEADERS), timeout: '60s', tags: { name: 'warmup_order' },
  });
  if (r.status !== 201) console.error(`warmup order status=${r.status}`);
}
