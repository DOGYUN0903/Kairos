import { EMPTY_SELECTION, isSameSelection, setPoints, type BenefitSelection } from "@/features/checkout/benefit-selection";

const POINT_CODES = new Set(["POINT_EXCEEDS_LIMIT", "POINT_INSUFFICIENT", "POINT_INVALID_AMOUNT"]);
const COUPON_CODES = new Set(["COUPON_MIN_AMOUNT_NOT_MET", "COUPON_NOT_APPLICABLE", "COUPON_NOT_AVAILABLE", "COUPON_NOT_FOUND"]);

/**
 * 견적이 혜택 코드로 실패했을 때 다음에 시도할 선택. 더 물러설 곳이 없으면 null(오류 상태를 보인다).
 * 쿠폰 오류: 직전 성공 선택 → 그것도 같으면 전부 비움 → 이미 비어 있으면 null. 매 단계 선택이 달라지므로 무한 반복하지 않는다.
 */
export function recoverSelection(code: string, current: BenefitSelection, lastGood: BenefitSelection): { next: BenefitSelection; notice: string } | null {
  if (POINT_CODES.has(code)) {
    if (current.points <= 0) return null;
    return { next: setPoints(current, 0), notice: "쿠폰이 바뀌어 포인트 사용을 초기화했습니다." };
  }
  if (COUPON_CODES.has(code)) {
    if (isSameSelection(current, EMPTY_SELECTION)) return null;
    const next = isSameSelection(current, lastGood) ? EMPTY_SELECTION : lastGood;
    return { next, notice: "쿠폰을 사용할 수 없어 선택을 되돌렸습니다." };
  }
  return null;
}
