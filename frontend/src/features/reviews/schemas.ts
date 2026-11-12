import { z } from "zod";

export const REVIEW_CONTENT_MIN = 10;
export const REVIEW_CONTENT_MAX = 1000;
export const REVIEW_IMAGE_MAX = 3;

/** 서버와 같은 기준: 앞뒤 공백을 뺀 UTF-16 코드 단위 수(JS length = Kotlin String.length). */
export function contentLength(raw: string): number {
  return raw.trim().length;
}

export const reviewFormSchema = z.object({
  rating: z
    .number({ error: "별점을 선택해 주세요" })
    .int("별점을 선택해 주세요")
    .min(1, "별점을 선택해 주세요")
    .max(5, "별점은 5점까지입니다"),
  content: z.string().superRefine((raw, ctx) => {
    const n = contentLength(raw);
    if (n < REVIEW_CONTENT_MIN) ctx.addIssue({ code: "custom", message: "10자 이상 입력해 주세요" });
    else if (n > REVIEW_CONTENT_MAX) ctx.addIssue({ code: "custom", message: "1,000자 이하로 입력해 주세요" });
  }),
  imageFileIds: z.array(z.number().int()).max(REVIEW_IMAGE_MAX, "사진은 3장까지 올릴 수 있습니다"),
});
export type ReviewFormValues = z.infer<typeof reviewFormSchema>;
