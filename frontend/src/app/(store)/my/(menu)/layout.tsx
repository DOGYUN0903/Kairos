"use client";

import { Suspense, type ReactNode } from "react";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { MyPageShell } from "@/features/mypage/components/MyPageShell";
import { useMyMenuMeta } from "@/features/mypage/useMyMenuMeta";

// 메뉴 개수는 인증된 뒤에만 읽는다(RequireAuth 안쪽).
function ShellWithMeta({ children }: { children: ReactNode }) {
  return <MyPageShell meta={useMyMenuMeta()}>{children}</MyPageShell>;
}

// RequireAuth 가 useSearchParams 를 쓰므로 Suspense 경계가 필요하다.
export default function MyMenuLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <ShellWithMeta>{children}</ShellWithMeta>
      </RequireAuth>
    </Suspense>
  );
}
