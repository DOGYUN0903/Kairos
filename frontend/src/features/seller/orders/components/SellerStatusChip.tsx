import { StatusChip } from "@/components/common/StatusChip";
import type { SellerOrderStatus } from "@/features/seller/orders/api";

/** 판매자 화면의 칩. PAID 는 구매자 화면의 "배송 준비 중"이 아니라 시안대로 "발송 대기"다. 나머지는 공용 칩을 쓴다. */
export function SellerStatusChip({ status, allItemsReturned = false }: { status: SellerOrderStatus; allItemsReturned?: boolean }) {
  if (allItemsReturned) return <StatusChip kind="sellerOrder" status="ALL_RETURNED" />;
  if (status === "PAID") return <span className="chip chip-pending">발송 대기</span>;
  return <StatusChip kind="sellerOrder" status={status} />;
}
