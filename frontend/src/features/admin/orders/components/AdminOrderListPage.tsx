"use client";

import { Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { ADMIN_ORDER_PAGE_SIZE, useAdminOrders, type OrderStatus } from "@/features/admin/orders/api";
import { AdminOrderTable } from "@/features/admin/orders/components/AdminOrderTable";
import { ADMIN_ORDER_STATUSES, ORDER_NO_MAX, parseAdminOrderParams, toAdminOrderSearch, type AdminOrderParams } from "@/features/admin/orders/params";

/** URL(?status=&orderNo=&memberId=&page=)이 필터의 진실이다. 필터를 적용하면 첫 페이지로 간다. */
export function AdminOrderListPage() {
  const router = useRouter();
  const params = parseAdminOrderParams(useSearchParams());
  const { status, memberId, orderNo, page } = params;
  const q = useAdminOrders(params);

  const [statusDraft, setStatusDraft] = useState<OrderStatus | "">(status ?? "");
  const [orderNoDraft, setOrderNoDraft] = useState(orderNo ?? "");
  const [memberDraft, setMemberDraft] = useState(memberId !== undefined ? String(memberId) : "");
  const [memberError, setMemberError] = useState<string | null>(null);
  useEffect(() => {
    setStatusDraft(status ?? "");
    setOrderNoDraft(orderNo ?? "");
    setMemberDraft(memberId !== undefined ? String(memberId) : "");
    setMemberError(null);
  }, [status, orderNo, memberId]);

  const go = (next: AdminOrderParams) => {
    const qs = toAdminOrderSearch(next);
    router.push(qs ? `/admin/orders?${qs}` : "/admin/orders", { scroll: false });
  };

  const apply = (e: FormEvent) => {
    e.preventDefault();
    const member = memberDraft.trim();
    if (member !== "" && !/^[1-9]\d{0,14}$/.test(member)) {
      setMemberError("회원 ID는 숫자로 입력해 주세요.");
      return;
    }
    setMemberError(null);
    const no = orderNoDraft.trim();
    go({
      ...(statusDraft ? { status: statusDraft } : {}),
      ...(member ? { memberId: Number(member) } : {}),
      ...(no ? { orderNo: no } : {}),
      page: 0,
    });
  };

  const filtered = status !== undefined || memberId !== undefined || orderNo !== undefined;
  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-5">
      <form role="search" aria-label="주문 검색" onSubmit={apply} noValidate className="flex flex-wrap items-start gap-3">
        <div className="field w-40">
          <label htmlFor="ao-status">상태</label>
          <select id="ao-status" className="input" value={statusDraft} onChange={(e) => setStatusDraft(e.target.value as OrderStatus | "")}>
            <option value="">모든 상태</option>
            {ADMIN_ORDER_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field w-72">
          <label htmlFor="ao-orderNo">주문번호</label>
          <input
            id="ao-orderNo"
            className="input tabular-nums"
            type="text"
            maxLength={ORDER_NO_MAX}
            placeholder="정확히 일치하는 주문번호"
            autoComplete="off"
            value={orderNoDraft}
            onChange={(e) => setOrderNoDraft(e.target.value)}
          />
        </div>
        <div className="field w-40">
          <label htmlFor="ao-member">회원 ID</label>
          <input
            id="ao-member"
            className="input tabular-nums"
            type="text"
            inputMode="numeric"
            placeholder="숫자"
            autoComplete="off"
            value={memberDraft}
            aria-invalid={memberError ? true : undefined}
            aria-describedby={memberError ? "ao-member-err" : undefined}
            onChange={(e) => setMemberDraft(e.target.value)}
          />
          {memberError && (
            <p id="ao-member-err" role="alert" className="field-error">
              {memberError}
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

      <section className="card" aria-labelledby="ao-h">
        <h2 id="ao-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
          주문 {data ? `(${data.totalElements})` : ""}
        </h2>
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 주문이 없습니다." action={{ label: "첫 페이지로", onClick: () => go({ ...params, page: 0 }) }} />
          ) : filtered ? (
            <EmptyState message="조건에 맞는 주문이 없습니다." action={{ label: "필터 초기화", onClick: () => go({ page: 0 }) }} />
          ) : (
            <EmptyState message="주문이 없습니다." />
          )
        ) : (
          <>
            <AdminOrderTable rows={rows} />
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">
                {data.totalElements}건 중 {data.page * ADMIN_ORDER_PAGE_SIZE + 1}–{data.page * ADMIN_ORDER_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go({ ...params, page: p })} />
            </div>
          </>
        )}
      </section>
    </div>
  );
}
