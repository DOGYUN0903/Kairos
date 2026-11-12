import { formatPoints, mod } from "@/lib/money";

export type PointsReason = "NOT_NUMBER" | "NOT_UNIT" | "OVER_MAX";
export type PointsCheck = { ok: true; points: number } | { ok: false; reason: PointsReason };

/** 포인트 입력 검증. 화면이 하는 계산은 10의 배수 확인(mod)뿐이고, 한도는 서버 견적의 maxUsablePoints 다. */
export function validatePoints(raw: string, maxUsable: number): PointsCheck {
  if (raw === "") return { ok: true, points: 0 };
  if (!/^\d+$/.test(raw)) return { ok: false, reason: "NOT_NUMBER" };
  const n = Number(raw);
  if (!Number.isSafeInteger(n)) return { ok: false, reason: "NOT_NUMBER" };
  if (mod(n, 10) !== 0) return { ok: false, reason: "NOT_UNIT" };
  if (n > maxUsable) return { ok: false, reason: "OVER_MAX" };
  return { ok: true, points: n };
}

export function pointsMessage(reason: PointsReason, maxUsable: number): string {
  switch (reason) {
    case "NOT_NUMBER":
      return "숫자만 입력해 주세요";
    case "NOT_UNIT":
      return "10P 단위로 입력해 주세요";
    case "OVER_MAX":
      return `최대 ${formatPoints(maxUsable)}까지 사용할 수 있습니다`;
  }
}

/** 입력을 막는 이유. 쓸 수 있으면 null. */
export function pointsDisabledReason(balance: number, maxUsable: number): string | null {
  if (maxUsable !== 0) return null;
  if (balance === 0) return "보유 포인트가 없습니다";
  return "이 주문에는 포인트를 사용할 수 없습니다 (1,000P 이상 보유 시, 상품 금액의 30%까지)";
}
