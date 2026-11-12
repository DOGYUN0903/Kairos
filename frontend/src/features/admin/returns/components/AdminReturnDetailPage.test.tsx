import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { AdminReturnDetailPage } from "@/features/admin/returns/components/AdminReturnDetailPage";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ GET: vi.fn(), PATCH: vi.fn(), toast: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET, PATCH: h.PATCH } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));
vi.mock("next/navigation", () => ({ notFound: vi.fn(), useRouter: () => ({ push: vi.fn() }) }));

const ret = (over: Partial<ReturnRequestResponse> = {}): ReturnRequestResponse =>
  ({
    returnId: 5,
    orderId: 40,
    sellerOrderId: 41,
    orderItemId: 7,
    productName: "램스울 크루넥 니트",
    quantity: 1,
    reason: "DEFECTIVE",
    feeBearer: "SELLER",
    status: "APPROVED",
    returnFee: 0,
    outboundFeeRecovered: 0,
    shippingFeeRefund: 0,
    sellerBorneFee: 0,
    refundAmount: 0,
    detail: null,
    pointsRefund: 0,
    requestedAt: "2026-11-09T11:02:00",
    decidedAt: "2026-11-09T13:40:00",
    decidedBy: "SELLER",
    thumbnailUrl: null,
    optionDisplayName: "오트밀 / M",
    orderNo: "K20261104093015T6QH2K",
    sellerName: "판매자 A",
    paidAmount: 30000,
    autoApproveAt: null,
    ...over,
  }) as ReturnRequestResponse;
const ok = (data: ReturnRequestResponse) => ({ data: { data } });

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <AdminReturnDetailPage returnId={5} />
    </QueryClientProvider>,
  );
  return { invalidate };
}

async function openReceive() {
  await userEvent.click(await screen.findByRole("button", { name: "대신 수거 확인" }));
  return screen.findByRole("button", { name: "수거 확인 · 환불 실행" });
}

