type Kind = "seller" | "brand" | "product" | "changeRequest" | "order" | "sellerOrder" | "orderItem" | "payment" | "coupon" | "couponPolicy" | "reply" | "return" | "settlement" | "adjustment";
import { returnStatusLabel } from "@/features/returns/return-text";
import { settlementStatusLabel } from "@/features/settlements/settlement-text";

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
  order: {
    PENDING_PAYMENT: ["결제 대기", "pending"],
    PAID: ["결제 완료", "info"],
    COMPLETED: ["구매 확정", "onsale"],
    CANCELED: ["취소", "ended"],
  },
  sellerOrder: {
    PENDING_PAYMENT: ["결제 대기", "pending"],
    PAID: ["배송 준비 중", "info"],
    SHIPPING: ["배송 중", "info"],
    DELIVERED: ["배송 완료", "onsale"],
    CONFIRMED: ["구매 확정", "onsale"],
    CANCELED: ["취소", "ended"],
  },
  orderItem: {
    ORDERED: ["주문 완료", "info"],
    CANCEL_REQUESTED: ["취소 확인 중", "pending"],
    CANCELED: ["취소", "ended"],
    RETURN_REQUESTED: ["반품 요청됨", "pending"],
    RETURN_APPROVED: ["반품 승인 · 수거 대기", "info"],
    RETURN_REFUNDING: ["환불 확인 중", "pending"],
    RETURNED: ["반품 완료", "ended"],
  },
  payment: {
    READY: ["결제 대기", "pending"],
    IN_PROGRESS: ["확인 중", "pending"],
    UNKNOWN: ["확인 중", "pending"],
    APPROVED: ["승인", "onsale"],
    PARTIAL_CANCELED: ["부분 환불", "paused"],
    CANCELED: ["전액 환불", "ended"],
    FAILED: ["실패", "rejected"],
  },
  coupon: {
    AVAILABLE: ["사용 가능", "onsale"],
    USED: ["사용 완료", "ended"],
    EXPIRED: ["기간 만료", "paused"],
  },
  couponPolicy: {
    ACTIVE: ["진행 중", "onsale"],
    ENDED: ["종료", "ended"],
    PERIOD_OVER: ["기간 종료", "paused"],
  },
  reply: {
    UNANSWERED: ["미답변", "pending"],
  },
  return: {
    REQUESTED: [returnStatusLabel("REQUESTED"), "pending"],
    APPROVED: [returnStatusLabel("APPROVED"), "info"],
    REFUNDING: [returnStatusLabel("REFUNDING"), "pending"],
    COMPLETED: [returnStatusLabel("COMPLETED"), "onsale"],
    REJECTED: [returnStatusLabel("REJECTED"), "rejected"],
    WITHDRAWN: [returnStatusLabel("WITHDRAWN"), "ended"],
    CANCELED_BY_ADMIN: [returnStatusLabel("CANCELED_BY_ADMIN"), "ended"],
  },
  settlement: {
    GENERATED: [settlementStatusLabel("GENERATED"), "pending"],
    CONFIRMED: [settlementStatusLabel("CONFIRMED"), "info"],
    PAID: [settlementStatusLabel("PAID"), "onsale"],
  },
  adjustment: {
    PENDING: ["다음 정산에 반영 예정", "pending"],
    APPLIED: ["정산에 반영됨", "ended"],
  },
};

export function StatusChip({ kind, status }: { kind: Kind; status: string }) {
  const [label, tone] = table[kind][status] ?? [status, "paused"];
  return <span className={`chip chip-${tone}`}>{label}</span>;
}
