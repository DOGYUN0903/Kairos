// 공통 설정. 모든 값은 환경변수(-e KEY=VALUE 또는 셸 env)로 덮어쓴다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const API = `${BASE_URL}/api/v1`;
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function userEmail(i) {
  return `perf-user-${String(i).padStart(4, '0')}@kairos.perf`;
}

export function envInt(name, dflt) {
  const v = __ENV[name];
  return v === undefined || v === '' ? dflt : parseInt(v, 10);
}
