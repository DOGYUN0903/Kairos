"use client";

import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useAdminBrand } from "@/features/admin/review/api";
import { DetailShell, Facts } from "@/features/admin/review/components/DetailShell";
import { formatDateTime } from "@/lib/datetime";

/** id 로 직접 읽는다(큐의 현재 페이지와 무관). */
export function BrandDetailCard({ id, onDone }: { id: number; onDone: (id: number) => void }) {
  const q = useAdminBrand(id);
  const b = q.data;
  if (q.isError) {
    return (
      <DetailShell row={null} title="브랜드 신청" onDone={onDone}>
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      </DetailShell>
    );
  }
  if (!b) {
    return (
      <DetailShell row={null} title="브랜드 신청" onDone={onDone}>
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3">
          <Skeleton className="h-20 w-20" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      </DetailShell>
    );
  }
  return (
    <DetailShell row={{ tab: "brands", id, name: b.name, status: b.status }} title={`${b.name} 브랜드 신청`} sub={`판매자 #${b.sellerId} · 신청 ${formatDateTime(b.createdAt)}`} onDone={onDone}>
      <div className="flex items-center gap-4">
        {b.logoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
          <img src={b.logoUrl} alt={`${b.name} 로고`} className="h-20 w-20 flex-none rounded border border-border object-cover" />
        ) : (
          <div className="grid h-20 w-20 flex-none place-items-center rounded border border-border bg-surface-2 text-xs text-text-3">로고 없음</div>
        )}
        <Facts items={[["이름", b.name], ["영문명", b.englishName || "-"]]} />
      </div>
      <div>
        <h3 className="mb-1 text-xs font-medium text-text-2">소개</h3>
        <p className="whitespace-pre-wrap">{b.description || "-"}</p>
      </div>
      {b.rejectReason && <p className="text-xs text-danger">이전 반려 사유: {b.rejectReason}</p>}
    </DetailShell>
  );
}
