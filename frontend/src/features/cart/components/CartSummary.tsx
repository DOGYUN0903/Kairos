"use client";

import { Check } from "lucide-react";
import Link from "next/link";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Money } from "@/components/common/Money";
import type { OrderQuote } from "@/features/checkout/api";
import { checkoutHref } from "@/features/checkout/checkout-params";
import { amountUntil } from "@/lib/money";

type Props = {
  selectedIds: number[];
  quote: OrderQuote | undefined;
  /** 새 견적을 기다리는 중(이전 값이 남아 있을 수 있다). */
  stale: boolean;
  error: unknown;
  onRetry: () => void;
};

/** 모든 금액은 서버 견적 값이다. 프론트가 계산하는 것은 무료배송까지 남은 금액(amountUntil)뿐이다. */
export function CartSummary({ selectedIds, quote, stale, error, onRetry }: Props) {
  const none = selectedIds.length === 0;
  const shown = none ? undefined : quote;
  const btn = "btn btn-primary btn-lg btn-block mt-5";
  const quoteFailed = !none && error !== null && error !== undefined && !(error instanceof ApiError && error.code === "SKU_NOT_ORDERABLE");
  const remaining = shown ? amountUntil(shown.freeShippingThreshold, shown.itemsAmount) : 0;

  return (
    <div className="rounded-lg border border-border bg-bg p-6" aria-busy={!none && stale}>
      <h2 className="mb-4 text-base font-bold">결제 예정 금액</h2>
      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2.5 text-sm">
        <dt className="text-text-2">상품 금액</dt>
        <dd className="text-right font-medium tabular-nums">
          <Money value={shown?.itemsAmount ?? 0} />
        </dd>
        <dt className="text-text-2">배송비</dt>
        <dd className="text-right font-medium tabular-nums">
          <Money value={shown?.buyerShippingFee ?? 0} />
        </dd>
        {shown?.sellerQuotes.map((s) => (
          <div key={s.sellerId} className="col-span-2 -mt-1.5 flex justify-between pl-2.5 text-xs text-text-3">
            <span>{s.sellerName}</span>
            <span className="tabular-nums">
              {s.shippingFeePayer === "PLATFORM" ? "무료배송" : <Money value={s.shippingFee} />}
            </span>
          </div>
        ))}
      </dl>
      <div className="mt-4 flex items-baseline justify-between border-t border-border pt-4 font-bold">
        <span>결제 예정 금액</span>
        <strong className="text-2xl tracking-tight">
          <Money value={shown?.totalAmount ?? 0} />
        </strong>
      </div>

      {shown &&
        (remaining > 0 ? (
          <div className="mt-4 rounded bg-surface px-3.5 py-3 text-[13px]">
            <span>
              <b className="tabular-nums">
                <Money value={remaining} />
              </b>{" "}
              더 담으면 무료배송
            </span>
            <progress
              aria-hidden
              value={shown.itemsAmount}
              max={shown.freeShippingThreshold}
              className="mt-2 block h-1 w-full appearance-none overflow-hidden rounded-sm [&::-moz-progress-bar]:bg-navy [&::-webkit-progress-bar]:bg-border [&::-webkit-progress-value]:bg-navy"
            />
          </div>
        ) : (
          <div className="mt-4 flex items-center gap-2 rounded bg-[#E6F4EC] px-3.5 py-3 text-[13px] font-medium text-success">
            <Check className="h-4 w-4" aria-hidden />
            무료배송
          </div>
        ))}

      {quoteFailed && (
        <p role="alert" className="mt-4 text-[13px] text-danger">
          {resolveError(error).message || "금액을 계산하지 못했습니다."}{" "}
          <button type="button" className="underline underline-offset-2" onClick={onRetry}>
            다시 시도
          </button>
        </p>
      )}

      {none || !shown || stale || quoteFailed ? (
        <>
          <button type="button" className={btn} disabled>
            주문하기{none ? "" : ` (${selectedIds.length})`}
          </button>
          {none && <span className="mt-2.5 block text-center text-xs text-text-2">주문할 상품을 선택해 주세요</span>}
        </>
      ) : (
        <Link className={btn} href={checkoutHref.fromCart(selectedIds)}>
          주문하기 ({selectedIds.length})
        </Link>
      )}
    </div>
  );
}
