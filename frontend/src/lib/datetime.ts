import { floorDiv, mod } from "@/lib/money";

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

const KST_LOCAL = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d{1,6})?$/;

/** 백엔드 LocalDateTime(KST) 까지 남은 초. 소수 초는 버리고 +09:00 으로 해석하며, 지났거나 해석할 수 없으면 0. */
export function secondsUntilKst(kstLocalDateTime: string, nowMs: number): number {
  const m = KST_LOCAL.exec(kstLocalDateTime);
  if (!m) return 0;
  const target = Date.parse(`${m[1]}+09:00`);
  if (Number.isNaN(target)) return 0;
  return Math.max(floorDiv(target - nowMs, 1000), 0);
}

/** 초를 "MM:SS" 로(분은 60 을 넘어도 그대로). */
export function formatCountdown(seconds: number): string {
  const s = Math.max(seconds, 0);
  return `${pad(floorDiv(s, 60))}:${pad(mod(s, 60))}`;
}
