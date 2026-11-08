// 측정용 회원 준비: 가입(409 무시) 후 로그인, 액세스 토큰을 파일에 저장한다.
// 측정 구간 밖에서 실행한다(BCrypt가 무거우므로 CONCURRENCY로 동시성을 제한).
// 사용: PASSWORD=... USER_COUNT=1000 k6 run perf/k6/setup-users.js   (저장소 루트에서)
import http from 'k6/http';
import { API, JSON_HEADERS, userEmail, envInt } from './lib/config.js';

const USER_COUNT = envInt('USER_COUNT', 1000);
const CONCURRENCY = envInt('CONCURRENCY', 30);
const PASSWORD = __ENV.PASSWORD;
const TOKENS_OUT = __ENV.TOKENS_OUT || 'perf/.tokens.json';

export const options = {
  vus: 1,
  iterations: 1,
  setupTimeout: '30m',
  // 출력에 토큰이 섞이지 않도록 기본 요약 통계만 사용
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
};

function post(path, body) {
  return { method: 'POST', url: `${API}${path}`, body: JSON.stringify(body), params: { headers: JSON_HEADERS, timeout: '60s' } };
}

export function setup() {
  if (!PASSWORD || PASSWORD.length < 8) throw new Error('PASSWORD env (8자 이상) 필요');
  const users = [];
  let signupOk = 0;
  let signupDup = 0;
  for (let start = 1; start <= USER_COUNT; start += CONCURRENCY) {
    const idx = [];
    for (let i = start; i < start + CONCURRENCY && i <= USER_COUNT; i++) idx.push(i);

    const signups = http.batch(idx.map((i) => post('/auth/signup', { email: userEmail(i), password: PASSWORD, name: `perf-user-${i}` })));
    signups.forEach((r, k) => {
      if (r.status === 201) signupOk++;
      else if (r.status === 409) signupDup++;
      else throw new Error(`signup 실패 index=${idx[k]} status=${r.status}`);
    });

    const logins = http.batch(idx.map((i) => post('/auth/login', { email: userEmail(i), password: PASSWORD })));
    logins.forEach((r, k) => {
      if (r.status !== 200) throw new Error(`login 실패 index=${idx[k]} status=${r.status}`);
      users.push({ email: userEmail(idx[k]), accessToken: r.json('data.accessToken') });
    });
  }
  return { issuedAtMs: Date.now(), count: users.length, signupOk, signupDup, users };
}

export default function () {}

// 토큰 파일은 handleSummary로만 쓴다(k6에는 파일 쓰기 API가 없다). 표준출력에는 토큰을 찍지 않는다.
export function handleSummary(data) {
  const s = data.setup_data;
  const out = {
    stdout: `users=${s.count} signupCreated=${s.signupOk} signupAlreadyExisted=${s.signupDup} -> ${TOKENS_OUT}\n`,
  };
  out[TOKENS_OUT] = JSON.stringify({ issuedAtMs: s.issuedAtMs, count: s.count, users: s.users });
  return out;
}
