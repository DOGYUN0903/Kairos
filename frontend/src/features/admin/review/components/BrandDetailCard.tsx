import { DetailShell, Facts } from "@/features/admin/review/components/DetailShell";
import type { QueueRow } from "@/features/admin/review/rows";
import { formatDateTime } from "@/lib/datetime";

export function BrandDetailCard({ row, onDone }: { row: Extract<QueueRow, { tab: "brands" }>; onDone: (id: number) => void }) {
  const b = row.raw;
  return (
    <DetailShell row={row} title={`${b.name} 브랜드 신청`} sub={`판매자 #${b.sellerId} · 신청 ${formatDateTime(b.createdAt)}`} onDone={onDone}>
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
