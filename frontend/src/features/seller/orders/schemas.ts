import { z } from "zod";
import { CARRIERS } from "@/features/orders/carriers";

export const TRACKING_MAX = 50;
const CODES = CARRIERS.map((c) => c.code);

export const shipSchema = z.object({
  carrierCode: z.string().refine((c) => CODES.includes(c), "택배사를 선택해 주세요."),
  trackingNumber: z
    .string()
    .trim()
    .min(1, "송장번호를 입력해 주세요.")
    .max(TRACKING_MAX, `송장번호는 ${TRACKING_MAX}자 이하로 입력해 주세요.`),
});
export type ShipValues = z.infer<typeof shipSchema>;

export const SELLER_CANCEL_REASON_MAX = 500;
export const sellerCancelSchema = z.object({
  reason: z.string().trim().min(1, "취소 사유를 입력해 주세요.").max(SELLER_CANCEL_REASON_MAX, `취소 사유는 ${SELLER_CANCEL_REASON_MAX}자 이하로 입력해 주세요.`),
});
export type SellerCancelValues = z.infer<typeof sellerCancelSchema>;
