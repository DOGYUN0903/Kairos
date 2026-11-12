"use client";

import { Info } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { PolicyFormDialog } from "@/features/coupon-policies/components/PolicyFormDialog";
import { PolicyTable } from "@/features/coupon-policies/components/PolicyTable";
import type { CouponPolicyResult } from "@/features/coupon-policies/policy-status";
import { ADMIN_POLICY_PAGE_SIZE as POLICY_PAGE_SIZE, useAdminPolicies, useCreateAdminPolicy, useEndAdminPolicy } from "@/features/admin/coupons/api";
import { POLICY_IMMUTABLE_NOTICE } from "@/features/coupon-policies/notices";
import { toast } from "@/hooks/use-toast";
import { parsePage } from "@/lib/page";

const ADMIN_POLICY_NOTICE = ["플랫폼 쿠폰 할인은 플랫폼이 부담합니다.", POLICY_IMMUTABLE_NOTICE];

/** 관리자 플랫폼 쿠폰 관리. ?page= 가 진실이다. */
export function AdminCouponsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const q = useAdminPolicies(page);
  const create = useCreateAdminPolicy();
  const end = useEndAdminPolicy();
  const [creating, setCreating] = useState(false);
  const [target, setTarget] = useState<CouponPolicyResult | null>(null);
  const [endingId, setEndingId] = useState<number | null>(null);

  const go = (p: number) => router.push(p > 0 ? `${pathname}?page=${p}` : pathname, { scroll: false });
  const data = q.data;
  const rows = data?.content ?? [];

  async function confirmEnd() {
    if (!target || endingId !== null) return;
    const policy = target;
    setEndingId(policy.policyId);
    try {
      await end(policy.policyId);
      toast({ title: "쿠폰을 종료했습니다" });
    } catch (e) {
      toast({ title: resolveError(e).message, variant: "destructive" });
    } finally {
      setEndingId(null);
      setTarget(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold">쿠폰 정책</h2>
          <p className="mt-2 text-[13px] text-text-2">플랫폼이 발급하는 쿠폰 정책입니다. 플랫폼 쿠폰 할인은 플랫폼이 부담합니다.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
          쿠폰 등록
        </button>
      </div>

      <div className="card">
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 쿠폰이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
          ) : (
            <EmptyState message="등록한 쿠폰이 없습니다." action={{ label: "쿠폰 등록", onClick: () => setCreating(true) }} />
          )
        ) : (
          <>
            <PolicyTable showType rows={rows} endingId={endingId} onEnd={(id) => setTarget(rows.find((r) => r.policyId === id) ?? null)} />
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">
                {data.totalElements}건 중 {data.page * POLICY_PAGE_SIZE + 1}–{data.page * POLICY_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
            </div>
          </>
        )}
      </div>

      <p className="flex items-start gap-1.5 text-xs text-text-2">
        <Info className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden />
        <span>&quot;기간 종료&quot;는 기간이 지났지만 아직 종료 처리하지 않은 쿠폰입니다. 종료하거나 기간이 끝나도 이미 받은 고객은 쿠폰을 사용할 수 있습니다.</span>
      </p>

      {creating && <PolicyFormDialog scope="admin" notice={ADMIN_POLICY_NOTICE} onSubmit={create} onClose={() => setCreating(false)} />}
      <ConfirmDialog
        open={target !== null}
        title="쿠폰 종료"
        description="종료하면 새로 받을 수 없습니다. 이미 받은 고객은 계속 사용할 수 있습니다."
        confirmLabel="쿠폰 종료"
        destructive
        onConfirm={() => void confirmEnd()}
        onCancel={() => endingId === null && setTarget(null)}
      />
    </div>
  );
}
