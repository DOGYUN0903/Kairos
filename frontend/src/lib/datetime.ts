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

// LOCAL_DATE_TIME 과 같은 범위를 받는다: 초는 생략될 수 있고(초·소수 초가 0), 소수 초는 1~9자리다.
const KST_LOCAL = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})(?::(\d{2})(?:\.\d{1,9})?)?$/;

/** 백엔드 LocalDateTime(KST) 까지 남은 초. 소수 초는 버리고 +09:00 으로 해석하며, 지났거나 해석할 수 없으면 0. */
export function secondsUntilKst(kstLocalDateTime: string, nowMs: number): number {
  const m = KST_LOCAL.exec(kstLocalDateTime);
  if (!m) return 0;
  const target = Date.parse(`${m[1]}:${m[2] ?? "00"}+09:00`);
  if (Number.isNaN(target)) return 0;
  return Math.max(floorDiv(target - nowMs, 1000), 0);
}

/** 초를 "MM:SS" 로(분은 60 을 넘어도 그대로). */
export function formatCountdown(seconds: number): string {
  const s = Math.max(seconds, 0);
  return `${pad(floorDiv(s, 60))}:${pad(mod(s, 60))}`;
}

/** 해당 KST 시각이 이미 지났는지(남은 시간 0 이하). */
export function hasPassedKst(kstLocalDateTime: string, nowMs: number): boolean {
  return secondsUntilKst(kstLocalDateTime, nowMs) <= 0;
}

const DATETIME_INPUT = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})$/;

/** `<input type="datetime-local">` 값을 서버 KST LocalDateTime 문자열로. 형식이 아니면 null. */
export function fromDateTimeInput(value: string): string | null {
  const m = DATETIME_INPUT.exec(value);
  return m ? `${m[1]}:00` : null;
}

/** "2026-11-12T10:05:30" → "11월 12일 10:05" (문자열만 다룬다). */
export function formatDateTimeMinute(s: string): string {
  const m = /^\d{4}-(\d{2})-(\d{2})T(\d{2}:\d{2})/.exec(s);
  if (!m) return s;
  return `${Number(m[1])}월 ${Number(m[2])}일 ${m[3]}`;
}

/** formatDateTimeMinute 에 연도를 더한다: 현재(KST) 연도가 아니면 "2025년 12월 30일 09:00". */
export function formatDateTimeMinuteWithYear(s: string, nowMs: number): string {
  const m = /^(\d{4})-/.exec(s);
  if (!m) return s;
  const currentYear = new Date(nowMs + KST_OFFSET_MS).getUTCFullYear();
  const base = formatDateTimeMinute(s);
  return Number(m[1]) === currentYear ? base : `${m[1]}년 ${base}`;
}

/** "2026-01-05T00:00:00" → "1월 5일". */
export function formatMonthDay(s: string): string {
  const m = /^\d{4}-(\d{2})-(\d{2})/.exec(s);
  if (!m) return s;
  return `${Number(m[1])}월 ${Number(m[2])}일`;
}

const DAY_MS = 86_400_000;
const KST_OFFSET_MS = 9 * 3_600_000;
const YMD = /^(\d{4})-(\d{2})-(\d{2})$/;

/** "YYYY-MM-DD" → 1970-01-01 부터 센 일수. 달력에 없는 날짜·형식 오류는 null. 실행 환경 시간대와 무관(UTC 로만 계산). */
function dayNumber(date: string): number | null {
  const m = YMD.exec(date);
  if (!m) return null;
  const ms = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  if (Number.isNaN(ms)) return null;
  const days = floorDiv(ms, DAY_MS);
  return dayToString(days) === date ? days : null;
}

function dayToString(days: number): string {
  return new Date(days * DAY_MS).toISOString().slice(0, 10);
}

/** 1970-01-01(목) 기준 요일. 월요일 = 0. */
function weekdayIndex(days: number): number {
  return mod(days + 3, 7);
}

/** KST 기준 가장 최근에 끝난 월~일 기간의 월요일("YYYY-MM-DD"). */
export function lastFinishedWeekMonday(nowMs: number): string {
  const today = floorDiv(nowMs + KST_OFFSET_MS, DAY_MS);
  return dayToString(today - weekdayIndex(today) - 7);
}

/** "YYYY-MM-DD" 가 달력에 있는 월요일인지. */
export function isMonday(date: string): boolean {
  const days = dayNumber(date);
  return days !== null && weekdayIndex(days) === 0;
}

/** 그 월요일 + 7일 00:00 KST 가 지났는지(해석할 수 없는 날짜는 false). */
export function isWeekFinished(monday: string, nowMs: number): boolean {
  const days = dayNumber(monday);
  if (days === null) return false;
  return (days + 7) * DAY_MS - KST_OFFSET_MS <= nowMs;
}

/** 서버의 periodEnd(다음 월요일, 미포함)에서 화면에 보일 마지막 날(하루 전)을 구한다. */
export function periodLastDay(periodEnd: string): string {
  const days = dayNumber(periodEnd);
  return days === null ? periodEnd : dayToString(days - 1);
}

/** "YYYY-MM-DD" 가 달력에 있는 날짜인지(2026-02-30 은 아니다). */
export function isCalendarDate(date: string): boolean {
  return dayNumber(date) !== null;
}

/** 월요일("YYYY-MM-DD")이 시작하는 주의 마지막 날(일요일). 해석할 수 없으면 입력 그대로. */
export function weekLastDay(monday: string): string {
  const days = dayNumber(monday);
  return days === null ? monday : dayToString(days + 6);
}

/** 해당 KST 시각을 지났는지(정각까지는 지나지 않은 것, 소수 초는 버린다). 해석할 수 없으면 false. */
export function isPastKst(kstLocalDateTime: string, nowMs: number): boolean {
  const m = KST_LOCAL.exec(kstLocalDateTime);
  if (!m) return false;
  const target = Date.parse(`${m[1]}:${m[2] ?? "00"}+09:00`);
  return !Number.isNaN(target) && nowMs > target;
}
