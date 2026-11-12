import { z } from "zod";
import type { CreateCouponPolicyRequest } from "@/features/coupon-policies/policy-status";
import { fromDateTimeInput } from "@/lib/datetime";
import { parseWon, percentToBp } from "@/lib/money";

export type PolicyScope = "seller" | "admin";

export const NAME_MAX = 50;
const INT32_MAX = 2147483647;

/** 폼 입력값은 전부 문자열(입력 칸 그대로)이다. 변환·검증은 policyFormSchema 가 한다. */
export type PolicyFormInput = {
  name: string;
  couponType: "PRODUCT" | "CART";
  targetType: "ALL_PRODUCTS" | "SELLER" | "PRODUCT" | "NONE";
  targetProductId: string;
  discountType: "FIXED" | "RATE";
  discountValue: string;
  percent: string;
  maxDiscount: string;
  minOrderAmount: string;
  totalQuantity: string;
  validityDays: string;
  validFrom: string;
  validUntil: string;
};

export type PolicyFormValues = {
  name: string;
  couponType: "PRODUCT" | "CART";
  targetType: "ALL_PRODUCTS" | "SELLER" | "PRODUCT" | "NONE";
  targetProductId: number | null;
  discountType: "FIXED" | "RATE";
  /** 정액이면 원, 정률이면 bp. */
  discountValue: number;
  maxDiscount: number | null;
  minOrderAmount: number | null;
  totalQuantity: number | null;
  validityDays: number | null;
  validFrom: string;
  validUntil: string;
};

export function defaultPolicyForm(scope: PolicyScope): PolicyFormInput {
  return {
    name: "",
    couponType: "PRODUCT",
    targetType: scope === "seller" ? "SELLER" : "ALL_PRODUCTS",
    targetProductId: "",
    discountType: "FIXED",
    discountValue: "",
    percent: "",
    maxDiscount: "",
    minOrderAmount: "",
    totalQuantity: "",
    validityDays: "",
    validFrom: "",
    validUntil: "",
  };
}

/** 양의 정수(쉼표 허용)면 그 값, 아니면 null. */
function positiveInt(s: string, max = Number.MAX_SAFE_INTEGER): number | null {
  const n = parseWon(s);
  return n !== null && n >= 1 && n <= max ? n : null;
}

const str = z.string();

