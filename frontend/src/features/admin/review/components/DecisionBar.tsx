"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ApproveSellerDialog } from "@/features/admin/review/components/ApproveSellerDialog";
import { RejectDialog } from "@/features/admin/review/components/RejectDialog";
import { useApprove, useReject } from "@/features/admin/review/api";
import { canReview, type Subject } from "@/features/admin/review/rows";

const CONFIRM_COPY = {
  brands: { title: "브랜드를 승인할까요?", description: "승인하면 판매자가 이 브랜드로 상품을 등록할 수 있습니다." },
  products: { title: "상품을 승인할까요?", description: "승인하면 바로 판매 중이 되어 구매자에게 보입니다." },
  "change-requests": { title: "변경 요청을 승인할까요?", description: "승인하면 상품이 요청한 내용으로 바뀌고, 사라진 옵션 조합의 SKU는 단종됩니다." },
} as const;

/** 승인·반려 버튼과 다이얼로그. 검수 대기가 아닌 건에는 버튼을 막는다. */
export function DecisionBar({ row, onDone, ready = true }: { row: Subject; onDone: (id: number) => void; /** 승인 대상 내용(상세)을 다 불러온 뒤에만 true. */ ready?: boolean }) {
  const approve = useApprove(row.tab, onDone);
  const reject = useReject(row.tab, onDone);
  const [dialog, setDialog] = useState<"approve" | "reject" | null>(null);
  const allowed = canReview(row.tab, row.status) && ready;
  const busy = approve.isPending || reject.isPending;
  const close = () => setDialog(null);

  return (
    <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border bg-surface px-5 py-4">
      {!canReview(row.tab, row.status) ? (
        <span className="mr-auto text-xs text-text-2">검수 대기 상태가 아니라 처리할 수 없습니다.</span>
      ) : (
        !ready && <span className="mr-auto text-xs text-text-2">내용을 불러온 뒤에 처리할 수 있습니다.</span>
      )}
      <button type="button" className="btn btn-danger" disabled={!allowed || busy} onClick={() => setDialog("reject")}>반려</button>
      <button type="button" className="btn btn-primary" disabled={!allowed || busy} onClick={() => setDialog("approve")}>승인</button>

      <RejectDialog
        open={dialog === "reject"}
        title={`${row.name} 반려`}
        submitting={reject.isPending}
        onCancel={close}
        onSubmit={async (reason) => {
          const ok = await reject.submit({ id: row.id, reason });
          if (ok) close();
          return ok;
        }}
      />
      {row.tab === "sellers" ? (
        <ApproveSellerDialog
          open={dialog === "approve"}
          companyName={row.name}
          submitting={approve.isPending}
          onCancel={close}
          onSubmit={async (commissionRateBp) => {
            const ok = await approve.submit({ id: row.id, commissionRateBp });
            if (ok) close();
            return ok;
          }}
        />
      ) : (
        <ConfirmDialog
          open={dialog === "approve"}
          title={CONFIRM_COPY[row.tab].title}
          description={`${row.name} — ${CONFIRM_COPY[row.tab].description}`}
          confirmLabel={approve.isPending ? "처리 중…" : "승인"}
          onCancel={() => !approve.isPending && close()}
          onConfirm={() => {
            void approve.submit({ id: row.id }).then((ok) => ok && close());
          }}
        />
      )}
    </div>
  );
}
