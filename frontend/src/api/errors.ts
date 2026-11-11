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
  UNAUTHORIZED: { kind: "page" },
  FORBIDDEN: { kind: "page" },
  PRODUCT_NOT_FOUND: { kind: "page" },
  BRAND_NOT_FOUND: { kind: "page" },
  CATEGORY_HAS_PRODUCTS: { kind: "toast" },
  CATEGORY_HAS_CHILDREN: { kind: "toast" },
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
