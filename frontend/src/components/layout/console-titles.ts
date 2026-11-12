/** 콘솔 상단 바 제목. 경로 접두사로 고르고, 모르는 경로는 기본값이다. 긴 접두사가 먼저 와야 한다. */
export function sellerTitleOf(pathname: string): string {
  if (pathname.startsWith("/seller/apply")) return "입점 신청";
  if (pathname.startsWith("/seller/products")) return "상품 관리";
  if (pathname.startsWith("/seller/orders")) return "주문 관리";
  if (pathname.startsWith("/seller/brands")) return "브랜드";
  if (pathname.startsWith("/seller/coupons")) return "쿠폰 관리";
  if (pathname.startsWith("/seller/reviews")) return "후기 관리";
  return "대시보드";
}

/** 관리자 콘솔. 제목이 없는 화면(기존)은 undefined 로 두어 ConsoleShell 의 기본(역할 이름)을 쓴다. */
export function adminTitleOf(pathname: string): string | undefined {
  if (pathname.startsWith("/admin/coupons")) return "쿠폰 관리";
  if (pathname.startsWith("/admin/members/points")) return "회원 포인트";
  return undefined;
}
