/** 주문서에서 고른 혜택. 금액은 들어 있지 않다(금액은 전부 견적 응답 값). */
export type BenefitSelection = {
  /** skuId → couponId */
  productCoupons: Record<number, number>;
  cartCouponId: number | null;
  points: number;
};

export const EMPTY_SELECTION: BenefitSelection = Object.freeze({ productCoupons: Object.freeze({}), cartCouponId: null, points: 0 }) as BenefitSelection;

/** 같은 쿠폰이 다른 skuId 에 있으면 거기서 뺀다. couponId 가 null 이면 그 상품의 쿠폰을 뺀다. */
export function selectProductCoupon(sel: BenefitSelection, skuId: number, couponId: number | null): BenefitSelection {
  const next: Record<number, number> = {};
  for (const [k, v] of Object.entries(sel.productCoupons)) {
    const sku = Number(k);
    if (sku === skuId) continue;
    if (couponId !== null && v === couponId) continue;
    next[sku] = v;
  }
  if (couponId !== null) next[skuId] = couponId;
  return { ...sel, productCoupons: next };
}

export function selectCartCoupon(sel: BenefitSelection, couponId: number | null): BenefitSelection {
  return { ...sel, productCoupons: { ...sel.productCoupons }, cartCouponId: couponId };
}

export function setPoints(sel: BenefitSelection, points: number): BenefitSelection {
  return { ...sel, productCoupons: { ...sel.productCoupons }, points };
}

/** 포인트는 유지한다. */
export function clearCoupons(sel: BenefitSelection): BenefitSelection {
  return { productCoupons: {}, cartCouponId: null, points: sel.points };
}

function sortedPairs(sel: BenefitSelection): [number, number][] {
  return Object.entries(sel.productCoupons)
    .map(([k, v]): [number, number] => [Number(k), v])
    .sort((a, b) => a[0] - b[0]);
}

export function isSameSelection(a: BenefitSelection, b: BenefitSelection): boolean {
  if (a.cartCouponId !== b.cartCouponId || a.points !== b.points) return false;
  const pa = sortedPairs(a);
  const pb = sortedPairs(b);
  return pa.length === pb.length && pa.every(([sku, id], i) => sku === pb[i]![0] && id === pb[i]![1]);
}

/** 견적 요청·쿼리 키에 쓰는 본문. productCoupons 는 skuId 오름차순(키·본문 안정). */
export function toBenefitBody(sel: BenefitSelection): { productCoupons: { skuId: number; couponId: number }[]; cartCouponId: number | null; pointsToUse: number } {
  return {
    productCoupons: sortedPairs(sel).map(([skuId, couponId]) => ({ skuId, couponId })),
    cartCouponId: sel.cartCouponId,
    pointsToUse: sel.points,
  };
}

/** 그 쿠폰이 이 상품이 아닌 다른 상품에 적용돼 있는가. */
export function couponAppliedElsewhere(sel: BenefitSelection, skuId: number, couponId: number): boolean {
  return Object.entries(sel.productCoupons).some(([k, v]) => Number(k) !== skuId && v === couponId);
}
