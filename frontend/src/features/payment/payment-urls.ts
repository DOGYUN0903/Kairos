// 결제창(모의·토스)이 끝난 뒤 돌아오는 주소. 구매자 API 는 주문번호로 주문을 찾지 못하므로 숫자 주문 ID 를 oid 로 붙인다.

export function successUrl(orderId: number): string {
  return `/checkout/success?oid=${orderId}`;
}

export function failUrl(orderId: number): string {
  return `/checkout/fail?oid=${orderId}`;
}

/** 모의 결제의 이동 주소. 토스 성공 리다이렉트와 같은 쿼리(orderId=주문번호, paymentKey, amount)라 승인 코드가 한 벌이다. */
export function mockSuccessHref(orderId: number, orderNo: string, paymentKey: string, amount: number): string {
  const q = new URLSearchParams({ orderId: orderNo, paymentKey, amount: String(amount) });
  return `${successUrl(orderId)}&${q.toString()}`;
}
