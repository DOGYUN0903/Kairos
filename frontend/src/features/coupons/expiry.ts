import { secondsUntilKst } from "@/lib/datetime";

const SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60;

/** 남은 시간이 0 초과 7일 이하. */
export function isExpiringSoon(expiresAt: string, nowMs: number): boolean {
  const left = secondsUntilKst(expiresAt, nowMs);
  return left > 0 && left <= SEVEN_DAYS_SECONDS;
}
