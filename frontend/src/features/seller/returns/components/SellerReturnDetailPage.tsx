"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError } from "@/api/client";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { usePollingWindow } from "@/features/orders/polling";
import { needsReturnPolling } from "@/features/returns/return-text";
import { useSellerReturn } from "@/features/seller/returns/api";
import { ReturnDetailView } from "@/features/seller/returns/components/ReturnDetailView";
import { SellerReturnActions, type ReturnNotice } from "@/features/seller/returns/components/SellerReturnActions";

/** 판매자 반품 상세. 환불 확인 중인 동안만 3초 간격으로 다시 읽는다(최대 2분, 그 뒤에는 새로고침 안내). */
export function SellerReturnDetailPage({ returnId }: { returnId: number }) {
  const [polling, setPolling] = useState(false);
  const [notice, setNotice] = useState<ReturnNotice | null>(null);
  const win = usePollingWindow(polling);
  const q = useSellerReturn(returnId, { refetchInterval: win.interval });
  const r = q.data;
  const needs = r ? needsReturnPolling(r.status) : false;
  useEffect(() => setPolling(needs), [needs]);

  if (q.isError && q.error instanceof ApiError && (q.error.code === "RETURN_NOT_FOUND" || q.error.status === 404)) notFound();

  return (
    <div className="flex flex-col gap-5">
      <Link href="/seller/returns" className="inline-flex w-fit items-center gap-1 text-[13px] text-text-2 hover:underline">
        <ChevronLeft className="h-4 w-4" aria-hidden />
        반품 목록으로
      </Link>
      {notice && r?.status === notice.whileStatus && (
        <div className={notice.tone === "warn" ? "notice notice-warn" : "notice"} role="alert">
          <span>{notice.text}</span>
        </div>
      )}
      {q.isError && !r ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !r ? (
        <PageSkeleton />
      ) : (
        <>
          {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
          <ReturnDetailView
            data={r}
            orderHref={`/seller/orders/${r.sellerOrderId}`}
            actions={
              <div className="flex flex-col gap-3">
                {needs && (
                  <p role="status" className="notice">
                    <span>
                      {win.expired ? (
                        <>
                          환불 확인이 아직 끝나지 않았습니다.{" "}
                          <button type="button" className="underline" onClick={() => void q.refetch()}>
                            새로고침
                          </button>
                        </>
                      ) : (
                        "환불을 확인하고 있습니다. 3초마다 자동으로 새로 고칩니다."
                      )}
                    </span>
                  </p>
                )}
                <SellerReturnActions data={r} onNotice={setNotice} />
              </div>
            }
          />
        </>
      )}
    </div>
  );
}
