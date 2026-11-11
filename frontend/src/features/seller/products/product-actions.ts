import type { components } from "@/api/schema";

type ProductStatus = components["schemas"]["ProductStatus"];

/**
 * 상품·판매자 상태별 판매 상태 작업. 백엔드 규칙과 같다:
 * - 재개는 APPROVED 판매자만(정지 중 재개 금지)
 * - 일시 중지·종료는 정지된 판매자도 가능(판매를 줄이는 방향)
 */
export function statusActions(status: ProductStatus, canManage: boolean): { pause: boolean; resume: boolean; end: boolean } {
  return {
    pause: status === "ON_SALE",
    resume: status === "PAUSED" && canManage,
    end: status === "ON_SALE" || status === "PAUSED",
  };
}

/** 내용 수정 경로: 판매 중·일시 중지는 변경 요청(재검수), 검수 대기·반려는 직접 수정, 종료는 불가. */
export function contentEditKind(status: ProductStatus): "change-request" | "edit" | null {
  if (status === "ON_SALE" || status === "PAUSED") return "change-request";
  if (status === "PENDING_REVIEW" || status === "REJECTED") return "edit";
  return null;
}
