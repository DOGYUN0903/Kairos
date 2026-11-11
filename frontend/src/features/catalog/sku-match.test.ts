import { describe, expect, it } from "vitest";
import { disabledValues, findSku, selectionTotal, type ProductOption, type Sku } from "./sku-match";

// 색상(1: 블랙=11, 화이트=12) x 사이즈(2: M=21, L=22)
const options: ProductOption[] = [
  { optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] },
  { optionId: 2, name: "사이즈", values: [{ valueId: 21, name: "M" }, { valueId: 22, name: "L" }] },
];
const sku = (skuId: number, optionValueIds: number[], extraPrice = 0, soldOut = false): Sku => ({
  skuId,
  optionValueIds,
  displayName: "",
  extraPrice,
  soldOut,
});
const skus: Sku[] = [sku(1, [11, 21]), sku(2, [11, 22], 2000, true), sku(3, [12, 21]), sku(4, [12, 22], 2000)];

describe("findSku", () => {
  it("모든_축을_고르면_optionValueIds가_같은_SKU_하나", () => {
    expect(findSku(skus, { 1: 12, 2: 22 }, options)?.skuId).toBe(4);
    expect(findSku(skus, { 2: 21, 1: 11 }, options)?.skuId).toBe(1);
  });

  it("축이_덜_골라지면_null", () => {
    expect(findSku(skus, { 1: 11 }, options)).toBeNull();
    expect(findSku(skus, {}, options)).toBeNull();
  });

  it("옵션이_없는_상품은_빈_선택으로_단일_SKU", () => {
    const single = [sku(9, [])];
    expect(findSku(single, {}, [])?.skuId).toBe(9);
  });
});

describe("disabledValues", () => {
  it("남은_조합이_전부_품절인_값은_disabled", () => {
    // 블랙을 고르면 블랙/L 이 품절이라 L 이 막힌다.
    expect([...disabledValues(skus, options, { 1: 11 })]).toEqual([22]);
  });

  it("아무것도_고르지_않으면_하나라도_팔리는_값은_열려_있다", () => {
    expect(disabledValues(skus, options, {}).size).toBe(0);
  });

  it("자기_축의_선택은_조건에서_제외한다", () => {
    // 블랙 + L 이 품절이어도 사이즈 축에서는 M 으로 바꿀 수 있어야 한다.
    expect(disabledValues(skus, options, { 1: 11, 2: 22 }).has(21)).toBe(false);
  });

  it("SKU가_없는_값은_disabled", () => {
    const only = [sku(1, [11, 21])];
    expect(disabledValues(only, options, {})).toEqual(new Set([12, 22]));
  });
});

describe("selectionTotal", () => {
  it("selectionTotal은_추가_금액을_수량만큼_곱한다", () => {
    expect(selectionTotal(27300, sku(4, [12, 22], 2000), 3)).toBe(87900);
  });
});
