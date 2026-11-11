import { z } from "zod";

// OpenAPI BrandRequest: name 1..50, englishName ≤100(선택), logoFileId(선택), description ≤1000(선택).
export const brandSchema = z.object({
  name: z.string().trim().min(1, "브랜드명을 입력해 주세요.").max(50, "브랜드명은 50자 이하로 입력해 주세요."),
  englishName: z.string().trim().max(100, "영문명은 100자 이하로 입력해 주세요."),
  logoFileId: z.number().nullable(),
  description: z.string().trim().max(1000, "소개는 1000자 이하로 입력해 주세요."),
});

export type BrandValues = z.infer<typeof brandSchema>;
