import type { components } from "@/api/schema";
import { isCalendarDate, isMonday, isWeekFinished } from "@/lib/datetime";

export type GenerateSettlementResult = components["schemas"]["GenerateSettlementResult"];
export type PeriodStartError = "NOT_MONDAY" | "NOT_FINISHED" | "INVALID";

/** 기간 시작일을 화면에서 먼저 검사한다. 통과하면 null. 서버가 최종 판단한다(앞선 기간 미생성 등). */
export function validatePeriodStart(date: string, nowMs: number): PeriodStartError | null {
  if (!isCalendarDate(date)) return "INVALID";
  if (!isMonday(date)) return "NOT_MONDAY";
  if (!isWeekFinished(date, nowMs)) return "NOT_FINISHED";
  return null;
}

export const PERIOD_START_MESSAGE: Record<PeriodStartError, string> = {
  NOT_MONDAY: "월요일을 선택해 주세요",
  NOT_FINISHED: "아직 끝나지 않은 주입니다",
  INVALID: "날짜를 선택해 주세요",
};

export const PERIOD_GUIDANCE = "앞선 기간이 아직 생성되지 않았다면 그 기간부터 생성해 주세요";

/** 생성 결과 문구. 숫자는 서버 응답 값 그대로다. */
export function generateResultText(r: GenerateSettlementResult): { tone: "success" | "info" | "warning"; lines: string[] } {
  if (r.alreadyGenerated) return { tone: "info", lines: ["이미 생성된 기간입니다"] };
  if (r.failedCount > 0) {
    return { tone: "warning", lines: [`생성 ${r.createdCount} · 건너뜀 ${r.skippedCount} · 실패 ${r.failedCount}`, "다시 생성하면 실패한 판매자만 만들어집니다"] };
  }
  return { tone: "success", lines: [`대상 판매자 ${r.sellerCount}명 · 생성 ${r.createdCount} · 건너뜀 ${r.skippedCount}`] };
}
