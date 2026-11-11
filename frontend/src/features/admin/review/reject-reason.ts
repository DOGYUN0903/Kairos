import { z } from "zod";

export const REASON_MAX = 500;

/** 반려 사유: 공백만은 안 되고 1..500자(OpenAPI ReasonRequest). 판매자에게 그대로 전달된다. */
export const rejectReasonSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, "반려 사유를 입력해 주세요.")
    .max(REASON_MAX, `반려 사유는 ${REASON_MAX}자 이하로 입력해 주세요.`),
});
export type RejectReasonValues = z.infer<typeof rejectReasonSchema>;

/** 수수료율(bp): 0..10000 정수(OpenAPI ApproveSellerRequest). 입력은 문자열. */
export const commissionSchema = z.object({
  commissionRateBp: z.string().transform((s, ctx) => {
    const t = s.trim();
    if (!/^\d{1,5}$/.test(t)) {
      ctx.addIssue({ code: "custom", message: "수수료율은 0~10000 사이 정수(bp)로 입력해 주세요." });
      return z.NEVER;
    }
    const n = Number(t);
    if (n > 10000) {
      ctx.addIssue({ code: "custom", message: "수수료율은 10000bp(100%) 이하여야 합니다." });
      return z.NEVER;
    }
    return n;
  }),
});
