import { z } from "zod";

export const CANCEL_REASON_MAX = 500;

/** 항목 취소 사유(필수, 500자). 구매자·판매자·관리자 취소가 함께 쓴다. */
export const cancelReasonSchema = z.object({
  reason: z.string().trim().min(1, "취소 사유를 입력해 주세요.").max(CANCEL_REASON_MAX, `취소 사유는 ${CANCEL_REASON_MAX}자 이하로 입력해 주세요.`),
});
export type CancelReasonValues = z.infer<typeof cancelReasonSchema>;
