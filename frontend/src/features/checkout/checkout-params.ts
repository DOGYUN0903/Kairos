import type { Cart } from "@/features/cart/api";

export const checkoutHref = {
  fromCart: (cartItemIds: number[]): string => `/checkout?cartItemIds=${cartItemIds.join(",")}`,
  direct: (skuId: number, qty: number): string => `/checkout?skuId=${skuId}&qty=${qty}`,
};

export type CheckoutParams = { mode: "cart"; cartItemIds: number[] } | { mode: "direct"; skuId: number; quantity: number };
export type CheckoutItem = { skuId: number; quantity: number };

const MAX_QTY = 999;
const toId = (s: string): number | null => (/^\d+$/.test(s) && Number(s) > 0 && Number.isSafeInteger(Number(s)) ? Number(s) : null);

/** 둘 다 있거나 없거나, 숫자가 아니거나, 수량이 1~999 밖이면 null (호출 쪽이 /cart 로 보낸다). */
export function parseCheckoutParams(sp: URLSearchParams): CheckoutParams | null {
  const ids = sp.get("cartItemIds");
  const sku = sp.get("skuId");
  if ((ids === null) === (sku === null && sp.get("qty") === null)) return null;
  if (ids !== null) {
    const parsed = ids.split(",").map(toId);
    if (parsed.some((n) => n === null)) return null;
    const unique = [...new Set(parsed as number[])];
    return unique.length > 0 ? { mode: "cart", cartItemIds: unique } : null;
  }
  const skuId = sku === null ? null : toId(sku);
  const qtyRaw = sp.get("qty");
  const quantity = qtyRaw === null ? null : toId(qtyRaw);
  if (skuId === null || quantity === null || quantity > MAX_QTY) return null;
  return { mode: "direct", skuId, quantity };
}

/** 장바구니 모드는 선택한 ID 중 구매 가능한 항목만. 장바구니를 아직 못 읽었거나 남은 것이 없으면 null. */
export function resolveCheckoutItems(parsed: CheckoutParams, cart: Cart | undefined): CheckoutItem[] | null {
  if (parsed.mode === "direct") return [{ skuId: parsed.skuId, quantity: parsed.quantity }];
  if (!cart) return null;
  const wanted = new Set(parsed.cartItemIds);
  const items = cart.items
    .filter((i) => wanted.has(i.cartItemId) && i.availability === "AVAILABLE")
    .map((i) => ({ skuId: i.skuId, quantity: i.quantity }));
  return items.length > 0 ? items : null;
}
