import { z } from "zod";

export const REPLY_MAX = 500;

/** 판매자 답글: 앞뒤 공백을 뺀 1~500자(공백만은 안 된다). 서버는 덮어쓰기라 같은 규칙을 화면에서도 막는다. */
export const replySchema = z.object({
  content: z
    .string()
    .trim()
    .min(1, "답글 내용을 입력해 주세요. 공백만 입력할 수 없습니다.")
    .max(REPLY_MAX, `${REPLY_MAX}자 이하로 입력해 주세요.`),
});
export type ReplyFormValues = z.infer<typeof replySchema>;
