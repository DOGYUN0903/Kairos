/** ?page= 값(0부터). 없거나 숫자가 아니거나 음수·소수면 0. 목록 화면(구매자·판매자·관리자 주문)이 함께 쓴다. */
export function parsePage(raw: string | null | undefined): number {
  return raw !== null && raw !== undefined && /^\d{1,9}$/.test(raw) ? Number(raw) : 0;
}
