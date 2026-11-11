import { z } from "zod";

/** OpenAPI: name 1..50. displayOrder 는 정수이고 화면 규칙으로 0 이상만 받는다(서버는 음수도 받지만 정렬 의미가 없다). */
export const categorySchema = z.object({
  name: z.string().trim().min(1, "카테고리 이름을 입력해 주세요.").max(50, "이름은 50자 이하로 입력해 주세요."),
  displayOrder: z
    .number({ error: "표시 순서를 숫자로 입력해 주세요." })
    .int("표시 순서는 정수여야 합니다.")
    .min(0, "표시 순서는 0 이상이어야 합니다."),
});

export type CategoryValues = z.infer<typeof categorySchema>;
