/** 승인·거절·수거 확인은 활성(APPROVED) 판매자만. 정지 판매자는 조회만 하고 서버도 거절한다. */
export function canProcessReturns(sellerStatus: string): boolean {
  return sellerStatus === "APPROVED";
}
