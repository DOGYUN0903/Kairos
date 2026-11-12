"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { StatusChip } from "@/components/common/StatusChip";
import { Skeleton } from "@/components/ui/skeleton";
import { ADMIN_SETTLEMENT_PAGE_SIZE, useAdminSettlements } from "@/features/admin/settlements/api";
import { GenerateDialog } from "@/features/admin/settlements/components/GenerateDialog";
import { parseAdminSettlementParams, SETTLEMENT_STATUSES, toAdminSettlementSearch, type AdminSettlementParams, type SettlementStatus } from "@/features/admin/settlements/params";
import { periodLastDay } from "@/lib/datetime";
import { parseId } from "@/lib/ids";
import { formatKrwMinus } from "@/lib/money";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

/** URL(?sellerId=&status=&page=)이 필터의 진실이다. 필터를 적용하면 첫 페이지로 간다. */
export function AdminSettlementListPage() {
  const router = useRouter();
  const params = parseAdminSettlementParams(useSearchParams());
  const { sellerId, status, page } = params;
  const q = useAdminSettlements(params);
  const [generating, setGenerating] = useState(false);

  const [sellerDraft, setSellerDraft] = useState(sellerId !== undefined ? String(sellerId) : "");
  const [statusDraft, setStatusDraft] = useState<SettlementStatus | "">(status ?? "");
  const [sellerError, setSellerError] = useState(false);
  useEffect(() => {
    setSellerDraft(sellerId !== undefined ? String(sellerId) : "");
    setStatusDraft(status ?? "");
    setSellerError(false);
  }, [sellerId, status]);

  const go = (next: AdminSettlementParams) => {
    const qs = toAdminSettlementSearch(next);
    router.push(qs ? `/admin/settlements?${qs}` : "/admin/settlements", { scroll: false });
  };

  const apply = (e: FormEvent) => {
    e.preventDefault();
    const raw = sellerDraft.trim();
    const id = raw === "" ? null : parseId(raw);
    if (raw !== "" && id === null) {
      setSellerError(true);
      return;
    }
    setSellerError(false);
    go({ ...(id !== null ? { sellerId: id } : {}), ...(statusDraft ? { status: statusDraft } : {}), page: 0 });
  };

  const filtered = sellerId !== undefined || status !== undefined;
  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold">정산</h2>
          <p className="mt-2 text-[13px] text-text-2">판매자별 주간 정산입니다. 판매자 상태가 승인이 아니면 이름 옆에 표시합니다.</p>
        </div>
        <div className="flex gap-2">
          <Link href="/admin/settlements/adjustments" className="btn btn-ghost">
            정산 조정
          </Link>
          <button type="button" className="btn btn-primary" onClick={() => setGenerating(true)}>
            정산 생성
          </button>
        </div>
      </div>

      <div className="card">
        <form role="search" aria-label="정산 검색" onSubmit={apply} noValidate className="flex flex-wrap items-start gap-3 border-b border-border p-4">
          <div className="field w-40">
            <label htmlFor="as-seller">판매자 ID</label>
            <input
              id="as-seller"
              className="input tabular-nums"
              inputMode="numeric"
              placeholder="숫자"
              autoComplete="off"
              value={sellerDraft}
              aria-invalid={sellerError ? true : undefined}
              aria-describedby={sellerError ? "as-seller-err" : undefined}
              onChange={(e) => setSellerDraft(e.target.value)}
            />
            {sellerError && (
              <p id="as-seller-err" role="alert" className="field-error">
                판매자 ID는 숫자로 입력해 주세요.
              </p>
            )}
          </div>
          <div className="field w-40">
            <label htmlFor="as-status">상태</label>
            <select id="as-status" className="input" value={statusDraft} onChange={(e) => setStatusDraft(e.target.value as SettlementStatus | "")}>
              <option value="">전체</option>
              {SETTLEMENT_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex gap-2 pt-[26px]">
            <button type="submit" className="btn btn-primary">
              조회
            </button>
            {filtered && (
              <button type="button" className="btn btn-ghost" onClick={() => go({ page: 0 })}>
                초기화
              </button>
            )}
          </div>
        </form>

        {q.isError && data && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data || q.isPlaceholderData ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 정산이 없습니다." action={{ label: "첫 페이지로", onClick: () => go({ ...params, page: 0 }) }} />
          ) : filtered ? (
            <EmptyState message="조건에 맞는 정산이 없습니다. 판매자나 상태 조건을 바꿔 보세요." action={{ label: "필터 초기화", onClick: () => go({ page: 0 }) }} />
          ) : (
            <EmptyState message="아직 정산이 없습니다. 정산 생성으로 지난주 정산을 만들 수 있습니다" />
          )
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr>
                    <th scope="col" className={TH}>판매자</th>
                    <th scope="col" className={TH}>정산 기간</th>
                    <th scope="col" className={TH}>지급 예정일</th>
                    <th scope="col" className={TH}>상태</th>
                    <th scope="col" className={`${TH} text-right`}>합계</th>
                    <th scope="col" className={`${TH} text-right`}>지급액</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((s) => (
                    <tr key={s.settlementId}>
                      <td className={TD}>
                        {s.sellerName}
                        {s.sellerStatus !== "APPROVED" && (
                          <span className="ml-1.5 align-middle">
                            <StatusChip kind="seller" status={s.sellerStatus} />
                          </span>
                        )}
                      </td>
                      <td className={`${TD} whitespace-nowrap tabular-nums`}>
                        <Link href={`/admin/settlements/${s.settlementId}`} className="font-medium underline">
                          {s.periodStart} ~ {periodLastDay(s.periodEnd)}
                        </Link>
                      </td>
                      <td className={`${TD} whitespace-nowrap tabular-nums text-text-2`}>{s.payoutDate}</td>
                      <td className={TD}>
                        <StatusChip kind="settlement" status={s.status} />
                      </td>
                      <td className={`${TD} text-right tabular-nums`}>{formatKrwMinus(s.totalAmount)}</td>
                      <td className={`${TD} text-right font-bold tabular-nums`}>{formatKrwMinus(s.payableAmount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-3.5 pb-4 pt-3">
              <span className="text-xs text-text-2">
                총 {data.totalElements}건 · {data.page * ADMIN_SETTLEMENT_PAGE_SIZE + 1}–{data.page * ADMIN_SETTLEMENT_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go({ ...params, page: p })} />
            </div>
          </>
        )}
      </div>

      {generating && <GenerateDialog onClose={() => setGenerating(false)} />}
    </div>
  );
}
