"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import type { OrderItemResult } from "@/features/orders/api";
import { ItemCancelDialog } from "@/features/orders/components/ItemCancelDialog";
import { NoDeductionNotice } from "@/features/orders/components/NoDeductionNotice";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useSellerCancelItem } from "@/features/seller/orders/api";
import { queryKeys } from "@/lib/query-keys";

/** 판매자 항목 취소 창. item 이 있을 때만 열린다(닫으면 내용이 언마운트되어 사유가 새로 시작한다). */
export function SellerCancelDialog({ sellerOrderId, item, onClose }: { sellerOrderId: number; item: OrderItemResult | null; onClose: () => void }) {
  if (!item) return null;
  return <CancelBody key={item.orderItemId} sellerOrderId={sellerOrderId} item={item} onClose={onClose} />;
}

function CancelBody({ sellerOrderId, item, onClose }: { sellerOrderId: number; item: OrderItemResult; onClose: () => void }) {
  const cancel = useSellerCancelItem(sellerOrderId);
  const setBanner = useSellerBanner();
  const qc = useQueryClient();
  return (
    <ItemCancelDialog
      // 판매자 화면은 자기 상품이라 브랜드 줄을 보이지 않는다.
      item={{ ...item, brandName: null }}
      cancel={(reason, key) => cancel(item.orderItemId, reason, key)}
      notice={<NoDeductionNotice />}
      submitTone="danger"
      onSpecialError={(e) => {
        if (!(e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE")) return false;
        setBanner("판매자 상태 때문에 처리할 수 없습니다.");
        void qc.invalidateQueries({ queryKey: queryKeys.seller.me });
        onClose();
        return true;
      }}
      onClose={onClose}
    />
  );
}
