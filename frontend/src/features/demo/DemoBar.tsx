"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/auth/useAuth";
import { afterLoginPath } from "@/features/auth/redirect";
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from "@/features/demo/accounts";
import { reseedDemo } from "@/features/demo/api";
import { toast } from "@/hooks/use-toast";

type Role = keyof typeof DEMO_ACCOUNTS;

const SWITCHES: { role: Role; label: string }[] = [
  { role: "buyer", label: "구매자로 전환" },
  { role: "seller", label: "판매자로 전환" },
  { role: "admin", label: "관리자로 전환" },
];

const btn = "rounded px-2 py-0.5 text-xs hover:bg-border disabled:opacity-50";

export function DemoBar() {
  const { login, logout } = useAuth();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  // 일반 로그인 API 만 쓴다(백도어 없음). login 이 쿼리 캐시를 비운다.
  async function run(task: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    try {
      await task();
    } catch {
      toast({ title: "데모 작업에 실패했습니다.", variant: "destructive" });
    } finally {
      setBusy(false);
    }
  }

  const switchTo = (role: Role) =>
    run(async () => {
      await logout();
      const me = await login(DEMO_ACCOUNTS[role], DEMO_PASSWORD);
      router.push(afterLoginPath(me, null));
    });

  const reseed = () =>
    run(async () => {
      await logout();
      await login(DEMO_ACCOUNTS.admin, DEMO_PASSWORD);
      const r = await reseedDemo();
      const total = r.members + r.sellers + r.brands + r.categories + r.products + r.pendingReviews;
      toast({ title: `데모 데이터 ${total}건을 보충했습니다.`, description: `상품 ${r.products}건, 검수 대기 ${r.pendingReviews}건` });
      await queryClient.invalidateQueries();
    });

  return (
    <div className="fixed inset-x-0 bottom-0 z-50 flex h-8 items-center gap-2 bg-surface-2 px-4 text-xs" role="region" aria-label="데모 바">
      <span className="font-semibold">데모</span>
      {SWITCHES.map((s) => (
        <button key={s.role} type="button" className={btn} disabled={busy} onClick={() => switchTo(s.role)}>
          {s.label}
        </button>
      ))}
      <button type="button" className={btn} disabled={busy} onClick={reseed}>
        데모 데이터 보충
      </button>
    </div>
  );
}

export default DemoBar;
