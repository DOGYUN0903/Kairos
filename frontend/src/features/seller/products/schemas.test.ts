import { describe, expect, it } from "vitest";
import { priceSchema, productSchema, toRegisterRequest, type ProductFormInput } from "@/features/seller/products/schemas";

const valid: ProductFormInput = {
  brandId: 1,
  categoryId: 11,
  name: "오버핏 티셔츠",
  description: "두께감 있는 코튼 티셔츠",
  images: [{ fileId: 5, url: "http://x/5.png" }, { fileId: 6, url: "http://x/6.png" }],
  originalPrice: "39,000",
  salePrice: "27300",
  options: [
    { name: "색상", values: ["블랙", "화이트"] },
    { name: "사이즈", values: ["M", "L"] },
  ],
};

function fieldErrors(input: unknown): Record<string, string> {
  const r = productSchema.safeParse(input);
  if (r.success) return {};
  return Object.fromEntries(r.error.issues.map((i) => [i.path.join("."), i.message]));
}

describe("productSchema", () => {
  it("올바른_입력은_원_단위_정수로_바꾼다", () => {
    const r = productSchema.parse(valid);
    expect(r.originalPrice).toBe(39000);
    expect(r.salePrice).toBe(27300);
  });

  it("판매가가_정가보다_크면_salePrice_필드_에러", () => {
    const errs = fieldErrors({ ...valid, originalPrice: "10000", salePrice: "10001" });
    expect(Object.keys(errs)).toEqual(["salePrice"]);
  });

  it("다른_필드가_틀려도_판매가_초과는_바로_알린다", () => {
    const errs = fieldErrors({ ...valid, name: "", images: [], originalPrice: "10000", salePrice: "20000" });
    expect(errs).toHaveProperty("salePrice", "판매가는 정가 이하여야 합니다.");
    expect(errs).toHaveProperty("name");
  });

  it("다른_필드가_틀려도_축_이름_중복은_바로_알린다", () => {
    const options = [
      { name: "색상", values: ["블랙"] },
      { name: "색상", values: ["M"] },
    ];
    expect(fieldErrors({ ...valid, name: "", options })).toHaveProperty("options.1.name");
  });

  it("빈_옵션_값은_한국어_메시지", () => {
    expect(fieldErrors({ ...valid, options: [{ name: "색상", values: [" "] }] })).toHaveProperty("options.0.values.0", "옵션 값을 입력해 주세요.");
  });

  it("판매가_0원은_거부한다", () => {
    expect(fieldErrors({ ...valid, salePrice: "0" })).toHaveProperty("salePrice");
  });

  it("소수_입력은_거부한다", () => {
    expect(fieldErrors({ ...valid, originalPrice: "39000.5" })).toHaveProperty("originalPrice");
    expect(fieldErrors({ ...valid, salePrice: "1.0" })).toHaveProperty("salePrice");
  });

  it("음수와_빈_값은_거부한다", () => {
    expect(fieldErrors({ ...valid, originalPrice: "-1" })).toHaveProperty("originalPrice");
    expect(fieldErrors({ ...valid, salePrice: "" })).toHaveProperty("salePrice");
  });

  it("이미지_0장은_거부", () => {
    expect(fieldErrors({ ...valid, images: [] })).toHaveProperty("images");
  });

  it("이미지_11장은_거부", () => {
    const images = Array.from({ length: 11 }, (_, i) => ({ fileId: i + 1, url: `u${i}` }));
    expect(fieldErrors({ ...valid, images })).toHaveProperty("images");
  });

  it("브랜드와_카테고리를_고르지_않으면_거부", () => {
    const errs = fieldErrors({ ...valid, brandId: Number.NaN, categoryId: Number.NaN });
    expect(errs).toHaveProperty("brandId");
    expect(errs).toHaveProperty("categoryId");
  });

  it("옵션_축_3개는_거부", () => {
    const options = [
      { name: "a", values: ["1"] },
      { name: "b", values: ["1"] },
      { name: "c", values: ["1"] },
    ];
    expect(fieldErrors({ ...valid, options })).toHaveProperty("options");
  });

  it("같은_축_안_값_중복은_거부", () => {
    const errs = fieldErrors({ ...valid, options: [{ name: "색상", values: ["블랙", "블랙"] }] });
    expect(errs).toHaveProperty("options.0.values");
  });

  it("축_이름_중복은_거부", () => {
    const options = [
      { name: "색상", values: ["블랙"] },
      { name: "색상", values: ["M"] },
    ];
    expect(fieldErrors({ ...valid, options })).toHaveProperty("options.1.name");
  });

  it("값이_없는_축은_거부", () => {
    expect(fieldErrors({ ...valid, options: [{ name: "색상", values: [] }] })).toHaveProperty("options.0.values");
  });

  it("축_하나에_값_21개는_거부", () => {
    const values = Array.from({ length: 21 }, (_, i) => `v${i}`);
    expect(fieldErrors({ ...valid, options: [{ name: "색상", values }] })).toHaveProperty("options.0.values");
  });
});

describe("toRegisterRequest", () => {
  it("폼_값을_등록_요청으로_바꾼다", () => {
    const req = toRegisterRequest(productSchema.parse(valid));
    expect(req).toEqual({
      brandId: 1,
      originalPrice: 39000,
      salePrice: 27300,
      content: {
        name: "오버핏 티셔츠",
        description: "두께감 있는 코튼 티셔츠",
        categoryId: 11,
        imageFileIds: [5, 6],
        options: [
          { optionId: null, name: "색상", values: [{ valueId: null, name: "블랙" }, { valueId: null, name: "화이트" }] },
          { optionId: null, name: "사이즈", values: [{ valueId: null, name: "M" }, { valueId: null, name: "L" }] },
        ],
      },
    });
  });
});

describe("priceSchema", () => {
  it("정가보다_큰_판매가는_거부", () => {
    expect(priceSchema(10000).safeParse({ salePrice: "10001" }).success).toBe(false);
  });
  it("정가_이하는_정수로_통과", () => {
    expect(priceSchema(10000).parse({ salePrice: "9,000" })).toEqual({ salePrice: 9000 });
  });
});
