"use client";

import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useAdminSeller } from "@/features/admin/review/api";
import { DetailShell, Facts } from "@/features/admin/review/components/DetailShell";
import { formatDateTime } from "@/lib/datetime";

/** id 로 직접 읽는다(큐의 현재 페이지와 무관). */
export function SellerDetailCard({ id, onDone }: { id: number; onDone: (id: number) => void }) {
  const q = useAdminSeller(id);
  const s = q.data;
  if (q.isError) {
    return (
      <DetailShell row={null} title="판매자 입점 신청" onDone={onDone}>
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      </DetailShell>
    );
  }
  if (!s) {
    return (
      <DetailShell row={null} title="판매자 입점 신청" onDone={onDone}>
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </DetailShell>
    );
  }
  return (
    <DetailShell row={{ tab: "sellers", id, name: s.companyName, status: s.status }} title={`${s.companyName} 입점 신청`} sub={`신청 ${formatDateTime(s.createdAt)}`} onDone={onDone}>
      <Facts
        items={[
          ["회사명", s.companyName],
          ["사업자번호", s.businessNumber],
          ["대표자", s.representativeName],
          ["연락처", s.phone],
          ["정산 계좌", `${s.bank} ${s.accountNumber} (예금주 ${s.holder})`],
        ]}
      />
      {s.rejectReason && <p className="text-xs text-danger">이전 반려 사유: {s.rejectReason}</p>}
    </DetailShell>
  );
}
