/** 결제 가능 여부. 주문이 한 번 만들어지면 다시 열리지 않는다. */
export function canPay(s: { hasQuote: boolean; fetching: boolean; placeholder: boolean; errored: boolean; orderCreated: boolean }): boolean {
  return s.hasQuote && !s.fetching && !s.placeholder && !s.errored && !s.orderCreated;
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

/** 주문 생성 뒤 이동할 곳. 0원 주문처럼 이미 결제 완료면 완료 화면, 아니면 결제 페이지(결제창 자동 열기). */
export function afterOrderHref(orderId: number, orderStatus: string): string {
  return orderStatus === "PAID" ? `/my/orders/${orderId}/complete` : `/my/orders/${orderId}/pay?auto=1`;
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
