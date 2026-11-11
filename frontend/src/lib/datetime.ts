// 백엔드 LocalDateTime(KST, 오프셋 없음)은 Date 로 파싱하지 않고 문자열로만 다룬다.
// DATETIME(6) 이라 소수 초가 붙을 수 있다: 2026-11-07T14:20:00.123456
const LOCAL_DATE_TIME = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d{1,9})?)?$/;
const LOCAL_DATE = /^(\d{4}-\d{2}-\d{2})(?:T.*)?$/;

export function formatDateTime(s: string): string {
  const m = LOCAL_DATE_TIME.exec(s);
  if (!m) return s;
  return `${m[1]} ${m[2]}`;
}

export function formatDate(s: string): string {
  const m = LOCAL_DATE.exec(s);
  return m?.[1] ?? s;
}

const pad = (n: number, width = 2) => String(n).padStart(width, "0");

export function toLocalDateTimeString(d: { y: number; m: number; d: number; hh: number; mm: number }): string {
  return `${pad(d.y, 4)}-${pad(d.m)}-${pad(d.d)}T${pad(d.hh)}:${pad(d.mm)}:00`;
}
