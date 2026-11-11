"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useAuth } from "@/auth/useAuth";
import { AuthTabs } from "@/features/auth/components/AuthTabs";
import { LoginForm } from "@/features/auth/components/LoginForm";
import { afterLoginPath, safeNext } from "@/features/auth/redirect";

function Content() {
  const next = safeNext(useSearchParams().get("next"));
  const { status, me } = useAuth();
  const router = useRouter();

  // 이미 로그인한 사용자는 머물 이유가 없다.
  useEffect(() => {
    if (status === "authenticated" && me) router.replace(afterLoginPath(me, next));
  }, [status, me, next, router]);

  if (status !== "anonymous") return <PageSkeleton rows={3} />;
  return (
    <div className="card">
      <AuthTabs current="login" next={next} />
      <LoginForm next={next} />
    </div>
  );
}

export default function LoginPage() {
  return (
    <div className="mx-auto mb-20 mt-14 w-[420px]">
      <h1 className="sr-only">로그인</h1>
      <Suspense fallback={<PageSkeleton rows={3} />}>
        <Content />
      </Suspense>
      <p className="mt-6 text-center text-[13px] text-text-2">
        비회원도 상품을 둘러볼 수 있습니다 →{" "}
        <Link href="/products" className="text-navy underline">
          상품 보기
        </Link>
      </p>
    </div>
  );
}
