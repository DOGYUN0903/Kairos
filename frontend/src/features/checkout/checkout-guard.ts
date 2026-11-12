/** 결제 가능 여부. 주문이 한 번 만들어지면 다시 열리지 않는다. */
export function canPay(s: { hasQuote: boolean; fetching: boolean; placeholder: boolean; errored: boolean; orderCreated: boolean; submitting: boolean }): boolean {
  return s.hasQuote && !s.fetching && !s.placeholder && !s.errored && !s.orderCreated && !s.submitting;
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
