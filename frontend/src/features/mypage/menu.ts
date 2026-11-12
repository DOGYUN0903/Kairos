export const MY_MENU: { href: string; label: string }[] = [
  { href: "/my/orders", label: "주문 내역" },
  { href: "/my/returns", label: "반품 내역" },
  { href: "/my/coupons", label: "쿠폰" },
  { href: "/my/points", label: "포인트" },
  { href: "/my/reviews", label: "내 후기" },
  { href: "/my/likes", label: "좋아요" },
];

/** 현재 경로가 속한 메뉴 항목의 href. 어느 항목에도 속하지 않으면 null. */
export function activeMenuHref(pathname: string): string | null {
  const hit = MY_MENU.find((m) => pathname === m.href || pathname.startsWith(`${m.href}/`));
  return hit ? hit.href : null;
}
