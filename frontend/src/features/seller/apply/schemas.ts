import { z } from "zod";

/** 사업자번호는 입력 중 하이픈·공백을 제거해 숫자 10자리만 통과시킨다. 서버 전송 시에는 formatBusinessNumber 로 000-00-00000 으로 되돌린다. */
export const applySchema = z.object({
  companyName: z.string().trim().min(1, "회사명을 입력해 주세요."),
  businessNumber: z
    .string()
    .transform((s) => s.replace(/[-\s]/g, ""))
    .pipe(z.string().regex(/^\d{10}$/, "사업자등록번호 10자리를 입력해 주세요.")),
  representativeName: z.string().trim().min(1, "대표자명을 입력해 주세요."),
  phone: z.string().trim().min(1, "연락처를 입력해 주세요."),
  bank: z.string().trim().min(1, "정산 은행을 선택해 주세요."),
  accountNumber: z.string().trim().min(1, "계좌번호를 입력해 주세요."),
  holder: z.string().trim().min(1, "예금주를 입력해 주세요."),
});

export type ApplyValues = z.infer<typeof applySchema>;

/** OpenAPI 는 `^\d{3}-\d{2}-\d{5}$` 를 요구한다. */
export function formatBusinessNumber(digits: string): string {
  const d = digits.replace(/[-\s]/g, "");
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
}

export const BANKS = ["국민은행", "신한은행", "우리은행", "하나은행", "농협은행", "기업은행", "카카오뱅크", "토스뱅크"] as const;
