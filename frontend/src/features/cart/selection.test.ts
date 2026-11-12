import { describe, expect, it } from "vitest";
import type { CartItem } from "@/features/cart/api";
import {
  clampQuantity,
  groupBySeller,
  initialSelection,
  isSelectable,
  pruneSelection,
  selectedQuoteItems,
  toggleAll,
  toggleItem,
  toggleSeller,
  unavailableLabel,
} from "@/features/cart/selection";

function item(id: number, overrides: Partial<CartItem> = {}): CartItem {
  return {
    cartItemId: id,
    skuId: id * 10,
    productId: id * 100,
    sellerId: 1,
    sellerName: "판매자 A",
    productName: `상품 ${id}`,
    brandName: "브랜드",
    thumbnailUrl: "",
    optionDisplayName: "블랙 / L",
    unitPrice: 1000,
    quantity: 1,
    lineAmount: 1000,
    availability: "AVAILABLE",
    ...overrides,
  };
}

describe("장바구니 선택", () => {
  it("처음에는_구매_가능한_항목만_선택된다", () => {
    const items = [item(1), item(2, { availability: "PAUSED" }), item(3)];
    expect(initialSelection(items)).toEqual(new Set([1, 3]));
  });

  it("구매_불가_항목은_토글해도_선택되지_않는다", () => {
    const sold = item(2, { availability: "SOLD_OUT" });
    const items = [item(1), sold];
    expect(isSelectable(sold)).toBe(false);
    expect(toggleItem(items, new Set([1]), 2)).toEqual(new Set([1]));
    expect(toggleItem(items, new Set([1]), 1)).toEqual(new Set());
    expect(toggleItem(items, new Set(), 1)).toEqual(new Set([1]));
  });

  it("전체_선택은_구매_가능한_항목만_고르고_다시_누르면_모두_해제한다", () => {
    const items = [item(1), item(2, { availability: "ENDED" }), item(3)];
    const all = toggleAll(items, new Set([1]));
    expect(all).toEqual(new Set([1, 3]));
    expect(toggleAll(items, all)).toEqual(new Set());
  });

  it("판매자_묶음_선택은_그_판매자의_구매_가능_항목만_바꾼다", () => {
    const items = [
      item(1, { sellerId: 1 }),
      item(2, { sellerId: 1, availability: "PAUSED" }),
      item(3, { sellerId: 2, sellerName: "판매자 B" }),
    ];
    const sel = new Set([3]);
    const on = toggleSeller(items, sel, 1);
    expect(on).toEqual(new Set([1, 3]));
    expect(toggleSeller(items, on, 1)).toEqual(new Set([3]));
  });

  it("다시_읽은_장바구니에서_사라졌거나_구매_불가가_된_항목은_선택에서_빠진다", () => {
    const items = [item(1), item(2, { availability: "SOLD_OUT" })];
    expect(pruneSelection(items, new Set([1, 2, 9]))).toEqual(new Set([1]));
  });

  it("판매자별로_묶고_판매자_정보가_없는_항목은_마지막_묶음이다", () => {
    const items = [
      item(1, { sellerId: null, sellerName: null, availability: "DISCONTINUED" }),
      item(2, { sellerId: 2, sellerName: "판매자 B" }),
      item(3, { sellerId: 1 }),
      item(4, { sellerId: 2, sellerName: "판매자 B" }),
    ];
    const groups = groupBySeller(items);
    expect(groups.map((g) => g.sellerId)).toEqual([2, 1, null]);
    expect(groups[0]?.items.map((i) => i.cartItemId)).toEqual([2, 4]);
    expect(groups[2]?.sellerName).toBe("판매 종료 상품");
  });

  it("선택한_항목을_견적_요청_형태로_바꾼다", () => {
    const items = [item(1, { quantity: 3 }), item(2)];
    expect(selectedQuoteItems(items, new Set([1]))).toEqual([{ skuId: 10, quantity: 3 }]);
  });

  it("구매_불가_사유_문구", () => {
    expect(unavailableLabel("SOLD_OUT")).toBe("품절");
    expect(unavailableLabel("PAUSED")).toBe("판매 중지");
    expect(unavailableLabel("ENDED")).toBe("판매 종료");
    expect(unavailableLabel("DISCONTINUED")).toBe("판매 종료");
  });

  it("수량_입력은_1에서_999로_맞춘다", () => {
    expect(clampQuantity("0")).toBe(1);
    expect(clampQuantity("-3")).toBe(1);
    expect(clampQuantity("1000")).toBe(999);
    expect(clampQuantity("abc")).toBe(1);
    expect(clampQuantity("12")).toBe(12);
  });
});
