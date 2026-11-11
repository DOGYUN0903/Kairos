import { describe, expect, it } from "vitest";
import { skuImpact, type ChangeOption } from "@/features/seller/products/sku-impact";

// 색상(1) 블랙(11)·화이트(12) × 사이즈(2) M(21)·L(22)
const current = {
  options: [
    { optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] },
    { optionId: 2, name: "사이즈", values: [{ valueId: 21, name: "M" }, { valueId: 22, name: "L" }] },
  ],
  skus: [
    { skuId: 101, optionValueIds: [11, 21], displayName: "블랙 / M" },
    { skuId: 102, optionValueIds: [11, 22], displayName: "블랙 / L" },
    { skuId: 103, optionValueIds: [12, 21], displayName: "화이트 / M" },
    { skuId: 104, optionValueIds: [12, 22], displayName: "화이트 / L" },
  ],
};

const keepAll: ChangeOption[] = [
  { optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] },
  { optionId: 2, name: "사이즈", values: [{ valueId: 21, name: "M" }, { valueId: 22, name: "L" }] },
];

describe("skuImpact", () => {
  it("값_ID를_유지하면_kept", () => {
    expect(skuImpact(current, keepAll)).toEqual({
      kept: ["블랙 / M", "블랙 / L", "화이트 / M", "화이트 / L"],
      added: [],
      discontinued: [],
    });
  });

  it("새_값을_더하면_신규_조합만_added", () => {
    const next: ChangeOption[] = [
      keepAll[0]!,
      { ...keepAll[1]!, values: [...keepAll[1]!.values, { valueId: null, name: "XL" }] },
    ];
    const r = skuImpact(current, next);
    expect(r.kept).toHaveLength(4);
    expect(r.added).toEqual(["블랙 / XL", "화이트 / XL"]);
    expect(r.discontinued).toEqual([]);
  });

  it("값을_빼면_그_값의_조합은_단종", () => {
    const next: ChangeOption[] = [{ ...keepAll[0]!, values: [keepAll[0]!.values[0]!] }, keepAll[1]!];
    const r = skuImpact(current, next);
    expect(r.kept).toEqual(["블랙 / M", "블랙 / L"]);
    expect(r.discontinued).toEqual(["화이트 / M", "화이트 / L"]);
    expect(r.added).toEqual([]);
  });

  it("축_하나를_없애면_남은_축의_값마다_신규_1개_기존_조합은_전부_단종", () => {
    const r = skuImpact(current, [keepAll[0]!]);
    expect(r.kept).toEqual([]);
    expect(r.added).toEqual(["블랙", "화이트"]);
    expect(r.discontinued).toEqual(["블랙 / M", "블랙 / L", "화이트 / M", "화이트 / L"]);
  });

  it("축_0개에서_1개로_가면_기존_단일_SKU는_단종", () => {
    const single = { options: [], skus: [{ skuId: 1, optionValueIds: [], displayName: "" }] };
    const r = skuImpact(single, [{ optionId: null, name: "색상", values: [{ valueId: null, name: "블랙" }] }]);
    expect(r).toEqual({ kept: [], added: ["블랙"], discontinued: [""] });
  });

  it("축_0개를_유지하면_단일_SKU는_kept", () => {
    const single = { options: [], skus: [{ skuId: 1, optionValueIds: [], displayName: "" }] };
    expect(skuImpact(single, [])).toEqual({ kept: [""], added: [], discontinued: [] });
  });
});