describe("AdminReturnDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.GET.mockResolvedValue(ok(ret()));
  });

  it("대신_수거_확인을_연타해도_요청은_한_번만_나간다", async () => {
    let resolve!: (v: unknown) => void;
    h.PATCH.mockReturnValue(new Promise((r) => (resolve = r)));
    mount();
    const confirm = await openReceive();
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    await waitFor(() => expect(confirm).toBeDisabled());
    fireEvent.click(confirm);
    expect(h.PATCH).toHaveBeenCalledTimes(1);
    expect(h.PATCH).toHaveBeenCalledWith("/api/v1/admin/returns/{returnId}/receive", { params: { path: { returnId: 5 } } });
    resolve(ok(ret({ status: "COMPLETED", refundAmount: 33000 })));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "환불을 완료했습니다. 환불 금액 33,000원" }));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
    // 상세가 아직 APPROVED 로 보여도 버튼은 잠긴다(다시 보내지 않는다).
    expect(await screen.findByRole("button", { name: "대신 수거 확인" })).toBeDisabled();
  });

  it("응답이_REFUNDING이면_환불_확인_중으로_보인다", async () => {
    h.PATCH.mockResolvedValue(ok(ret({ status: "REFUNDING", receivedAt: "2026-11-12T10:40:00" })));
    mount();
    h.GET.mockResolvedValue(ok(ret({ status: "REFUNDING", receivedAt: "2026-11-12T10:40:00" })));
    fireEvent.click(await openReceive());
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "환불을 확인하고 있습니다" }));
    expect(await screen.findByText("환불을 확인하고 있습니다. 3초마다 자동으로 새로 고칩니다.")).toBeInTheDocument();
    expect(screen.getAllByText("환불 확인 중").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "대신 수거 확인" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "중단" })).not.toBeInTheDocument();
  });

  it("네트워크_오류면_결과를_확인하지_못했다고_알리고_다시_보내지_않는다", async () => {
    h.PATCH.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    mount();
    const confirm = await openReceive();
    const getsBefore = h.GET.mock.calls.length;
    fireEvent.click(confirm);
    await waitFor(() => expect(h.GET.mock.calls.length).toBeGreaterThan(getsBefore));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "결과를 확인하지 못했습니다. 반품 상태를 확인해 주세요", variant: "destructive" }));
    expect(screen.getByText("결과를 확인하지 못했습니다. 아래 상태를 확인한 뒤 판단해 주세요. 자동으로 다시 시도하지 않습니다.")).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 100));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("PG가_거절하면_승인_상태로_돌아갔다고_알리고_잠그지_않는다", async () => {
    h.PATCH.mockRejectedValue(new ApiError(409, "PAYMENT_CANCEL_FAILED", "PG 거절", null));
    mount();
    fireEvent.click(await openReceive());
    await waitFor(() =>
      expect(h.toast).toHaveBeenCalledWith({ title: "환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요", variant: "destructive" }),
    );
    expect(await screen.findByRole("button", { name: "대신 수거 확인" })).toBeEnabled();
  });

  it("환불_확인_중에_PG_거절로_승인_상태로_돌아오면_잠금을_풀고_거절_안내를_보인다", { timeout: 10000 }, async () => {
    let stage: "APPROVED" | "REFUNDING" = "APPROVED";
    h.GET.mockImplementation(async () => ok(ret({ status: stage })));
    h.PATCH.mockImplementation(async () => {
      stage = "REFUNDING";
      return ok(ret({ status: "REFUNDING" }));
    });
    mount();
    fireEvent.click(await openReceive());
    expect(await screen.findByText("환불을 확인하고 있습니다. 3초마다 자동으로 새로 고칩니다.")).toBeInTheDocument();
    stage = "APPROVED";
    expect(await screen.findByText("환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요", {}, { timeout: 6000 })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "대신 수거 확인" })).toBeEnabled();
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("수거_확인_창은_판매자_부담_안내를_금액_없이_보인다", async () => {
    mount();
    await openReceive();
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("판매자 부담 반품입니다. 반품 배송비는 정산에서 차감됩니다.")).toBeInTheDocument();
    expect(within(dialog).queryByText(/\d,\d{3}원/)).not.toBeInTheDocument();
  });

  it("거절된_반품도_다시_승인할_수_있다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REJECTED", rejectReason: "착용 흔적" })));
    h.PATCH.mockResolvedValue(ok(ret({ status: "APPROVED" })));
    const { invalidate } = mount();
    await userEvent.click(await screen.findByRole("button", { name: "승인" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "승인" }));
    await waitFor(() => expect(h.PATCH).toHaveBeenCalledWith("/api/v1/admin/returns/{returnId}/approve", { params: { path: { returnId: 5 } } }));
    await waitFor(() => {
      const keys = invalidate.mock.calls.map((c) => JSON.stringify((c[0] as { queryKey: unknown }).queryKey));
      expect(keys).toContain(JSON.stringify(queryKeys.adminReturns.detail(5)));
      expect(keys).toContain(JSON.stringify(queryKeys.adminReturns.lists));
    });
  });

  it("구매_확정된_주문은_승인할_수_없다는_안내를_보인다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REJECTED" })));
    h.PATCH.mockRejectedValue(new ApiError(409, "RETURN_NOT_ELIGIBLE", "서버 메시지", null));
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "승인" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "승인" }));
    await waitFor(() =>
      expect(h.toast).toHaveBeenCalledWith({ title: "이미 구매 확정된 주문이라 승인할 수 없습니다. 정산 조정으로 처리해 주세요", variant: "destructive" }),
    );
  });

  it("중단은_확인_문구를_보이고_확인하면_cancel을_한_번_보낸다", async () => {
    h.PATCH.mockResolvedValue(ok(ret({ status: "CANCELED_BY_ADMIN" })));
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "중단" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("구매자 요청으로 반품을 중단합니다. 상품은 주문 상태로 돌아가며, 구매자는 이 상품을 다시 반품 요청할 수 없습니다")).toBeInTheDocument();
    expect(h.PATCH).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "중단" }));
    await waitFor(() => expect(h.PATCH).toHaveBeenCalledWith("/api/v1/admin/returns/{returnId}/cancel", { params: { path: { returnId: 5 } } }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "반품을 중단했습니다" }));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("주문번호는_관리자_주문_상세로_연결한다", async () => {
    mount();
    expect(await screen.findByRole("link", { name: "K20261104093015T6QH2K" })).toHaveAttribute("href", "/admin/orders/40");
    expect(screen.getByText("판매자 A")).toBeInTheDocument();
  });

  it("처리할_수_없는_상태에는_버튼이_없다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "CANCELED_BY_ADMIN" })));
    mount();
    expect(await screen.findByText("처리할 수 있는 작업이 없습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "중단" })).not.toBeInTheDocument();
  });
});
