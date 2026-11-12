export type CouponTab = "claimable" | "mine";
export type CouponStatus = "AVAILABLE" | "USED" | "EXPIRED";

export const COUPON_STATUSES: readonly CouponStatus[] = ["AVAILABLE", "USED", "EXPIRED"];

type Params = Pick<URLSearchParams, "get">;

/** ?tab= 이 진실이다. 모르는 값은 기본(받을 수 있는 쿠폰). */
export function parseCouponTab(sp: Params): CouponTab {
  return sp.get("tab") === "mine" ? "mine" : "claimable";
}

/** ?status= 가 진실이다. 모르는 값은 기본(사용 가능). */
export function parseCouponStatus(sp: Params): CouponStatus {
  const raw = sp.get("status");
  return COUPON_STATUSES.find((s) => s === raw) ?? "AVAILABLE";
}
