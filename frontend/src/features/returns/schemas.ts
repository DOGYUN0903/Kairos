import { z } from "zod";

export const RETURN_DETAIL_MAX = 500;
export const RETURN_REASONS = ["CHANGE_OF_MIND", "DEFECTIVE", "WRONG_DELIVERY"] as const;

/** 반품 요청 양식: 사유 필수, 상세 내용은 선택(500자). */
export const returnRequestSchema = z.object({
  reason: z.enum(RETURN_REASONS, { error: "반품 사유를 선택해 주세요" }),
  detail: z.string().max(RETURN_DETAIL_MAX, `상세 내용은 ${RETURN_DETAIL_MAX}자 이하로 입력해 주세요`),
});
export type ReturnRequestValues = z.infer<typeof returnRequestSchema>;

/** 요청 본문. 비어 있는(공백뿐인) 상세 내용은 보내지 않는다. */
export function toReturnBody(values: ReturnRequestValues): { reason: ReturnRequestValues["reason"]; detail?: string } {
  const detail = values.detail.trim();
  return detail === "" ? { reason: values.reason } : { reason: values.reason, detail };
}
