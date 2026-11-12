import type { CartItem } from "@/features/cart/api";

export type Availability = CartItem["availability"];
export type QuoteItem = { skuId: number; quantity: number };
export type SellerGroup = { sellerId: number | null; sellerName: string; items: CartItem[] };

const MAX_QTY = 999;
export const NO_SELLER_LABEL = "판매 종료 상품";

export function isSelectable(item: CartItem): boolean {
  return item.availability === "AVAILABLE";
}

export function initialSelection(items: CartItem[]): Set<number> {
  return new Set(items.filter(isSelectable).map((i) => i.cartItemId));
}

export function toggleItem(items: CartItem[], sel: Set<number>, id: number): Set<number> {
  const target = items.find((i) => i.cartItemId === id);
  if (!target || !isSelectable(target)) return new Set(sel);
  const next = new Set(sel);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

function toggleMany(targets: CartItem[], sel: Set<number>): Set<number> {
  const ids = targets.filter(isSelectable).map((i) => i.cartItemId);
  const allOn = ids.length > 0 && ids.every((id) => sel.has(id));
  const next = new Set(sel);
  for (const id of ids) {
    if (allOn) next.delete(id);
    else next.add(id);
  }
  return next;
}

/** 구매 가능한 항목이 모두 선택돼 있으면 모두 해제, 아니면 모두 선택. */
export function toggleAll(items: CartItem[], sel: Set<number>): Set<number> {
  return toggleMany(items, sel);
}

export function toggleSeller(items: CartItem[], sel: Set<number>, sellerId: number | null): Set<number> {
  return toggleMany(
    items.filter((i) => i.sellerId === sellerId),
    sel,
  );
}

/** 다시 읽은 장바구니에서 사라졌거나 구매 불가가 된 ID 를 뺀다. */
export function pruneSelection(items: CartItem[], sel: Set<number>): Set<number> {
  const ok = new Set(items.filter(isSelectable).map((i) => i.cartItemId));
  return new Set([...sel].filter((id) => ok.has(id)));
}

/** 판매자 첫 등장 순서를 유지하고, 판매자 정보가 없는 항목은 맨 뒤 묶음으로 둔다. */
export function groupBySeller(items: CartItem[]): SellerGroup[] {
  const groups: SellerGroup[] = [];
  const orphan: SellerGroup = { sellerId: null, sellerName: NO_SELLER_LABEL, items: [] };
  for (const item of items) {
    if (item.sellerId === null) {
      orphan.items.push(item);
      continue;
    }
    const found = groups.find((g) => g.sellerId === item.sellerId);
    if (found) found.items.push(item);
    else groups.push({ sellerId: item.sellerId, sellerName: item.sellerName ?? NO_SELLER_LABEL, items: [item] });
  }
  return orphan.items.length > 0 ? [...groups, orphan] : groups;
}

export function selectedQuoteItems(items: CartItem[], sel: Set<number>): QuoteItem[] {
  return items.filter((i) => sel.has(i.cartItemId)).map((i) => ({ skuId: i.skuId, quantity: i.quantity }));
}

export function unavailableLabel(availability: Availability): string {
  switch (availability) {
    case "SOLD_OUT":
      return "품절";
    case "PAUSED":
      return "판매 중지";
    case "ENDED":
    case "DISCONTINUED":
      return "판매 종료";
    default:
      return "";
  }
}

export function clampQuantity(input: string): number {
  const n = Number(input.trim());
  if (!Number.isFinite(n) || input.trim() === "") return 1;
  return Math.min(MAX_QTY, Math.max(1, Math.trunc(n)));
}
