import { DetailShell, Facts } from "@/features/admin/review/components/DetailShell";
import type { QueueRow } from "@/features/admin/review/rows";
import { formatDateTime } from "@/lib/datetime";

export function SellerDetailCard({ row, onDone }: { row: Extract<QueueRow, { tab: "sellers" }>; onDone: (id: number) => void }) {
  const s = row.raw;
  return (
    <DetailShell row={row} title={`${s.companyName} 입점 신청`} sub={`신청 ${formatDateTime(s.createdAt)}`} onDone={onDone}>
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
