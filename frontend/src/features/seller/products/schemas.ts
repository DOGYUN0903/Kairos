import { z } from "zod";
import type { components } from "@/api/schema";
import { MAX_AXES, MAX_VALUES_PER_AXIS } from "@/features/seller/products/sku-preview";
import { parseWon } from "@/lib/money";

// 제약은 OpenAPI(RegisterProductRequest·ProductContentRequest·OptionRequest)와 백엔드 ProductContent 에 맞춘다.
// 설명은 백엔드 길이 제한이 없어 2000자로 프론트에서만 막는다. 축당 값 개수 한도도 프론트 전용(조합 폭발 방지).
export const NAME_MAX = 100;
export const DESCRIPTION_MAX = 2000;
export const OPTION_NAME_MAX = 50;
export const OPTION_VALUE_MAX = 50;
export const IMAGES_MAX = 10;

/** 문자열 금액 입력 → 원 단위 정수. 소수·음수·빈 값은 parseWon 이 null 을 준다. */
const won = (label: string) =>
  z.string().transform((s, ctx) => {
    if (s.trim() === "") {
      ctx.addIssue({ code: "custom", message: `${label}를 입력해 주세요.` });
      return z.NEVER;
    }
    const n = parseWon(s);
    if (n === null) {
      ctx.addIssue({ code: "custom", message: `${label}는 원 단위 정수로 입력해 주세요.` });
      return z.NEVER;
    }
    if (n < 1) {
      ctx.addIssue({ code: "custom", message: `${label}는 1원 이상이어야 합니다.` });
      return z.NEVER;
    }
    return n;
  });

const optionSchema = z.object({
  name: z.string().trim().min(1, "옵션명을 입력해 주세요.").max(OPTION_NAME_MAX, `옵션명은 ${OPTION_NAME_MAX}자 이하로 입력해 주세요.`),
  values: z
    .array(z.string().trim().min(1).max(OPTION_VALUE_MAX, `옵션 값은 ${OPTION_VALUE_MAX}자 이하로 입력해 주세요.`))
    .min(1, "옵션 값을 하나 이상 입력해 주세요.")
    .max(MAX_VALUES_PER_AXIS, `옵션 값은 축마다 최대 ${MAX_VALUES_PER_AXIS}개입니다.`)
    .refine((vs) => new Set(vs).size === vs.length, "같은 옵션 값을 두 번 넣을 수 없습니다."),
});

export const productSchema = z
  .object({
    brandId: z.number({ message: "브랜드를 선택해 주세요." }).int().positive("브랜드를 선택해 주세요."),
    categoryId: z.number({ message: "카테고리를 선택해 주세요." }).int().positive("카테고리를 선택해 주세요."),
    name: z.string().trim().min(1, "상품명을 입력해 주세요.").max(NAME_MAX, `상품명은 ${NAME_MAX}자 이하로 입력해 주세요.`),
    description: z
      .string()
      .trim()
      .min(1, "상품 설명을 입력해 주세요.")
      .max(DESCRIPTION_MAX, `상품 설명은 ${DESCRIPTION_MAX}자 이하로 입력해 주세요.`),
    images: z
      .array(z.object({ fileId: z.number(), url: z.string() }))
      .min(1, "이미지를 한 장 이상 올려 주세요.")
      .max(IMAGES_MAX, `이미지는 최대 ${IMAGES_MAX}장입니다.`),
    originalPrice: won("정가"),
    salePrice: won("판매가"),
    options: z.array(optionSchema).max(MAX_AXES, `옵션 축은 최대 ${MAX_AXES}개입니다.`),
  })
  .superRefine((v, ctx) => {
    if (v.salePrice > v.originalPrice) {
      ctx.addIssue({ code: "custom", path: ["salePrice"], message: "판매가는 정가 이하여야 합니다." });
    }
    const seen = new Set<string>();
    v.options.forEach((o, i) => {
      if (seen.has(o.name)) ctx.addIssue({ code: "custom", path: ["options", i, "name"], message: "옵션명이 겹칩니다." });
      seen.add(o.name);
    });
  });

export type ProductFormInput = z.input<typeof productSchema>;
export type ProductFormValues = z.output<typeof productSchema>;

export function toRegisterRequest(v: ProductFormValues): components["schemas"]["RegisterProductRequest"] {
  return {
    brandId: v.brandId,
    originalPrice: v.originalPrice,
    salePrice: v.salePrice,
    content: {
      name: v.name,
      description: v.description,
      categoryId: v.categoryId,
      imageFileIds: v.images.map((i) => i.fileId),
      options: v.options.map((o) => ({ optionId: null, name: o.name, values: o.values.map((name) => ({ valueId: null, name })) })),
    },
  };
}

/** 판매가 수정 다이얼로그. 정가는 바꿀 수 없으므로 인자로 받는다. */
export const priceSchema = (originalPrice: number) =>
  z.object({ salePrice: won("판매가") }).superRefine((v, ctx) => {
    if (v.salePrice > originalPrice) {
      ctx.addIssue({ code: "custom", path: ["salePrice"], message: "판매가는 정가 이하여야 합니다." });
    }
  });

export type PriceFormInput = z.input<ReturnType<typeof priceSchema>>;
export type PriceFormValues = z.output<ReturnType<typeof priceSchema>>;
