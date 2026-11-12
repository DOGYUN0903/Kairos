"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { StatusChip } from "@/components/common/StatusChip";
import { useAdminAdjustmentActions, useAdminAdjustments, type SettlementAdjustment } from "@/features/admin/settlements/api";
import { adjustmentState, canDeleteAdjustment } from "@/features/admin/settlements/adjustment-schema";
import { AdjustmentForm } from "@/features/admin/settlements/components/AdjustmentForm";
import { adjustmentTypeLabel } from "@/features/settlements/settlement-text";
import { toast } from "@/hooks/use-toast";
import { parseId } from "@/lib/ids";
import { formatSignedKrw } from "@/lib/money";
import { parsePage } from "@/lib/page";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";
export const APPLIED_BLOCKED = "이미 정산에 반영된 조정은 삭제할 수 없습니다";

/** 정산 조정. ?sellerId=&page= 가 진실이다. */
export function AdminAdjustmentsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const rawSeller = sp.get("sellerId");
  const sellerId = parseId(rawSeller);
  const page = parsePage(sp.get("page"));
  const [input, setInput] = useState(rawSeller ?? "");
  const [inputError, setInputError] = useState(false);
  useEffect(() => {
    setInput(rawSeller ?? "");
    setInputError(false);
  }, [rawSeller]);
  const invalid = rawSeller !== null && rawSeller !== "" && sellerId === null;

  function submit(e: FormEvent) {
    e.preventDefault();
    const id = parseId(input.trim());
    if (id === null) {
      setInputError(true);
      return;
    }
    setInputError(false);
    router.push(`${pathname}?sellerId=${id}`);
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-bold">정산 조정</h2>
        <p className="mt-2 text-[13px] text-text-2">판매자별 수동 조정입니다. 조정은 다음에 만들어지는 정산에 반영됩니다.</p>
      </div>
      <form onSubmit={submit} className="card flex items-start gap-2 p-4" noValidate>
        <div className="field">
          <label htmlFor="adj-seller">판매자 ID</label>
          <input id="adj-seller" className="input w-48 tabular-nums" inputMode="numeric" placeholder="예: 12" value={input} onChange={(e) => setInput(e.target.value)} aria-invalid={inputError || invalid ? true : undefined} />
        </div>
        <button type="submit" className="btn btn-primary mt-[26px]">
          조회
        </button>
        <Link href="/admin/settlements" className="btn btn-ghost mt-[26px]">
          정산 목록
        </Link>
      </form>
      {(inputError || invalid) && (
        <p role="alert" className="text-sm text-danger">
          판매자 ID를 숫자로 입력해 주세요
        </p>
      )}
      {sellerId === null ? (
        <div className="card">
          <EmptyState message="판매자를 선택해 주세요. 판매자 ID를 입력하면 그 판매자의 정산 조정을 볼 수 있습니다." />
        </div>
      ) : (
        <SellerAdjustments key={sellerId} sellerId={sellerId} page={page} onPage={(p) => router.push(p > 0 ? `${pathname}?sellerId=${sellerId}&page=${p}` : `${pathname}?sellerId=${sellerId}`, { scroll: false })} />
      )}
    </div>
  );
}

function SellerAdjustments({ sellerId, page, onPage }: { sellerId: number; page: number; onPage: (p: number) => void }) {
  const q = useAdminAdjustments(sellerId, page);
  const { remove } = useAdminAdjustmentActions(sellerId);
  const [adding, setAdding] = useState(false);
  const [target, setTarget] = useState<SettlementAdjustment | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const data = q.data;

  // 삭제는 키 없는 멱등 DELETE 다. 처리 중에는 다시 보내지 않는다.
  const confirmDelete = async () => {
    if (!target || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setNotice(null);
    try {
      await remove(target.adjustmentId);
      toast({ title: "조정을 삭제했습니다" });
    } catch (e) {
      // 목록은 api 훅이 이미 다시 읽는다.
      if (e instanceof ApiError && e.code === "SETTLEMENT_ADJUSTMENT_APPLIED") setNotice(APPLIED_BLOCKED);
      else toast({ title: resolveError(e).message, variant: "destructive" });
    } finally {
      inFlight.current = false;
      setBusy(false);
      setTarget(null);
    }
  };

  return (
    <>
      {notice && (
        <div role="alert" className="notice notice-warn">
          <span>{notice}</span>
        </div>
      )}
      {adding && <AdjustmentForm sellerId={sellerId} onDone={() => setAdding(false)} onCancel={() => setAdding(false)} />}
      <section className="card" aria-label="조정 목록">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <h3 className="text-[15px] font-bold">판매자 {sellerId} 조정 목록</h3>
          {!adding && (
            <button type="button" className="btn btn-primary" onClick={() => setAdding(true)}>
              조정 등록
            </button>
          )}
        </div>
        {q.isError && data && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data || q.isPlaceholderData ? (
          <PageSkeleton />
        ) : data.content.length === 0 ? (
          data.totalPages > 0 && page > 0 ? (
            <EmptyState message="해당 페이지에 조정이 없습니다." action={{ label: "첫 페이지로", onClick: () => onPage(0) }} />
          ) : (
            <EmptyState message="등록된 조정이 없습니다." />
          )
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr>
                    <th scope="col" className={TH}>종류</th>
                    <th scope="col" className={`${TH} text-right`}>금액</th>
                    <th scope="col" className={TH}>사유</th>
                    <th scope="col" className={TH}>근거 주문</th>
                    <th scope="col" className={TH}>반영</th>
                    <th scope="col" className={TH}><span className="sr-only">작업</span></th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((a) => (
                    <tr key={a.adjustmentId}>
                      <td className={`${TD} whitespace-nowrap`}>{adjustmentTypeLabel(a.type)}</td>
                      <td className={`${TD} text-right tabular-nums`}>{formatSignedKrw(a.amount)}</td>
                      <td className={TD}>{a.reason}</td>
                      <td className={`${TD} whitespace-nowrap tabular-nums`}>{a.refSellerOrderId != null ? `판매자 주문 ${a.refSellerOrderId}` : "—"}</td>
                      <td className={TD}>
                        {a.settlementId != null ? (
                          <Link href={`/admin/settlements/${a.settlementId}`} className="underline">
                            <StatusChip kind="adjustment" status={adjustmentState(a)} /> 정산 {a.settlementId}
                          </Link>
                        ) : (
                          <StatusChip kind="adjustment" status={adjustmentState(a)} />
                        )}
                      </td>
                      <td className={`${TD} text-right`}>
                        {canDeleteAdjustment(a) && (
                          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => setTarget(a)}>
                            삭제
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-3.5 pb-4 pt-3">
              <span className="text-xs text-text-2">총 {data.totalElements}건</span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={onPage} />
            </div>
          </>
        )}
      </section>
      <ConfirmDialog
        open={target !== null}
        title="조정 삭제"
        description={target ? `${adjustmentTypeLabel(target.type)} ${formatSignedKrw(target.amount)} · ${target.reason} — 이 조정을 삭제할까요? 아직 정산에 반영되지 않은 수동 조정만 삭제할 수 있습니다.` : ""}
        confirmLabel="삭제"
        destructive
        onConfirm={() => void confirmDelete()}
        onCancel={() => !busy && setTarget(null)}
      />
    </>
  );
}
