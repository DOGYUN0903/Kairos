"use client";

import { Clock } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useWithdrawChangeRequest, type SellerProductDetail } from "@/features/seller/products/api";
import { contentEditKind } from "@/features/seller/products/product-actions";

/**
 * 내용 수정 진입점. 판매 중·일시 중지는 변경 요청(재검수), 검수 대기·반려는 직접 수정, 종료는 숨김.
 * 대기 중인 변경 요청이 있으면 "검수 중" 칩과 철회 버튼(판매자용 변경 요청 조회 API 가 없어 번호만 보인다).
 */
export function ChangeRequestCard({ product, canManage }: { product: SellerProductDetail; canManage: boolean }) {
  const withdraw = useWithdrawChangeRequest(product.productId);
  const [open, setOpen] = useState(false);
  const kind = contentEditKind(product.status);
  if (kind === null) return null;
  const pendingId = product.pendingChangeRequestId ?? null;

  return (
    <section className="card" aria-labelledby="cr-h">
      <h2 id="cr-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
        {kind === "change-request" ? "변경 요청" : "내용 수정"}
      </h2>
      <div className="flex flex-wrap items-center justify-between gap-3 p-5 text-sm">
        {pendingId !== null ? (
          <>
            <div className="flex flex-col gap-1">
              <span className="inline-flex w-fit items-center gap-1 rounded-sm bg-surface-2 px-1.5 py-0.5 text-[12px] text-text-2">
                <Clock className="h-3 w-3" aria-hidden />
                검수 중 · #CR-{pendingId}
              </span>
              <p className="text-text-2">검수 중에도 기존 내용으로 계속 판매됩니다. 철회하면 새로 요청할 수 있습니다.</p>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" disabled={!canManage || withdraw.isPending} onClick={() => setOpen(true)}>
              {withdraw.isPending ? "철회 중…" : "변경 요청 철회"}
            </button>
            <ConfirmDialog
              open={open}
              title="변경 요청을 철회할까요?"
              description="검수 중인 변경 요청을 철회합니다. 상품은 지금 내용 그대로 판매됩니다."
              confirmLabel="철회"
              destructive
              onCancel={() => setOpen(false)}
              onConfirm={() => {
                setOpen(false);
                if (!withdraw.isPending) withdraw.mutate({ changeRequestId: pendingId });
              }}
            />
          </>
        ) : (
          <>
            <p className="text-text-2">
              {kind === "change-request"
                ? "상품명, 설명, 이미지, 카테고리, 옵션 구성, 정가는 변경 요청으로 재검수를 받습니다."
                : product.status === "REJECTED"
                  ? "반려 사유를 확인하고 내용을 고쳐 다시 제출하세요."
                  : "검수 대기 중인 상품의 내용을 고칠 수 있습니다. 수정하면 다시 검수 대기가 됩니다."}
            </p>
            {canManage ? (
              <Link className="btn btn-primary btn-sm" href={`/seller/products/${product.productId}/edit`}>
                {kind === "change-request" ? "내용 수정(재검수)" : product.status === "REJECTED" ? "수정 후 재제출" : "내용 수정"}
              </Link>
            ) : (
              <button type="button" className="btn btn-primary btn-sm" disabled title="정지 중">
                {kind === "change-request" ? "내용 수정(재검수)" : "내용 수정"}
              </button>
            )}
          </>
        )}
      </div>
    </section>
  );
}
