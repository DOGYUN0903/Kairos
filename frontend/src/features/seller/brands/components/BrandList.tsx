"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useBrands, type BrandDetail } from "@/features/seller/brands/api";
import { BrandCard } from "@/features/seller/brands/components/BrandCard";
import { BrandDialog } from "@/features/seller/brands/components/BrandDialog";
import { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";

export function BrandList() {
  const { canManage } = useSellerCapabilities();
  const q = useBrands();
  const router = useRouter();
  const params = useSearchParams();
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<BrandDetail | null>(null);

  // 대시보드 "브랜드 신청" 바로가기(?new=1)는 한 번만 다이얼로그를 연다.
  const wantsNew = params.get("new") === "1";
  useEffect(() => {
    if (wantsNew) {
      if (canManage) setCreateOpen(true);
      router.replace("/seller/brands");
    }
  }, [wantsNew, canManage, router]);

  const applyButton = (
    <button
      type="button"
      className="btn btn-primary"
      disabled={!canManage}
      aria-disabled={!canManage ? true : undefined}
      title={!canManage ? "정지 중" : undefined}
      onClick={() => setCreateOpen(true)}
    >
      브랜드 신청
    </button>
  );

  return (
    <section aria-labelledby="brands-h">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 id="brands-h" className="text-[15px] font-bold">
          내 브랜드
        </h2>
        {applyButton}
      </div>

      {q.isPending ? (
        <div aria-busy="true" aria-label="불러오는 중" className="grid grid-cols-3 gap-4">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-32 w-full" />
          ))}
        </div>
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <EmptyState message="아직 신청한 브랜드가 없습니다. 브랜드가 승인되어야 상품을 등록할 수 있습니다." />
      ) : (
        <ul className="grid grid-cols-3 gap-4">
          {q.data.map((b) => (
            <BrandCard key={b.brandId} brand={b} onEdit={() => (canManage ? setEditing(b) : undefined)} />
          ))}
        </ul>
      )}

      <BrandDialog mode="create" open={createOpen} onOpenChange={setCreateOpen} />
      {editing && <BrandDialog mode="edit" brand={editing} open onOpenChange={(o) => !o && setEditing(null)} />}
    </section>
  );
}
