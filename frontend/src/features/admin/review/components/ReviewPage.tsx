"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { useQueue, useQueueCounts } from "@/features/admin/review/api";
import { BrandDetailCard } from "@/features/admin/review/components/BrandDetailCard";
import { ChangeRequestDiff } from "@/features/admin/review/components/ChangeRequestDiff";
import { ProductDetailCard } from "@/features/admin/review/components/ProductDetailCard";
import { QueueTable } from "@/features/admin/review/components/QueueTable";
import { QueueTabs } from "@/features/admin/review/components/QueueTabs";
import { SellerDetailCard } from "@/features/admin/review/components/SellerDetailCard";
import { parseReviewParams, TAB_LABEL, toReviewSearch, type ReviewParams, type ReviewSort, type ReviewTab } from "@/features/admin/review/review-params";
import { nextSelection, type QueueRow } from "@/features/admin/review/rows";

/** URL(?tab=&id=&page=&sort=)이 상태의 진실이다. */
export function ReviewPage() {
  const router = useRouter();
  const sp = useSearchParams();
  const params = parseReviewParams(sp);
  const { tab, id, page, sort } = params;
  const queue = useQueue(tab, { sort, page });
  const { counts } = useQueueCounts();
  const rows = queue.data?.content ?? [];
  const selected = id === null ? undefined : rows.find((r) => r.id === id);

  const go = (patch: Partial<ReviewParams>, mode: "push" | "replace" = "push") => {
    const q = toReviewSearch({ ...params, ...patch });
    const url = q ? `/admin/review?${q}` : "/admin/review";
    if (mode === "push") router.push(url, { scroll: false });
    else router.replace(url, { scroll: false });
  };

  // 처리한 행은 선택에서 빼고 다음 행(없으면 이전 행)을 고른다.
  const onDone = (doneId: number) => go({ id: nextSelection(rows, doneId) }, "replace");

  return (
    <div className="flex flex-col gap-5">
      <QueueTabs tab={tab} counts={counts} onChange={(t) => go({ tab: t, id: null, page: 0 })} />
      <div className="grid items-start gap-6 min-[1100px]:grid-cols-[minmax(0,1fr)_520px]">
        <section className="card" aria-labelledby="queue-h">
          <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
            <h2 id="queue-h" className="text-[15px] font-bold">대기 중인 {TAB_LABEL[tab]}</h2>
            <label className="flex items-center gap-2 text-xs text-text-2">
              정렬
              <select
                className="input min-h-[36px] w-auto py-0 text-[13px]"
                value={sort}
                onChange={(e) => go({ sort: e.target.value === "LATEST" ? "LATEST" : ("OLDEST" satisfies ReviewSort), id: null, page: 0 })}
              >
                <option value="OLDEST">오래된 순</option>
                <option value="LATEST">최신 순</option>
              </select>
            </label>
          </div>
          {queue.isError ? (
            <ErrorState error={queue.error} onRetry={() => void queue.refetch()} />
          ) : queue.isPending ? (
            <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
              {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-12 w-full" />)}
            </div>
          ) : rows.length === 0 ? (
            <EmptyState message="대기 중인 건이 없습니다" />
          ) : (
            <>
              <QueueTable tab={tab} rows={rows} selectedId={id} onSelect={(rowId) => go({ id: rowId }, "replace")} />
              <div className="pb-4">
                <Pagination page={page} totalPages={queue.data?.totalPages ?? 0} onChange={(p) => go({ page: p, id: null })} />
              </div>
            </>
          )}
        </section>

        <div className="min-[1100px]:sticky min-[1100px]:top-4">
          <Detail tab={tab} id={id} hint={selected} onDone={onDone} />
        </div>
      </div>
    </div>
  );
}

function Detail({ tab, id, hint, onDone }: { tab: ReviewTab; id: number | null; hint: QueueRow | undefined; onDone: (id: number) => void }) {
  if (id === null) {
    return (
      <section className="card">
        <EmptyState message="왼쪽에서 건을 선택하세요" />
      </section>
    );
  }
  // key: 선택이 바뀌면 다이얼로그·폼 상태를 새로 시작한다. 상세는 큐 페이지와 무관하게 id 로 읽는다.
  switch (tab) {
    case "sellers":
      return <SellerDetailCard key={id} id={id} onDone={onDone} />;
    case "brands":
      return <BrandDetailCard key={id} id={id} onDone={onDone} />;
    case "products":
      return <ProductDetailCard key={id} id={id} hint={hint} onDone={onDone} />;
    case "change-requests":
      return <ChangeRequestDiff key={id} id={id} hint={hint} onDone={onDone} />;
  }
}
