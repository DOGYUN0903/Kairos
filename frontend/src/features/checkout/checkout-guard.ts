import type { OrderQuote } from "@/features/checkout/api";
import { isSameSelection, toBenefitBody, type BenefitSelection } from "@/features/checkout/benefit-selection";
import { formatKrw } from "@/lib/money";

/**
 * 결제 가능 여부. 주문이 한 번 만들어지면 다시 열리지 않는다.
 * pointsSettled: 포인트 입력 칸이 검증을 통과했고 그 값이 선택(견적 요청)에 반영됐다(입력 지연 중이거나 잘못된 값이 칸에 있으면 false).
 */
export function canPay(s: { hasQuote: boolean; fetching: boolean; placeholder: boolean; errored: boolean; orderCreated: boolean; pointsSettled: boolean }): boolean {
  return s.hasQuote && !s.fetching && !s.placeholder && !s.errored && !s.orderCreated && s.pointsSettled;
}

/**
 * 제출할 금액과 선택을 **같은 견적**에서 꺼낸다. 결제할 수 없거나, 견적이 없거나,
 * 견적을 만든 선택(quoteSelection)이 지금 선택과 다르면(새 견적을 기다리는 중) null.
 */
export function submitSnapshot(s: {
  quote: OrderQuote | undefined;
  quoteSelection: BenefitSelection | null;
  selection: BenefitSelection;
  payable: boolean;
}): { expectedTotalAmount: number; benefit: ReturnType<typeof toBenefitBody> } | null {
  if (!s.payable || !s.quote || !s.quoteSelection || !isSameSelection(s.quoteSelection, s.selection)) return null;
  return { expectedTotalAmount: s.quote.totalAmount, benefit: toBenefitBody(s.quoteSelection) };
}

/** 주문 생성 오류 중 혜택 선택을 비워야 하는 것. ORDER_PRICE_CHANGED 등은 null(선택 유지). */
export function orderErrorAction(code: string): "CLEAR_COUPONS" | "CLEAR_POINTS" | null {
  if (code === "COUPON_NOT_AVAILABLE" || code === "COUPON_NOT_FOUND" || code === "COUPON_NOT_APPLICABLE" || code === "COUPON_MIN_AMOUNT_NOT_MET") return "CLEAR_COUPONS";
  if (code === "POINT_INSUFFICIENT" || code === "POINT_EXCEEDS_LIMIT" || code === "POINT_INVALID_AMOUNT") return "CLEAR_POINTS";
  return null;
}

export function payButtonLabel(totalAmount: number): string {
  return totalAmount === 0 ? "주문하기" : `${formatKrw(totalAmount)} 결제하기`;
}

/**
 * 보낼 순간에 확정된 견적이 없어 주문 요청을 보내지 않았다는 표시.
 * ApiError 가 아니므로 useIdempotentSubmit 이 "응답 없는 실패"로 보고 키를 유지한다:
 * 직전 시도가 네트워크 오류였다면(서버에 주문이 생겼을 수 있다) 다음 제출도 같은 키로 나가야 주문이 둘이 되지 않는다.
 * 조용히 return 하면 성공으로 보여 그 키가 버려진다.
 */
export class QuoteNotReadyError extends Error {
  constructor() {
    super("견적이 확정되지 않았습니다");
    this.name = "QuoteNotReadyError";
  }
}

/**
 * 주문 생성 뒤 이동할 곳. 이미 결제 완료(0원 주문 포함)면 완료 화면, 0원인데 PAID 가 아니면(드문 경우) 주문 상세,
 * 아니면 결제 페이지(결제창 자동 열기).
 */
export function afterOrderHref(orderId: number, orderStatus: string, totalAmount: number): string {
  if (orderStatus === "PAID") return `/my/orders/${orderId}/complete`;
  if (totalAmount === 0) return `/my/orders/${orderId}`;
  return `/my/orders/${orderId}/pay?auto=1`;
}

/** 장바구니 모드에서 남은 항목이 없을 때만 /cart 로 보낸다. 주문이 만들어진 뒤에는 이동을 덮어쓰지 않는다. */
export function shouldRedirectToCart(s: { empty: boolean; orderCreated: boolean }): boolean {
  return s.empty && !s.orderCreated;
}

/** 품절·구매 불가 뒤 이동 위치. 바로 구매는 상품 상세(모르면 목록), 장바구니 모드는 장바구니. history 에 기대지 않는다. */
export function unavailableDestination(mode: "cart" | "direct", productId: number | undefined): string {
  if (mode === "cart") return "/cart";
  return productId === undefined ? "/products" : `/products/${productId}`;
}
