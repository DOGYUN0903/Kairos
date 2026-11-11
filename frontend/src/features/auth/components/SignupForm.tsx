"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Info } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { useAuth } from "@/auth/useAuth";
import { useSignup } from "@/features/auth/api";
import { applyFormError } from "@/features/auth/apply-form-error";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { afterLoginPath } from "@/features/auth/redirect";
import { signupSchema, type SignupValues } from "@/features/auth/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";

export function SignupForm({ next }: { next: string | null }) {
  const { login } = useAuth();
  const router = useRouter();
  const signup = useSignup();
  const form = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: "", password: "", name: "" },
  });
  const { register, formState } = form;
  const { errors } = formState;

  const { onSubmit, submitting } = useIdempotentSubmit(form, async (values, key) => {
    try {
      await signup.mutateAsync({ values, key });
    } catch (e) {
      applyFormError(form, e);
      return;
    }
    // 같은 값으로 다시 가입을 시도하면 새 Idempotency-Key 가 필요하므로 성공 뒤 폼을 비운다.
    form.reset();
    toast({ title: "가입했습니다." });
    try {
      const me = await login(values.email, values.password);
      router.replace(afterLoginPath(me, next));
    } catch {
      router.replace(next ? `/login?next=${encodeURIComponent(next)}` : "/login");
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 p-7">
      <fieldset disabled={submitting} className="flex flex-col gap-4">
        {errors.root?.server && <FormNotice message={errors.root.server.message} />}
        <div className="field">
          <label htmlFor="s-email">이메일</label>
          <input
            id="s-email"
            className="input"
            type="email"
            autoComplete="email"
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? "s-email-err" : undefined}
            {...register("email")}
          />
          {errors.email && (
            <span id="s-email-err" className="field-error">
              {errors.email.message}
            </span>
          )}
        </div>
        <div className="field">
          <label htmlFor="s-pw">비밀번호</label>
          <input
            id="s-pw"
            className="input"
            type="password"
            autoComplete="new-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? "s-pw-err" : "s-pw-hint"}
            {...register("password")}
          />
          {errors.password ? (
            <span id="s-pw-err" className="field-error">
              {errors.password.message}
            </span>
          ) : (
            <span id="s-pw-hint" className="hint">
              8자 이상
            </span>
          )}
        </div>
        <div className="field">
          <label htmlFor="s-name">이름</label>
          <input
            id="s-name"
            className="input"
            type="text"
            autoComplete="name"
            aria-invalid={errors.name ? true : undefined}
            aria-describedby={errors.name ? "s-name-err" : undefined}
            {...register("name")}
          />
          {errors.name && (
            <span id="s-name-err" className="field-error">
              {errors.name.message}
            </span>
          )}
        </div>
        <button type="submit" className="btn btn-primary btn-block btn-lg">
          {submitting ? "가입 중…" : "가입하기"}
        </button>
      </fieldset>
      <div className="notice">
        <Info className="h-4 w-4 flex-none text-navy" aria-hidden />
        <span>
          판매를 원하시면 가입 후{" "}
          <Link href="/seller" className="text-navy underline">
            판매자 센터에서 입점 신청
          </Link>
          을 해 주세요. 관리자 심사 후 상품을 등록할 수 있습니다.
        </span>
      </div>
    </form>
  );
}
