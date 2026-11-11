type Kind = "seller" | "brand" | "product" | "changeRequest";
type Tone = "pending" | "onsale" | "rejected" | "paused" | "ended" | "info";

// 스펙 §9-4 표 그대로.
const table: Record<Kind, Record<string, [string, Tone]>> = {
  seller: {
    PENDING: ["심사 중", "pending"],
    APPROVED: ["승인", "onsale"],
    REJECTED: ["반려", "rejected"],
    SUSPENDED: ["정지", "paused"],
  },
  brand: {
    PENDING: ["심사 중", "pending"],
    APPROVED: ["승인", "onsale"],
    REJECTED: ["반려", "rejected"],
  },
  product: {
    PENDING_REVIEW: ["검수 대기", "pending"],
    ON_SALE: ["판매 중", "onsale"],
    REJECTED: ["반려", "rejected"],
    PAUSED: ["일시 중지", "paused"],
    ENDED: ["판매 종료", "ended"],
  },
  changeRequest: {
    PENDING: ["검수 중", "pending"],
    APPROVED: ["승인", "onsale"],
    REJECTED: ["반려", "rejected"],
    WITHDRAWN: ["철회", "ended"],
  },
};

export function StatusChip({ kind, status }: { kind: Kind; status: string }) {
  const [label, tone] = table[kind][status] ?? [status, "paused"];
  return <span className={`chip chip-${tone}`}>{label}</span>;
}
