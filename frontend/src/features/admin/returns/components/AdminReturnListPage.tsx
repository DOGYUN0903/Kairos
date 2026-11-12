"use client";

import { Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { ADMIN_RETURN_PAGE_SIZE, useAdminReturns } from "@/features/admin/returns/api";
import { ADMIN_RETURN_STATUSES, parseAdminReturnParams, toAdminReturnSearch, type AdminReturnParams, type AdminReturnStatus } from "@/features/admin/returns/params";
import { ReturnTable } from "@/features/seller/returns/components/ReturnTable";
import { parseId } from "@/lib/ids";

const detailHref = (returnId: number) => `/admin/returns/${returnId}`;

/** URL(?status=&sellerId=&memberId=&page=)이 필터의 진실이다. 필터를 적용하면 첫 페이지로 간다. */
export function AdminReturnListPage() {
  const router = useRouter();
  const params = parseAdminReturnParams(useSearchParams());
  const { status, sellerId, memberId, page } = params;
  const q = useAdminReturns(params);

  const [statusDraft, setStatusDraft] = useState<AdminReturnStatus | "">(status ?? "");
  const [sellerDraft, setSellerDraft] = useState(sellerId !== undefined ? String(sellerId) : "");
  const [memberDraft, setMemberDraft] = useState(memberId !== undefined ? String(memberId) : "");
  const [errors, setErrors] = useState<{ seller?: string; member?: string }>({});
  useEffect(() => {
    setStatusDraft(status ?? "");
    setSellerDraft(sellerId !== undefined ? String(sellerId) : "");
    setMemberDraft(memberId !== undefined ? String(memberId) : "");
    setErrors({});
  }, [status, sellerId, memberId]);

  const go = (next: AdminReturnParams) => {
    const qs = toAdminReturnSearch(next);
    router.push(qs ? `/admin/returns?${qs}` : "/admin/returns", { scroll: false });
  };

  const apply = (e: FormEvent) => {
    e.preventDefault();
    const seller = sellerDraft.trim();
    const member = memberDraft.trim();
    const sellerValue = seller === "" ? null : parseId(seller);
    const memberValue = member === "" ? null : parseId(member);
    const next: typeof errors = {};
    if (seller !== "" && sellerValue === null) next.seller = "판매자 ID는 숫자로 입력해 주세요.";
    if (member !== "" && memberValue === null) next.member = "회원 ID는 숫자로 입력해 주세요.";
    setErrors(next);
    if (next.seller || next.member) return;
    go({
      ...(statusDraft ? { status: statusDraft } : {}),
      ...(sellerValue !== null ? { sellerId: sellerValue } : {}),
      ...(memberValue !== null ? { memberId: memberValue } : {}),
      page: 0,
    });
  };

  const filtered = status !== undefined || sellerId !== undefined || memberId !== undefined;
  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-5">
      <form role="search" aria-label="반품 검색" onSubmit={apply} noValidate className="flex flex-wrap items-start gap-3">
        <div className="field w-48">
          <label htmlFor="ar-status">상태</label>
          <select id="ar-status" className="input" value={statusDraft} onChange={(e) => setStatusDraft(e.target.value as AdminReturnStatus | "")}>
            <option value="">모든 상태</option>
            {ADMIN_RETURN_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field w-40">
          <label htmlFor="ar-seller">판매자 ID</label>
          <input
            id="ar-seller"
            className="input tabular-nums"
            type="text"
            inputMode="numeric"
            placeholder="숫자"
            autoComplete="off"
            value={sellerDraft}
            aria-invalid={errors.seller ? true : undefined}
            aria-describedby={errors.seller ? "ar-seller-err" : undefined}
            onChange={(e) => setSellerDraft(e.target.value)}
          />
          {errors.seller && (
            <p id="ar-seller-err" role="alert" className="field-error">
              {errors.seller}
            </p>
          )}
        </div>
        <div className="field w-40">
          <label htmlFor="ar-member">회원 ID</label>
          <input
            id="ar-member"
            className="input tabular-nums"
            type="text"
            inputMode="numeric"
            placeholder="숫자"
            autoComplete="off"
            value={memberDraft}
            aria-invalid={errors.member ? true : undefined}
            aria-describedby={errors.member ? "ar-member-err" : undefined}
            onChange={(e) => setMemberDraft(e.target.value)}
          />
          {errors.member && (
            <p id="ar-member-err" role="alert" className="field-error">
              {errors.member}
            </p>
          )}
        </div>
        <div className="flex gap-2 pt-[26px]">
          <button type="submit" className="btn btn-primary">
            <Search className="mr-1 h-4 w-4" aria-hidden />
            검색
          </button>
          {filtered && (
            <button type="button" className="btn btn-ghost" onClick={() => go({ page: 0 })}>
              초기화
            </button>
          )}
        </div>
      </form>

      <section className="card" aria-labelledby="ar-h">
        <h2 id="ar-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
          반품 {data && !q.isPlaceholderData ? `(${data.totalElements})` : ""}
        </h2>
        {q.isError && data && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data || q.isPlaceholderData ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 반품이 없습니다." action={{ label: "첫 페이지로", onClick: () => go({ ...params, page: 0 }) }} />
          ) : filtered ? (
            <EmptyState message="조건에 맞는 반품이 없습니다." action={{ label: "필터 초기화", onClick: () => go({ page: 0 }) }} />
          ) : (
            <EmptyState message="반품이 없습니다." />
          )
        ) : (
          <>
            <ReturnTable rows={rows} detailHref={detailHref} showSeller />
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">
                {data.totalElements}건 중 {data.page * ADMIN_RETURN_PAGE_SIZE + 1}–{data.page * ADMIN_RETURN_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go({ ...params, page: p })} />
            </div>
          </>
        )}
      </section>
    </div>
  );
}
