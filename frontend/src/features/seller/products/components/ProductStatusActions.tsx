"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useEnd, usePause, useResume, type SellerProductDetail } from "@/features/seller/products/api";
import { statusActions } from "@/features/seller/products/product-actions";

/** 일시 중지·재개·종료. 정지된 판매자는 재개만 못 한다(중지·종료는 가능). */
export function ProductStatusActions({ product, canManage }: { product: SellerProductDetail; canManage: boolean }) {
  const pause = usePause(product.productId);
  const resume = useResume(product.productId);
  const end = useEnd(product.productId);
  const [endOpen, setEndOpen] = useState(false);
  const busy = pause.isPending || resume.isPending || end.isPending;
  const can = statusActions(product.status, canManage);

  if (product.status === "ENDED") return <p className="text-sm text-text-3">판매가 종료된 상품입니다. 되돌릴 수 없습니다.</p>;
  if (!can.pause && !can.resume && !can.end) return null; // 검수 대기·반려

  return (
    <div className="flex flex-wrap items-center gap-2">
      {product.status === "PAUSED" ? (
        <button
          type="button"
          className="btn btn-primary btn-sm"
          disabled={!can.resume || busy}
          title={!canManage ? "정지 중에는 재개할 수 없습니다" : undefined}
          onClick={() => resume.mutate(undefined)}
        >
          판매 재개
        </button>
      ) : (
        <button type="button" className="btn btn-ghost btn-sm" disabled={!can.pause || busy} onClick={() => pause.mutate(undefined)}>
          일시 중지
        </button>
      )}
      <button type="button" className="btn btn-danger btn-sm" disabled={!can.end || busy} onClick={() => setEndOpen(true)}>
        판매 종료
      </button>
      <ConfirmDialog
        open={endOpen}
        title="판매를 종료할까요?"
        description={`${product.name} 판매를 종료합니다. 종료한 상품은 다시 판매하거나 수정할 수 없습니다.`}
        confirmLabel="종료"
        destructive
        onCancel={() => setEndOpen(false)}
        onConfirm={() => {
          setEndOpen(false);
          if (!end.isPending) end.mutate(undefined);
        }}
      />
    </div>
  );
}
