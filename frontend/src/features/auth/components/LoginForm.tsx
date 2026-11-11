"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { useAuth } from "@/auth/useAuth";
import { applyFormError } from "@/features/auth/apply-form-error";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { afterLoginPath } from "@/features/auth/redirect";
import { loginSchema, type LoginValues } from "@/features/auth/schemas";

export function LoginForm({ next }: { next: string | null }) {
  const { login } = useAuth();
  const router = useRouter();
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const { register, formState } = form;
  const { errors, isSubmitting } = formState;

  // 로그인은 리소스를 만들지 않으므로 Idempotency-Key 가 필요 없다. 이중 제출은 isSubmitting 으로 막는다.
  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const me = await login(values.email, values.password);
      router.replace(afterLoginPath(me, next));
    } catch (e) {
      applyFormError(form, e);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 p-7">
      <fieldset disabled={isSubmitting} className="flex flex-col gap-4">
        {errors.root?.server && <FormNotice message={errors.root.server.message} />}
        <div className="field">
          <label htmlFor="l-email">이메일</label>
          <input
            id="l-email"
            className="input"
            type="email"
            autoComplete="email"
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? "l-email-err" : undefined}
            {...register("email")}
          />
          {errors.email && (
            <span id="l-email-err" className="field-error">
              {errors.email.message}
            </span>
          )}
        </div>
        <div className="field">
          <label htmlFor="l-pw">비밀번호</label>
          <input
            id="l-pw"
            className="input"
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? "l-pw-err" : undefined}
            {...register("password")}
          />
          {errors.password && (
            <span id="l-pw-err" className="field-error">
              {errors.password.message}
            </span>
          )}
        </div>
        <button type="submit" className="btn btn-primary btn-block btn-lg">
          {isSubmitting ? "로그인 중…" : "로그인"}
        </button>
      </fieldset>
      <p className="text-center text-[13px] text-text-2">
        아직 회원이 아니신가요?{" "}
        <Link href={next ? `/signup?next=${encodeURIComponent(next)}` : "/signup"} className="text-navy underline">
          회원가입
        </Link>
      </p>
    </form>
  );
}
