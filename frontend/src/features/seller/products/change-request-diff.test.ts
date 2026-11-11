import { describe, expect, it } from "vitest";
import type { SellerChangeRequestDetail, SellerProductDetail } from "@/features/seller/products/api";
import { changedFields } from "@/features/seller/products/change-request-diff";

const product = {
  name: "티셔츠",
  description: "설명",
  categoryId: 2,
  images: [{ fileId: 1, url: "a", isMain: true }],
  originalPrice: 10000,
  options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }] }],
} as unknown as SellerProductDetail;

const request = (over: Partial<SellerChangeRequestDetail["content"]> = {}, originalPrice = 10000) =>
  ({
    originalPrice,
    content: {
      name: "티셔츠",
      description: "설명",
      categoryId: 2,
      images: [{ fileId: 1, url: "a", isMain: true }],
      options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }] }],
      ...over,
    },
  }) as unknown as SellerChangeRequestDetail;

describe("changedFields", () => {
  it("같으면_빈_목록", () => {
    expect(changedFields(product, request())).toEqual([]);
  });
  it("이름_정가_값_이름_변경을_잡는다", () => {
    expect(
      changedFields(product, request({ name: "새 티셔츠", options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "검정" }] }] }, 12000)),
    ).toEqual(["상품명", "옵션", "정가"]);
  });
  it("이미지_순서만_바뀌어도_이미지", () => {
    const p = { ...product, images: [{ fileId: 1 }, { fileId: 2 }] } as unknown as SellerProductDetail;
    expect(changedFields(p, request({ images: [{ fileId: 2, url: "", isMain: true }, { fileId: 1, url: "", isMain: false }] }))).toEqual(["이미지"]);
  });
});