/** OpenAPI CreateCouponPolicyRequest 제약과 같게. scope 가 판매자/관리자의 대상·종류 규칙을 정한다. */
export function policyFormSchema(scope: PolicyScope) {
  return z
    .object({
      name: str,
      couponType: z.enum(["PRODUCT", "CART"]),
      targetType: z.enum(["ALL_PRODUCTS", "SELLER", "PRODUCT", "NONE"]),
      targetProductId: str,
      discountType: z.enum(["FIXED", "RATE"]),
      discountValue: str,
      percent: str,
      maxDiscount: str,
      minOrderAmount: str,
      totalQuantity: str,
      validityDays: str,
      validFrom: str,
      validUntil: str,
    })
    .superRefine((v, ctx) => {
      const add = (path: keyof PolicyFormInput, message: string) => ctx.addIssue({ code: "custom", path: [path], message });
      const name = v.name.trim();
      if (name.length < 1) add("name", "쿠폰 이름을 입력해 주세요.");
      else if (name.length > NAME_MAX) add("name", `쿠폰 이름은 ${NAME_MAX}자 이하로 입력해 주세요.`);

      // 종류·대상: 판매자는 PRODUCT 고정, 대상은 SELLER | PRODUCT.
      const couponType = scope === "seller" ? "PRODUCT" : v.couponType;
      if (couponType === "PRODUCT") {
        const allowed = scope === "seller" ? ["SELLER", "PRODUCT"] : ["ALL_PRODUCTS", "PRODUCT"];
        if (!allowed.includes(v.targetType)) add("targetType", "적용 대상을 선택해 주세요.");
        if (v.targetType === "PRODUCT" && positiveInt(v.targetProductId) === null) {
          add("targetProductId", scope === "seller" ? "상품을 선택해 주세요." : "상품 ID를 1 이상의 정수로 입력해 주세요.");
        }
      }

      if (v.discountType === "FIXED") {
        if (positiveInt(v.discountValue) === null) add("discountValue", "할인 금액은 1원 이상의 정수로 입력해 주세요.");
      } else {
        if (!/^\d+$/.test(v.percent.trim()) || Number(v.percent) < 1 || Number(v.percent) > 100) {
          add("percent", "할인율은 1~100의 정수로 입력해 주세요.");
        }
        if (positiveInt(v.maxDiscount) === null) add("maxDiscount", "정률 할인은 최대 할인 금액을 1원 이상의 정수로 입력해 주세요.");
      }

      if (v.minOrderAmount.trim() !== "" && positiveInt(v.minOrderAmount) === null) add("minOrderAmount", "최소 주문 금액은 1원 이상의 정수로 입력해 주세요.");
      if (v.totalQuantity.trim() !== "" && positiveInt(v.totalQuantity, INT32_MAX) === null) add("totalQuantity", "총 수량은 1 이상의 정수로 입력해 주세요.");
      if (v.validityDays.trim() !== "" && positiveInt(v.validityDays, INT32_MAX) === null) add("validityDays", "유효 일수는 1 이상의 정수로 입력해 주세요.");

      const from = fromDateTimeInput(v.validFrom);
      const until = fromDateTimeInput(v.validUntil);
      if (from === null) add("validFrom", "시작 일시를 입력해 주세요.");
      if (until === null) add("validUntil", "종료 일시를 입력해 주세요.");
      // 같은 형식의 KST 문자열이라 문자열 비교가 곧 시각 비교다.
      else if (from !== null && until <= from) add("validUntil", "종료 일시는 시작 일시보다 뒤여야 합니다.");
    })
    .transform((v): PolicyFormValues => {
      const couponType = scope === "seller" ? "PRODUCT" : v.couponType;
      const targetType = couponType === "CART" ? "NONE" : v.targetType;
      const rate = v.discountType === "RATE";
      return {
        name: v.name.trim(),
        couponType,
        targetType,
        targetProductId: targetType === "PRODUCT" ? positiveInt(v.targetProductId) : null,
        discountType: v.discountType,
        discountValue: rate ? percentToBp(Number(v.percent)) : (positiveInt(v.discountValue) as number),
        maxDiscount: rate ? positiveInt(v.maxDiscount) : null,
        minOrderAmount: positiveInt(v.minOrderAmount),
        totalQuantity: positiveInt(v.totalQuantity, INT32_MAX),
        validityDays: positiveInt(v.validityDays, INT32_MAX),
        validFrom: fromDateTimeInput(v.validFrom) as string,
        validUntil: fromDateTimeInput(v.validUntil) as string,
      };
    });
}

/** 검증을 통과한 값을 요청 본문으로. 정액이면 maxDiscount 키를 보내지 않는다. */
export function toPolicyRequest(values: PolicyFormValues, scope: PolicyScope): CreateCouponPolicyRequest {
  const couponType = scope === "seller" ? "PRODUCT" : values.couponType;
  const targetType = couponType === "CART" ? "NONE" : values.targetType;
  return {
    name: values.name,
    couponType,
    targetType,
    targetProductId: targetType === "PRODUCT" ? values.targetProductId : null,
    discountType: values.discountType,
    discountValue: values.discountValue,
    ...(values.discountType === "RATE" ? { maxDiscount: values.maxDiscount } : {}),
    minOrderAmount: values.minOrderAmount,
    totalQuantity: values.totalQuantity,
    validityDays: values.validityDays,
    validFrom: values.validFrom,
    validUntil: values.validUntil,
  };
}
