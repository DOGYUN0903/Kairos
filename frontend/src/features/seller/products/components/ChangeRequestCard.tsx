"use client";

import { Clock, XCircle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { Money } from "@/components/common/Money";
import { Skeleton } from "@/components/ui/skeleton";
import { useSellerChangeRequest, useWithdrawChangeRequest, type SellerProductDetail } from "@/features/seller/products/api";
import { changedFields } from "@/features/seller/products/change-request-diff";
import { contentEditKind } from "@/features/seller/products/product-actions";
import { formatDateTime } from "@/lib/datetime";

/**
 * 내용 수정 진입점과 최근 변경 요청.
 * - 판매 중·일시 중지: 대기 중이면 "검수 중" + 요청 요약 + 철회, 최근 요청이 반려면 사유 + 요청 요약 + 다시 수정
 * - 검수 대기·반려: 직접 수정, 종료: 숨김
 */
export function ChangeRequestCard({ product, canManage }: { product: SellerProductDetail; canManage: boolean }) {
  const withdraw = useWithdrawChangeRequest(product.productId);
  const [open, setOpen] = useState(false);
  const kind = contentEditKind(product.status);
  const latest = product.latestChangeRequest ?? null;
  const shown = kind === "change-request" && latest && (latest.status === "PENDING" || latest.status === "REJECTED") ? latest : null;
  const request = useSellerChangeRequest(product.productId, shown?.changeRequestId ?? null);
  if (kind === null) return null;

  const editLabel = kind === "change-request" ? (shown?.status === "REJECTED" ? "다시 수정(재검수)" : "내용 수정(재검수)") : product.status === "REJECTED" ? "수정 후 재제출" : "내용 수정";
  const editButton = canManage ? (
    <Link className="btn btn-primary btn-sm" href={`/seller/products/${product.productId}/edit`}>
      {editLabel}
    </Link>
  ) : (
    <button type="button" className="btn btn-primary btn-sm" disabled title="정지 중">
      {editLabel}
    </button>
  );

  return (
    <section className="card" aria-labelledby="cr-h">
      <h2 id="cr-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
        {kind === "change-request" ? "변경 요청" : "내용 수정"}
      </h2>
      <div className="flex flex-col gap-4 p-5 text-sm">
        {shown ? (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-col gap-1">
                {shown.status === "PENDING" ? (
                  <span className="inline-flex w-fit items-center gap-1 rounded-sm bg-surface-2 px-1.5 py-0.5 text-[12px] text-text-2">
                    <Clock className="h-3 w-3" aria-hidden />
                    검수 중 · #CR-{shown.changeRequestId}
                  </span>
                ) : (
                  <span className="inline-flex w-fit items-center gap-1 rounded-sm bg-surface-2 px-1.5 py-0.5 text-[12px] text-danger">
                    <XCircle className="h-3 w-3" aria-hidden />
                    반려됨 · #CR-{shown.changeRequestId}
                  </span>
                )}
                <span className="text-xs text-text-2">요청 {formatDateTime(shown.requestedAt)}</span>
              </div>
              {shown.status === "PENDING" ? (
                <button type="button" className="btn btn-ghost btn-sm" disabled={!canManage || withdraw.isPending} onClick={() => setOpen(true)}>
                  {withdraw.isPending ? "철회 중…" : "변경 요청 철회"}
                </button>
              ) : (
                editButton
              )}
            </div>
            {shown.status === "REJECTED" && shown.rejectReason && <p className="text-danger">반려 사유: {shown.rejectReason}</p>}
            <RequestSummary product={product} query={request} />
            <p className="text-text-2">
              {shown.status === "PENDING"
                ? "검수 중에도 기존 내용으로 계속 판매됩니다. 철회하면 새로 요청할 수 있습니다."
                : "상품은 기존 내용으로 계속 판매 중입니다. 사유를 확인하고 다시 요청할 수 있습니다."}
            </p>
            <ConfirmDialog
              open={open}
              title="변경 요청을 철회할까요?"
              description="검수 중인 변경 요청을 철회합니다. 상품은 지금 내용 그대로 판매됩니다."
              confirmLabel="철회"
              destructive
              onCancel={() => setOpen(false)}
              onConfirm={() => {
                setOpen(false);
                if (!withdraw.isPending) withdraw.mutate({ changeRequestId: shown.changeRequestId });
              }}
            />
          </>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-text-2">
              {kind === "change-request"
                ? "상품명, 설명, 이미지, 카테고리, 옵션 구성, 정가는 변경 요청으로 재검수를 받습니다."
                : product.status === "REJECTED"
                  ? "반려 사유를 확인하고 내용을 고쳐 다시 제출하세요."
                  : "검수 대기 중인 상품의 내용을 고칠 수 있습니다. 수정하면 다시 검수 대기가 됩니다."}
            </p>
            {editButton}
          </div>
        )}
      </div>
    </section>
  );
}

function RequestSummary({ product, query }: { product: SellerProductDetail; query: ReturnType<typeof useSellerChangeRequest> }) {
  if (query.isPending) return <Skeleton className="h-10 w-full" aria-label="요청 내용 불러오는 중" />;
  if (query.isError)
    return (
      <p role="alert" className="text-danger">
        요청 내용을 불러오지 못했습니다.{" "}
        <button type="button" className="underline" onClick={() => void query.refetch()}>
          다시 시도
        </button>
      </p>
    );
  const r = query.data;
  const fields = changedFields(product, r);
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded border border-border p-3">
      <dt className="text-text-2">요청 항목</dt>
      <dd>{fields.length > 0 ? fields.join(", ") : "현재 내용과 같음"}</dd>
      {fields.includes("상품명") && (
        <>
          <dt className="text-text-2">상품명</dt>
          <dd>{r.content.name}</dd>
        </>
      )}
      {fields.includes("정가") && (
        <>
          <dt className="text-text-2">정가</dt>
          <dd>
            <Money value={product.originalPrice} className="text-text-3 line-through" /> → <Money value={r.originalPrice} />
          </dd>
        </>
      )}
      {fields.includes("옵션") && (
        <>
          <dt className="text-text-2">옵션</dt>
          <dd>{r.content.options.map((o) => `${o.name}: ${o.values.map((v) => v.name).join(", ")}`).join(" · ") || "옵션 없음"}</dd>
        </>
      )}
    </dl>
  );
}
