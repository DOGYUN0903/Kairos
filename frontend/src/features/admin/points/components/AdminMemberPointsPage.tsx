"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { useAdminMemberPoints } from "@/features/admin/points/api";
import { parseMemberIdParam } from "@/features/admin/points/params";
import { PointTransactionTable } from "@/features/points/components/PointTransactionTable";
import { parsePage } from "@/lib/page";
import { formatPoints } from "@/lib/money";

/** 관리자 회원 포인트 조회. ?memberId=&page= 가 진실이다. */
export function AdminMemberPointsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const rawMemberId = sp.get("memberId");
  const memberId = parseMemberIdParam(sp);
  const page = parsePage(sp.get("page"));
  const [input, setInput] = useState(rawMemberId ?? "");
  const [inputError, setInputError] = useState(false);
  const q = useAdminMemberPoints(memberId, page);
  const data = q.data;
  const invalid = rawMemberId !== null && rawMemberId !== "" && memberId === null;

  function submit(e: FormEvent) {
    e.preventDefault();
    const id = parseMemberIdParam(new URLSearchParams({ memberId: input.trim() }));
    if (id === null) {
      setInputError(true);
      return;
    }
    setInputError(false);
    router.push(`${pathname}?memberId=${id}`);
  }
  const go = (p: number) => router.push(p > 0 ? `${pathname}?memberId=${memberId}&page=${p}` : `${pathname}?memberId=${memberId}`, { scroll: false });

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-bold">회원 포인트 조회</h2>
        <p className="mt-2 text-[13px] text-text-2">회원 번호로 포인트 잔액과 내역을 확인합니다.</p>
      </div>
      <form onSubmit={submit} className="flex items-start gap-2" noValidate>
        <div className="field">
          <label htmlFor="member-id">회원 번호</label>
          <input id="member-id" className="input w-48" inputMode="numeric" value={input} onChange={(e) => setInput(e.target.value)} aria-invalid={inputError || invalid ? true : undefined} />
        </div>
        <button type="submit" className="btn btn-primary mt-[26px]">
          조회
        </button>
      </form>
      {(inputError || invalid) && (
        <p role="alert" className="text-sm text-danger">
          회원 번호를 숫자로 입력해 주세요
        </p>
      )}

      {memberId !== null &&
        (q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data ? (
          <PageSkeleton />
        ) : (
          <>
            <div className="flex flex-wrap items-center justify-between gap-4 rounded border border-border bg-surface px-6 py-5">
              <div>
                <div className="text-[13px] text-text-2">회원 {memberId} 보유 포인트</div>
                <strong className="text-[28px] font-bold tracking-tight text-navy">{formatPoints(data.balance)}</strong>
              </div>
              {data.expiringWithin30Days > 0 && <span className="chip chip-pending">30일 안에 만료 예정 {formatPoints(data.expiringWithin30Days)}</span>}
            </div>
            <h3 className="text-base font-bold">포인트 내역</h3>
            {data.transactions.content.length === 0 ? (
              data.transactions.totalPages > 0 && page > 0 ? (
                <EmptyState message="해당 페이지에 내역이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
              ) : (
                <EmptyState message="내역이 없습니다. 없는 회원 번호도 이렇게 보입니다." />
              )
            ) : (
              <>
                <PointTransactionTable rows={data.transactions.content} showOrderLink={false} />
                <Pagination page={data.transactions.page} totalPages={data.transactions.totalPages} onChange={go} />
              </>
            )}
          </>
        ))}
    </div>
  );
}
