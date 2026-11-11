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
    .array(z.string().trim().min(1, "옵션 값을 입력해 주세요.").max(OPTION_VALUE_MAX, `옵션 값은 ${OPTION_VALUE_MAX}자 이하로 입력해 주세요.`))
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
    options: z
      .array(optionSchema)
      .max(MAX_AXES, `옵션 축은 최대 ${MAX_AXES}개입니다.`)
      .superRefine((options, ctx) => {
        const seen = new Set<string>();
        options.forEach((o, i) => {
          if (seen.has(o.name)) ctx.addIssue({ code: "custom", path: [i, "name"], message: "옵션명이 겹칩니다." });
          seen.add(o.name);
        });
      }),
  })
  // 다른 필드가 아직 틀려도 두 가격만 올바르면 바로 검사한다(객체 refine 은 기본적으로 전체가 통과해야 돈다).
  .refine((v) => v.salePrice <= v.originalPrice, {
    path: ["salePrice"],
    message: "판매가는 정가 이하여야 합니다.",
    when: ({ issues }) => !issues.some((i) => i.path?.[0] === "originalPrice" || i.path?.[0] === "salePrice"),
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

/** 0 이상 정수 입력(추가 금액·재고). */
const nonNegInt = (message: string) =>
  z.string().transform((s, ctx) => {
    const n = parseWon(s);
    if (n === null) {
      ctx.addIssue({ code: "custom", message });
      return z.NEVER;
    }
    return n;
  });

export const STOCK_MAX = 2_147_483_647; // int32

/** SKU 행 편집(추가 금액·재고·판매 여부). */
export const skuSchema = z.object({
  extraPrice: nonNegInt("추가 금액은 0원 이상의 정수로 입력해 주세요."),
  stock: nonNegInt("재고는 0 이상의 정수로 입력해 주세요.").refine((n) => n <= STOCK_MAX, "재고가 너무 큽니다."),
  onSale: z.boolean(),
});
export type SkuFormInput = z.input<typeof skuSchema>;
export type SkuFormValues = z.output<typeof skuSchema>;

type CurrentOption = components["schemas"]["ProductOption"];

/**
 * 폼의 옵션(이름만)을 변경 요청 옵션으로 바꾼다. 이름이 같은 기존 옵션·값은 ID 를 유지해 SKU 가 남는다.
 * 축 이름을 바꾸면 새 옵션으로 취급되어 그 축의 기존 SKU 는 모두 단종된다(skuImpact 로 미리 보여 준다).
 */
export function toChangeOptions(
  options: { name: string; values: string[] }[],
  current: CurrentOption[],
): { optionId: number | null; name: string; values: { valueId: number | null; name: string }[] }[] {
  return options.map((o) => {
    const prev = current.find((c) => c.name === o.name.trim());
    return {
      optionId: prev?.optionId ?? null,
      name: o.name.trim(),
      values: o.values.map((v) => ({ valueId: prev?.values.find((pv) => pv.name === v.trim())?.valueId ?? null, name: v.trim() })),
    };
  });
}

export function toContentRequest(v: ProductFormValues, current: CurrentOption[]): components["schemas"]["ProductContentRequest"] {
  return {
    name: v.name,
    description: v.description,
    categoryId: v.categoryId,
    imageFileIds: v.images.map((i) => i.fileId),
    options: toChangeOptions(v.options, current),
  };
}

export function toChangeRequest(v: ProductFormValues, current: CurrentOption[]): components["schemas"]["SubmitChangeRequestRequest"] {
  return { originalPrice: v.originalPrice, content: toContentRequest(v, current) };
}
