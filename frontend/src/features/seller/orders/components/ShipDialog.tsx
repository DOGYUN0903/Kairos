"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { CARRIERS } from "@/features/orders/carriers";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useShip } from "@/features/seller/orders/api";
import { TRACKING_MAX, shipSchema, type ShipValues } from "@/features/seller/orders/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";
import { useQueryClient } from "@tanstack/react-query";

/** 발송 처리 창. open 일 때만 내용을 그려 닫으면 입력이 새로 시작한다. */
export function ShipDialog({ sellerOrderId, orderNo, itemCount, open, onClose }: { sellerOrderId: number; orderNo: string; itemCount: number; open: boolean; onClose: () => void }) {
  return (
    open ? <ShipBody sellerOrderId={sellerOrderId} orderNo={orderNo} itemCount={itemCount} onClose={onClose} /> : null
  );
}

function ShipBody({ sellerOrderId, orderNo, itemCount, onClose }: { sellerOrderId: number; orderNo: string; itemCount: number; onClose: () => void }) {
  const ship = useShip(sellerOrderId);
  const setBanner = useSellerBanner();
  const qc = useQueryClient();
  const form = useForm<ShipValues>({ resolver: zodResolver(shipSchema), defaultValues: { carrierCode: CARRIERS[0]?.code ?? "", trackingNumber: "" } });
  const { register, formState } = form;

  // 발송은 키 없는 PATCH 다(같은 송장은 서버가 200). 훅은 이중 제출 가드로만 쓴다.
  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values) => {
      await ship(values);
      toast({ title: "발송 처리했습니다" });
      onClose();
    },
    {
      onError: async (e) => {
        if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
          setBanner("판매자 상태 때문에 처리할 수 없습니다.");
          await qc.invalidateQueries({ queryKey: queryKeys.seller.me });
          onClose();
          return;
        }
        // 409(다른 송장으로 재전송, 취소 확인 중 항목 있음 등)는 서버 메시지를 그대로 보여 준다.
        toast({ title: resolveError(e).message, variant: "destructive" });
      },
    },
  );

  const trackingError = formState.errors.trackingNumber?.message;
  const carrierError = formState.errors.carrierCode?.message;

  return (
    <Dialog open onOpenChange={(next) => !next && !submitting && onClose()}>
    <DialogContent className="max-w-[460px]">
      <DialogHeader>
        <DialogTitle>발송 처리</DialogTitle>
        <DialogDescription>
          주문 <span className="tabular-nums">{orderNo}</span> · 항목 {itemCount}개를 발송합니다.
        </DialogDescription>
      </DialogHeader>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="field">
          <label htmlFor="ship-carrier">
            택배사<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
            <span className="sr-only"> (필수)</span>
          </label>
          <select id="ship-carrier" className="input" disabled={submitting} aria-invalid={carrierError ? true : undefined} {...register("carrierCode")}>
            {CARRIERS.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
          {carrierError && <p role="alert" className="field-error">{carrierError}</p>}
        </div>
        <div className="field">
          <label htmlFor="ship-tracking">
            송장번호<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
            <span className="sr-only"> (필수)</span>
          </label>
          <input
            id="ship-tracking"
            type="text"
            className="input tabular-nums"
            maxLength={TRACKING_MAX}
            required
            disabled={submitting}
            placeholder="숫자만 입력"
            autoComplete="off"
            aria-invalid={trackingError ? true : undefined}
            aria-describedby={trackingError ? "ship-tracking-err" : "ship-tracking-hint"}
            {...register("trackingNumber")}
          />
          {trackingError ? (
            <p id="ship-tracking-err" role="alert" className="field-error">{trackingError}</p>
          ) : (
            <p id="ship-tracking-hint" className="hint">발송 처리 뒤에는 송장번호를 바꿀 수 없습니다. 다시 확인해 주세요.</p>
          )}
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onClose}>
            닫기
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? "처리 중…" : "발송 처리"}
          </button>
        </div>
      </form>
    </DialogContent>
    </Dialog>
  );
}
