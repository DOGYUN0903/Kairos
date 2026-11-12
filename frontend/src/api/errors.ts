import { ApiError, type ErrorCode } from "@/api/client";

export type ErrorKind = "field" | "form" | "toast" | "page" | "banner";

export const errorTable: Partial<Record<ErrorCode, { kind: ErrorKind; field?: string; message?: string }>> = {
  MEMBER_EMAIL_DUPLICATED: { kind: "field", field: "email" },
  AUTH_INVALID_CREDENTIALS: { kind: "form" },
  SELLER_BUSINESS_NUMBER_DUPLICATED: { kind: "field", field: "businessNumber" },
  BRAND_NAME_DUPLICATED: { kind: "field", field: "name" },
  PRODUCT_INVALID_PRICE: { kind: "field", field: "salePrice" },
  CATEGORY_NOT_LEAF: { kind: "field", field: "categoryId", message: "소분류 카테고리를 선택해 주세요." },
  BRAND_NOT_APPROVED: { kind: "field", field: "brandId", message: "승인된 브랜드만 선택할 수 있습니다." },
  FILE_NOT_OWNED: { kind: "field", field: "images", message: "직접 올린 이미지만 사용할 수 있습니다. 다시 올려 주세요." },
  PRODUCT_INVALID: { kind: "form" },
  SELLER_NOT_ACTIVE: { kind: "banner" },
  ORDER_INVALID: { kind: "form" },
  REVIEW_INVALID: { kind: "form" },
  COUPON_POLICY_INVALID: { kind: "form" },
  COUPON_ALREADY_ISSUED: { kind: "toast", message: "이미 받은 쿠폰입니다." },
  COUPON_SOLD_OUT: { kind: "toast", message: "쿠폰이 모두 소진되었습니다." },
  COUPON_POLICY_ENDED: { kind: "toast", message: "종료된 쿠폰입니다." },
  RETURN_INVALID: { kind: "form" },
  SETTLEMENT_ADJUSTMENT_INVALID: { kind: "form" },
  SETTLEMENT_PERIOD_INVALID: { kind: "form" },
  RETURN_NOT_FOUND: { kind: "page" },
  SETTLEMENT_NOT_FOUND: { kind: "page" },
  UNAUTHORIZED: { kind: "page" },
  FORBIDDEN: { kind: "page" },
  PRODUCT_NOT_FOUND: { kind: "page" },
  BRAND_NOT_FOUND: { kind: "page" },
  ORDER_NOT_FOUND: { kind: "page" },
  SELLER_ORDER_NOT_FOUND: { kind: "page" },
  CATEGORY_HAS_PRODUCTS: { kind: "toast", message: "이 카테고리에 상품이 있어 삭제할 수 없습니다. 상품을 다른 카테고리로 옮긴 뒤 다시 시도하세요." },
  CATEGORY_HAS_CHILDREN: { kind: "toast", message: "소분류가 있는 대분류는 삭제할 수 없습니다. 소분류를 먼저 삭제하세요." },
  CHANGE_REQUEST_ALREADY_PENDING: {
    kind: "toast",
    message: "이미 검수 중인 변경 요청이 있습니다. 철회한 뒤 다시 요청하세요.",
  },
  IDEMPOTENCY_KEY_REUSED: { kind: "form" },
  IDEMPOTENCY_IN_PROGRESS: { kind: "toast", message: "같은 요청이 처리 중입니다. 잠시 후 확인하세요." },
  INVALID_REQUEST: { kind: "form" },
  INTERNAL_ERROR: { kind: "toast", message: "잠시 후 다시 시도해 주세요." },
};

export function resolveError(e: unknown): { kind: ErrorKind; field?: string; message: string; traceId: string | null } {
  const error = e instanceof ApiError ? e : new ApiError(0, "INTERNAL_ERROR", "잠시 후 다시 시도해 주세요.", null);
  const entry = errorTable[error.code];
  if (!entry) {
    return { kind: "toast", message: error.message, traceId: error.traceId };
  }
  return {
    kind: entry.kind,
    ...(entry.field ? { field: entry.field } : {}),
    message: entry.message ?? error.message,
    traceId: error.traceId,
  };
}
