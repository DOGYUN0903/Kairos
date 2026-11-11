"use client";

import { Clock } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import { useEnd, usePause, useResume, type SellerProductSummary } from "@/features/seller/products/api";
import { PriceDialog } from "@/features/seller/products/components/PriceDialog";
import { formatDate } from "@/lib/datetime";

const TH = "px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "px-3.5 py-3 align-middle";

export function ProductTable({ rows, canManage }: { rows: SellerProductSummary[]; canManage: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="bg-surface">
            <th scope="col" className={TH}>상품</th>
            <th scope="col" className={TH}>브랜드</th>
            <th scope="col" className={`${TH} text-right`}>판매가</th>
            <th scope="col" className={TH}>상태</th>
            <th scope="col" className={`${TH} text-right`}>재고</th>
            <th scope="col" className={TH}>등록일</th>
            <th scope="col" className={`${TH} text-right`}>작업</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((p) => (
            <Row key={p.productId} p={p} canManage={canManage} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({ p, canManage }: { p: SellerProductSummary; canManage: boolean }) {
  const pause = usePause(p.productId);
  const resume = useResume(p.productId);
  const end = useEnd(p.productId);
  const [priceOpen, setPriceOpen] = useState(false);
  const [endOpen, setEndOpen] = useState(false);
  const busy = pause.isPending || resume.isPending || end.isPending;
  const ended = p.status === "ENDED";
  const endable = p.status === "ON_SALE" || p.status === "PAUSED";

  return (
    <tr className={`border-b border-border ${ended ? "text-text-3" : ""}`}>
      <td className={TD}>
        <div className="flex items-center gap-3">
          {p.thumbnailUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
            <img src={p.thumbnailUrl} alt="" className="h-14 w-14 flex-none rounded object-cover" />
          ) : (
            <div className="grid h-14 w-14 flex-none place-items-center rounded bg-surface-2 text-[10px] text-text-3">이미지 없음</div>
          )}
          <div className="min-w-0">
            <Link href={`/seller/products/${p.productId}`} className="block font-medium hover:underline">
              {p.name}
            </Link>
            <span className="text-xs text-text-2">#{p.productId}</span>
          </div>
        </div>
      </td>
      <td className={TD}>{p.brandName}</td>
      <td className={`${TD} text-right`}>
        <Money value={p.salePrice} className="font-bold" />
      </td>
      <td className={TD}>
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusChip kind="product" status={p.status} />
          {p.hasPendingChangeRequest && (
            <span className="inline-flex items-center gap-1 rounded-sm bg-surface-2 px-1.5 py-0.5 text-[11px] text-text-2">
              <Clock className="h-3 w-3" aria-hidden />
              변경 요청 검수 중
            </span>
          )}
          {p.soldOut && !ended && <span className="rounded-sm border border-border px-1.5 py-0.5 text-[11px] text-text-2">품절</span>}
        </div>
      </td>
      <td className={`${TD} text-right tabular-nums`}>{ended ? "—" : p.stockTotal}</td>
      <td className={`${TD} text-text-2`}>{formatDate(p.createdAt)}</td>
      <td className={TD}>
        <div className="flex justify-end gap-1.5 whitespace-nowrap">
          {ended ? (
            <span className="text-xs">되돌릴 수 없음</span>
          ) : (
            <>
              {p.status === "REJECTED" ? (
                <Link className="btn btn-primary btn-sm" href={`/seller/products/${p.productId}`}>
                  수정 후 재제출
                </Link>
              ) : (
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  disabled={!canManage || busy}
                  title={!canManage ? "정지 중" : undefined}
                  onClick={() => setPriceOpen(true)}
                >
                  판매가 수정
                </button>
              )}
              {p.status === "PAUSED" ? (
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  disabled={!canManage || busy}
                  title={!canManage ? "정지 중" : undefined}
                  onClick={() => resume.mutate(undefined)}
                >
                  재개
                </button>
              ) : p.status !== "REJECTED" ? (
                <button type="button" className="btn btn-ghost btn-sm" disabled={p.status !== "ON_SALE" || busy} onClick={() => pause.mutate(undefined)}>
                  일시 중지
                </button>
              ) : null}
              <button type="button" className="btn btn-danger btn-sm" disabled={!endable || busy} onClick={() => setEndOpen(true)}>
                종료
              </button>
            </>
          )}
        </div>
        {priceOpen && <PriceDialog product={p} onClose={() => setPriceOpen(false)} />}
        <ConfirmDialog
          open={endOpen}
          title="판매를 종료할까요?"
          description={`${p.name} 판매를 종료합니다. 되돌릴 수 없습니다.`}
          confirmLabel="종료"
          destructive
          onCancel={() => setEndOpen(false)}
          onConfirm={() => {
            setEndOpen(false);
            end.mutate(undefined);
          }}
        />
      </td>
    </tr>
  );
}
