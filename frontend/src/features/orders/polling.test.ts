import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { OrderDetail } from "@/features/orders/api";
import { needsPolling, POLL_INTERVAL_MS, POLL_LIMIT_MS, usePollingWindow } from "@/features/orders/polling";

type PaymentStatus = NonNullable<OrderDetail["payment"]>["status"];
type ItemStatus = OrderDetail["sellerOrders"][number]["items"][number]["status"];

function order(payment: PaymentStatus | null, items: ItemStatus[] = ["ORDERED"]): OrderDetail {
  return {
    payment: payment ? { amount: 1000, refundedAmount: 0, status: payment } : null,
    sellerOrders: [{ items: items.map((status) => ({ status })) }],
  } as unknown as OrderDetail;
}

describe("needsPolling", () => {
  it("결제_확인_중이면_다시_조회한다", () => {
    expect(needsPolling(order("IN_PROGRESS"))).toBe(true);
    expect(needsPolling(order("UNKNOWN"))).toBe(true);
  });

  it("취소_확인_중_항목이_있으면_다시_조회한다", () => {
    expect(needsPolling(order("APPROVED", ["ORDERED", "CANCEL_REQUESTED"]))).toBe(true);
  });

  it("환불_확인_중인_반품_항목이_있으면_다시_조회한다", () => {
    expect(needsPolling(order("APPROVED", ["ORDERED", "RETURN_REFUNDING"]))).toBe(true);
  });

  it("둘_다_아니면_멈춘다", () => {
    expect(needsPolling(order("READY"))).toBe(false);
    expect(needsPolling(order("APPROVED", ["ORDERED", "CANCELED"]))).toBe(false);
    expect(needsPolling(order(null))).toBe(false);
  });
});

describe("usePollingWindow", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("활성일_때_3초_간격을_주고_2분이_지나면_멈춘다", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => usePollingWindow(true));
    expect(result.current).toEqual({ interval: POLL_INTERVAL_MS, expired: false });
    act(() => {
      vi.advanceTimersByTime(POLL_LIMIT_MS - 1);
    });
    expect(result.current).toEqual({ interval: 3000, expired: false });
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toEqual({ interval: false, expired: true });
  });

  it("비활성이면_조회하지_않고_다시_활성이_되면_그때부터_2분이다", () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(({ active }) => usePollingWindow(active), { initialProps: { active: false } });
    expect(result.current).toEqual({ interval: false, expired: false });
    act(() => {
      vi.advanceTimersByTime(120000);
    });
    rerender({ active: true });
    expect(result.current).toEqual({ interval: 3000, expired: false });
    act(() => {
      vi.advanceTimersByTime(119999);
    });
    expect(result.current.expired).toBe(false);
  });
});
