import { describe, expect, it } from "vitest";
import { cartesian, MAX_SKUS, previewRows } from "@/features/seller/products/sku-preview";

describe("sku-preview", () => {
  it("색상2_사이즈2는_4행", () => {
    const options = [
      { name: "색상", values: ["블랙", "화이트"] },
      { name: "사이즈", values: ["M", "L"] },
    ];
    expect(cartesian(options)).toEqual([
      ["블랙", "M"],
      ["블랙", "L"],
      ["화이트", "M"],
      ["화이트", "L"],
    ]);
    expect(previewRows(options).map((r) => r.displayName)).toEqual(["블랙 / M", "블랙 / L", "화이트 / M", "화이트 / L"]);
  });

  it("축_없음은_1행", () => {
    expect(cartesian([])).toEqual([[]]);
    expect(previewRows([])).toEqual([{ displayName: "" }]);
  });

  it("값이_빈_축은_조합에서_건너뛴다", () => {
    expect(cartesian([{ name: "색상", values: ["블랙"] }, { name: "사이즈", values: [] }])).toEqual([["블랙"]]);
  });

  it("최대_조합_수는_축당_20값_2축", () => {
    expect(MAX_SKUS).toBe(400);
  });
});
