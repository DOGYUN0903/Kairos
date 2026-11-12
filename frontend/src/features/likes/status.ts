/** 판매 중인 상품인가. ON_SALE 만 true — 그 밖의 값(모르는 값 포함)은 판매 종료로 본다. */
export function isOnSale(productStatus: string): boolean {
  return productStatus === "ON_SALE";
}
