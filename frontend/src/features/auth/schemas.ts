import { z } from "zod";

// OpenAPI SignupRequest / 백엔드 AuthRequests.kt 제약과 같게 둔다: password 8..72, name 1..50.
export const loginSchema = z.object({
  email: z.string().trim().email("이메일 형식이 올바르지 않습니다."),
  password: z.string().min(8, "비밀번호는 8자 이상입니다."),
});

export const signupSchema = loginSchema.extend({
  password: z.string().min(8, "비밀번호는 8자 이상입니다.").max(72, "비밀번호는 72자 이하입니다."),
  name: z.string().trim().min(1, "이름을 입력하세요.").max(50, "이름은 50자 이하입니다."),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type SignupValues = z.infer<typeof signupSchema>;
