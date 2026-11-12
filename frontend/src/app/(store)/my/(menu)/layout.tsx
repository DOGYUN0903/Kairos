"use client";

import { Suspense, type ReactNode } from "react";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { MyPageShell } from "@/features/mypage/components/MyPageShell";

// RequireAuth 가 useSearchParams 를 쓰므로 Suspense 경계가 필요하다.
export default function MyMenuLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <MyPageShell>{children}</MyPageShell>
      </RequireAuth>
    </Suspense>
  );
}
