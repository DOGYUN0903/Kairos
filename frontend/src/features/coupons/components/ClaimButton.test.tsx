import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { ClaimButton } from "@/features/coupons/components/ClaimButton";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ claimCoupon: vi.fn(), toast: vi.fn() }));
vi.mock("@/features/coupons/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/coupons/api")>()),
  claimCoupon: h.claimCoupon,
}));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

function setup() {
  const qc = new QueryClient();
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <ClaimButton policy={{ policyId: 5, claimed: false }} />
    </QueryClientProvider>,
  );
  return invalidate;
}

const click = () => userEvent.click(screen.getByRole("button", { name: "쿠폰 받기" }));

describe("ClaimButton", () => {
  beforeEach(() => vi.clearAllMocks());

  it("받기에_성공하면_서버_만료일로_안내하고_쿠폰_정책_내_쿠폰_견적을_다시_읽는다", async () => {
    h.claimCoupon.mockResolvedValue({ couponId: 1, expiresAt: "2026-12-31T23:59:59" });
    const invalidate = setup();
    await click();
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "쿠폰을 받았습니다. 12월 31일까지 사용할 수 있습니다." }));
    expect(h.claimCoupon).toHaveBeenCalledWith(5, expect.any(String));
    for (const queryKey of [queryKeys.coupons.policiesRoot, queryKeys.coupons.mineRoot, queryKeys.checkout.quotes]) {
      expect(invalidate).toHaveBeenCalledWith({ queryKey });
    }
  });

  it("네트워크_오류_뒤_다시_누르면_같은_키로_보낸다", async () => {
    h.claimCoupon.mockRejectedValueOnce(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null)).mockResolvedValue({ couponId: 1, expiresAt: "2026-12-31T23:59:59" });
    setup();
    await click();
    await waitFor(() => expect(h.toast).toHaveBeenCalled());
    await click();
    await waitFor(() => expect(h.claimCoupon).toHaveBeenCalledTimes(2));
    expect(h.claimCoupon.mock.calls[1]?.[1]).toBe(h.claimCoupon.mock.calls[0]?.[1]);
  });

  it("이미_받은_쿠폰이면_안내하고_정책_목록을_다시_읽는다", async () => {
    h.claimCoupon.mockRejectedValue(new ApiError(409, "COUPON_ALREADY_ISSUED", "x", null));
    const invalidate = setup();
    await click();
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "이미 받은 쿠폰입니다." }));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.coupons.policiesRoot });
  });
});
