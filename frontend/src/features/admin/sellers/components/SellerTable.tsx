"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { StatusChip } from "@/components/common/StatusChip";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useSuspend, useUnsuspend, type AdminSeller } from "@/features/admin/sellers/api";
import { sellerActions } from "@/features/admin/sellers/actions";
import { SuspendDialog } from "@/features/admin/sellers/components/SuspendDialog";
import { formatDateTime } from "@/lib/datetime";
import { formatBp } from "@/lib/money";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

export function SellerTable({ rows }: { rows: AdminSeller[] }) {
  const suspend = useSuspend();
  const unsuspend = useUnsuspend();
  const [suspendTarget, setSuspendTarget] = useState<AdminSeller | null>(null);
  const [resumeTarget, setResumeTarget] = useState<AdminSeller | null>(null);
  const [detail, setDetail] = useState<AdminSeller | null>(null);

  return (
    <>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th scope="col" className={TH}>회사명</th>
              <th scope="col" className={TH}>사업자번호</th>
              <th scope="col" className={TH}>상태</th>
              <th scope="col" className={TH}>수수료</th>
              <th scope="col" className={TH}>정지 사유</th>
              <th scope="col" className={TH}><span className="sr-only">작업</span></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((s) => {
              const act = sellerActions(s.status);
              return (
                <tr key={s.sellerId} className="hover:bg-surface">
                  <td className={TD}>
                    <button type="button" className="font-medium underline-offset-2 hover:underline" onClick={() => setDetail(s)}>{s.companyName}</button>
                  </td>
                  <td className={`${TD} whitespace-nowrap text-text-2`}>{s.businessNumber}</td>
                  <td className={TD}><StatusChip kind="seller" status={s.status} /></td>
                  <td className={`${TD} whitespace-nowrap`}>{s.status === "APPROVED" || s.status === "SUSPENDED" ? formatBp(s.commissionRateBp) : <span className="text-text-3">-</span>}</td>
                  <td className={`${TD} max-w-[260px] break-words text-text-2`}>{s.suspendReason ?? <span className="text-text-3">-</span>}</td>
                  <td className={`${TD} whitespace-nowrap`}>
                    <span className="flex gap-1.5">
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setDetail(s)} aria-label={`${s.companyName} 상세`}>상세</button>
                      {act.suspend && <button type="button" className="btn btn-ghost btn-sm text-danger" aria-label={`${s.companyName} 정지`} onClick={() => setSuspendTarget(s)}>정지</button>}
                      {act.unsuspend && <button type="button" className="btn btn-ghost btn-sm" aria-label={`${s.companyName} 정지 해제`} onClick={() => setResumeTarget(s)}>해제</button>}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <SuspendDialog
        open={suspendTarget !== null}
        companyName={suspendTarget?.companyName ?? ""}
        submitting={suspend.isPending}
        onCancel={() => setSuspendTarget(null)}
        onSubmit={async (reason) => {
          if (!suspendTarget) return false;
          const ok = await suspend.submit({ id: suspendTarget.sellerId, reason });
          // 409(이미 처리)도 목록은 다시 읽히므로 닫는다.
          setSuspendTarget(null);
          return ok;
        }}
      />
      <ConfirmDialog
        open={resumeTarget !== null}
        title={`${resumeTarget?.companyName ?? ""} 정지 해제`}
        description="정지를 해제하면 상품이 다시 공개 목록에 노출되고 판매자가 등록·수정을 할 수 있습니다."
        confirmLabel="해제"
        onCancel={() => setResumeTarget(null)}
        onConfirm={() => {
          const t = resumeTarget;
          setResumeTarget(null);
          if (t) void unsuspend.submit({ id: t.sellerId });
        }}
      />
      <Dialog open={detail !== null} onOpenChange={(next) => !next && setDetail(null)}>
        <DialogContent>
          {detail && (
            <>
              <DialogHeader>
                <DialogTitle>{detail.companyName}</DialogTitle>
                <DialogDescription>신청 {formatDateTime(detail.createdAt)}</DialogDescription>
              </DialogHeader>
              <dl className="grid grid-cols-[96px_1fr] gap-x-4 gap-y-2 text-sm">
                <Fact k="상태" v={<StatusChip kind="seller" status={detail.status} />} />
                <Fact k="사업자번호" v={detail.businessNumber} />
                <Fact k="대표자" v={detail.representativeName} />
                <Fact k="연락처" v={detail.phone} />
                <Fact k="정산 계좌" v={`${detail.bank} ${detail.accountNumber} (예금주 ${detail.holder})`} />
                <Fact k="수수료" v={detail.status === "APPROVED" || detail.status === "SUSPENDED" ? formatBp(detail.commissionRateBp) : "-"} />
                {detail.rejectReason && <Fact k="반려 사유" v={detail.rejectReason} />}
                {detail.suspendReason && <Fact k="정지 사유" v={detail.suspendReason} />}
              </dl>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

function Fact({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="contents">
      <dt className="text-text-2">{k}</dt>
      <dd className="min-w-0 break-words">{v}</dd>
    </div>
  );
}
