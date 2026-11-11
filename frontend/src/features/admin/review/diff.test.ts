import { describe, expect, it } from "vitest";
import type { SellerProductDetail } from "@/features/seller/products/api";
import { contentDiff, type ChangeRequestDetail } from "./diff";

const current = {
  productId: 1024,
  name: "오버핏 티셔츠",
  description: "설명",
  categoryId: 5,
  originalPrice: 39000,
  salePrice: 27300,
  images: [
    { fileId: 1, url: "/files/1", isMain: true },
    { fileId: 2, url: "/files/2", isMain: false },
  ],
  options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] }],
  skus: [
    { skuId: 101, optionValueIds: [11], displayName: "블랙", extraPrice: 0, stock: 50, onSale: true, skuStatus: "ACTIVE" },
    { skuId: 102, optionValueIds: [12], displayName: "화이트", extraPrice: 0, stock: 30, onSale: true, skuStatus: "ACTIVE" },
  ],
} as unknown as SellerProductDetail;

const same = (over: Partial<ChangeRequestDetail["content"]> = {}, originalPrice = 39000): ChangeRequestDetail => ({
  changeRequestId: 301,
  productId: 1024,
  sellerId: 1,
  status: "PENDING",
  requestedAt: "2026-11-07T14:20:00",
  originalPrice,
  content: {
    name: "오버핏 티셔츠",
    description: "설명",
    categoryId: 5,
    images: [
      { fileId: 1, url: "/files/1", isMain: true },
      { fileId: 2, url: "/files/2", isMain: false },
    ],
    options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] }],
    ...over,
  },
});

const changedFields = (r: ChangeRequestDetail) => contentDiff(current, r).diffs.filter((d) => d.changed).map((d) => d.field);

describe("contentDiff", () => {
  it("아무것도_안_바뀌면_changed가_없다", () => {
    expect(changedFields(same())).toEqual([]);
  });

  it("이름만_바뀌면_name만_changed", () => {
    expect(changedFields(same({ name: "오버핏 티셔츠 (2팩)" }))).toEqual(["name"]);
  });

  it("이미지_추가는_newImageIds에_들어간다", () => {
    const r = same({
      images: [
        { fileId: 1, url: "/files/1", isMain: true },
        { fileId: 2, url: "/files/2", isMain: false },
        { fileId: 9, url: "/files/9", isMain: false },
      ],
    });
    const d = contentDiff(current, r);
    expect([...d.newImageIds]).toEqual([9]);
    expect(changedFields(r)).toEqual(["images"]);
  });

  it("이미지_순서만_바뀌면_changed이지만_신규는_없다", () => {
    const r = same({
      images: [
        { fileId: 2, url: "/files/2", isMain: true },
        { fileId: 1, url: "/files/1", isMain: false },
      ],
    });
    expect(contentDiff(current, r).newImageIds.size).toBe(0);
    expect(changedFields(r)).toEqual(["images"]);
  });

  it("옵션_값_추가는_impact_added에_표시된다", () => {
    const r = same({
      options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }, { name: "네이비" }] }],
    });
    const d = contentDiff(current, r);
    expect(d.impact.added).toEqual(["네이비"]);
    expect(d.impact.kept).toEqual(["블랙", "화이트"]);
    expect(d.impact.discontinued).toEqual([]);
    expect(changedFields(r)).toEqual(["options"]);
  });

  it("옵션_값_삭제는_impact_discontinued에_표시된다", () => {
    const r = same({ options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }] }] });
    expect(contentDiff(current, r).impact.discontinued).toEqual(["화이트"]);
  });

  it("정가_변경은_originalPrice_changed", () => {
    expect(changedFields(same({}, 45000))).toEqual(["originalPrice"]);
  });
});
