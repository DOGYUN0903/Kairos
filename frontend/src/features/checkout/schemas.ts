import { z } from "zod";

/** 길이 제한은 docs/api/openapi.yaml 의 ShippingAddress maxLength 와 같다(receiverName 50, phone 20, zipCode 10, address1·address2·memo 200). */
export const shippingAddressSchema = z.object({
  receiverName: z.string().trim().min(1, "수령인을 입력해 주세요.").max(50, "수령인은 50자까지 입력할 수 있습니다."),
  phone: z
    .string()
    .trim()
    .min(1, "연락처를 입력해 주세요.")
    .max(20, "연락처는 20자까지 입력할 수 있습니다.")
    .regex(/^[0-9-]+$/, "숫자와 하이픈(-)만 입력할 수 있습니다."),
  zipCode: z.string().trim().min(1, "우편번호를 입력해 주세요.").max(10, "우편번호는 10자까지 입력할 수 있습니다."),
  address1: z.string().trim().min(1, "주소를 입력해 주세요.").max(200, "주소는 200자까지 입력할 수 있습니다."),
  address2: z.string().trim().min(1, "상세 주소를 입력해 주세요.").max(200, "상세 주소는 200자까지 입력할 수 있습니다."),
  memo: z
    .string()
    .trim()
    .max(200, "배송 메모는 200자까지 입력할 수 있습니다.")
    .transform((v) => (v === "" ? undefined : v)),
});

export type ShippingAddressInput = z.input<typeof shippingAddressSchema>;
export type ShippingAddressValues = z.output<typeof shippingAddressSchema>;
