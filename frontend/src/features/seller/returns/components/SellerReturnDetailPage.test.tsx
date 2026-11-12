import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { SellerReturnDetailPage } from "@/features/seller/returns/components/SellerReturnDetailPage";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ GET: vi.fn(), PATCH: vi.fn(), toast: vi.fn(), sellerStatus: { value: "APPROVED" } }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET, PATCH: h.PATCH } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));
vi.mock("next/navigation", () => ({ notFound: vi.fn(), useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/features/seller/gate/useSellerMe", () => ({ useSellerMe: () => ({ status: h.sellerStatus.value, seller: null, error: null, refetch: vi.fn() }) }));

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
// 서버가 만드는 상태별 금액(REFUNDING 이후는 계산된 금액을 싣는다): 30,000원 불량, 구매자가 낸 배송비 3,000원.
const refunding = { status: "REFUNDING", receivedAt: "2026-11-12T10:40:00", refundAmount: 33000, shippingFeeRefund: 3000, sellerBorneFee: 3000 } as const;
const completed = { ...refunding, status: "COMPLETED", completedAt: "2026-11-12T10:41:00" } as const;
const ok = (data: ReturnRequestResponse) => ({ data: { data } });

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <SellerReturnDetailPage returnId={5} />
    </QueryClientProvider>,
  );
  return { invalidate };
}

async function openReceive() {
  await userEvent.click(await screen.findByRole("button", { name: "수거 확인" }));
  return screen.findByRole("button", { name: "수거 확인 · 환불 실행" });
}

describe("SellerReturnDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.sellerStatus.value = "APPROVED";
    h.GET.mockResolvedValue(ok(ret()));
  });

  it("수거_확인을_연타해도_요청은_한_번만_나간다", async () => {
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
    expect(h.PATCH).toHaveBeenCalledWith("/api/v1/seller/returns/{returnId}/receive", { params: { path: { returnId: 5 } } });
    resolve(ok(ret(completed)));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "환불을 완료했습니다. 환불 금액 33,000원" }));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
    // 상세가 아직 APPROVED 로 보여도 버튼은 잠긴다(다시 보내지 않는다).
    expect(await screen.findByRole("button", { name: "수거 확인" })).toBeDisabled();
  });

  it("응답이_REFUNDING이면_환불_확인_중으로_보인다", async () => {
    h.PATCH.mockResolvedValue(ok(ret(refunding)));
    mount();
    h.GET.mockResolvedValue(ok(ret(refunding)));
    fireEvent.click(await openReceive());
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "환불을 확인하고 있습니다" }));
    expect(await screen.findByText("환불을 확인하고 있습니다. 3초마다 자동으로 새로 고칩니다.")).toBeInTheDocument();
    expect(screen.getAllByText("환불 확인 중").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "수거 확인" })).not.toBeInTheDocument();
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

  it("PG가_거절하면_승인_상태로_돌아갔다고_알린다", async () => {
    h.PATCH.mockRejectedValue(new ApiError(409, "PAYMENT_CANCEL_FAILED", "PG 거절", null));
    mount();
    fireEvent.click(await openReceive());
    await waitFor(() =>
      expect(h.toast).toHaveBeenCalledWith({ title: "환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요", variant: "destructive" }),
    );
    expect(await screen.findByRole("button", { name: "수거 확인" })).toBeEnabled();
  });

  it("수거_확인_창에_사유별_재고_안내와_판매자_부담_안내를_금액_없이_보인다", async () => {
    mount();
    await openReceive();
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText(/재고는 자동으로 복구되지 않습니다. 재판매할 수 있으면 상품 관리에서 재고를 수정해 주세요/)).toBeInTheDocument();
    expect(within(dialog).getByText("판매자 부담 반품입니다. 반품 배송비는 정산에서 차감됩니다.")).toBeInTheDocument();
    expect(within(dialog).queryByText(/\d,\d{3}원/)).not.toBeInTheDocument();
  });

  it("변심_반품은_재고가_자동으로_복구된다고_안내한다", async () => {
    h.GET.mockResolvedValue(ok(ret({ reason: "CHANGE_OF_MIND", feeBearer: "BUYER", sellerBorneFee: 0 })));
    mount();
    await openReceive();
    expect(screen.getByText(/재고가 자동으로 복구됩니다/)).toBeInTheDocument();
  });

  it("승인하면_상세와_목록_건수를_다시_읽게_한다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REQUESTED", autoApproveAt: "2026-11-13T14:22:00" })));
    h.PATCH.mockResolvedValue(ok(ret({ status: "APPROVED" })));
    const { invalidate } = mount();
    await userEvent.click(await screen.findByRole("button", { name: "승인" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "승인" }));
    await waitFor(() => expect(h.PATCH).toHaveBeenCalledWith("/api/v1/seller/returns/{returnId}/approve", { params: { path: { returnId: 5 } } }));
    await waitFor(() => {
      const keys = invalidate.mock.calls.map((c) => JSON.stringify((c[0] as { queryKey: unknown }).queryKey));
      expect(keys).toContain(JSON.stringify(queryKeys.sellerReturns.detail(5)));
      expect(keys).toContain(JSON.stringify(queryKeys.sellerReturns.lists));
    });
  });

  it("거절은_사유가_비어_있으면_보내지_않는다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REQUESTED" })));
    h.PATCH.mockResolvedValue(ok(ret({ status: "REJECTED", rejectReason: "x" })));
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "거절" }));
    expect(await within(dialog).findByText("거절 사유를 입력해 주세요.")).toBeInTheDocument();
    expect(h.PATCH).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByRole("textbox"), "착용 흔적이 있습니다");
    await userEvent.click(within(dialog).getByRole("button", { name: "거절" }));
    await waitFor(() =>
      expect(h.PATCH).toHaveBeenCalledWith("/api/v1/seller/returns/{returnId}/reject", { params: { path: { returnId: 5 } }, body: { rejectReason: "착용 흔적이 있습니다" } }),
    );
  });

  it("409_상태_충돌은_서버_메시지를_보인다", async () => {
    h.PATCH.mockRejectedValue(new ApiError(409, "RETURN_INVALID_STATE_TRANSITION", "이미 철회된 반품입니다", null));
    mount();
    const confirm = await openReceive();
    const getsBefore = h.GET.mock.calls.length;
    fireEvent.click(confirm);
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "이미 철회된 반품입니다", variant: "destructive" }));
    await waitFor(() => expect(h.GET.mock.calls.length).toBeGreaterThan(getsBefore));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("승인_중_409_상태_충돌은_서버_메시지를_보이고_상세를_다시_읽는다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REQUESTED" })));
    h.PATCH.mockRejectedValue(new ApiError(409, "RETURN_INVALID_STATE_TRANSITION", "이미 처리된 반품입니다", null));
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "승인" }));
    const getsBefore = h.GET.mock.calls.length;
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "승인" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "이미 처리된 반품입니다", variant: "destructive" }));
    await waitFor(() => expect(h.GET.mock.calls.length).toBeGreaterThan(getsBefore));
  });

  it("거절_중_409면_서버_메시지를_보이고_상세를_다시_읽는다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REQUESTED" })));
    h.PATCH.mockRejectedValue(new ApiError(409, "RETURN_INVALID_STATE_TRANSITION", "이미 철회된 반품입니다", null));
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByRole("textbox"), "사유");
    const getsBefore = h.GET.mock.calls.length;
    await userEvent.click(within(dialog).getByRole("button", { name: "거절" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "이미 철회된 반품입니다", variant: "destructive" }));
    await waitFor(() => expect(h.GET.mock.calls.length).toBeGreaterThan(getsBefore));
  });

  it("거절_요청이_실패하면_창과_입력한_사유를_유지한다", async () => {
    h.GET.mockResolvedValue(ok(ret({ status: "REQUESTED" })));
    h.PATCH.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByRole("textbox"), "착용 흔적");
    await userEvent.click(within(dialog).getByRole("button", { name: "거절" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalled());
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(within(screen.getByRole("dialog")).getByRole("textbox")).toHaveValue("착용 흔적");
  });

  it("환불_확인_중에_PG_거절로_승인_상태로_돌아오면_잠금을_풀고_거절_안내를_보인다", { timeout: 10000 }, async () => {
    let stage: "APPROVED" | "REFUNDING" = "APPROVED";
    h.GET.mockImplementation(async () => ok(ret({ status: stage })));
    h.PATCH.mockImplementation(async () => {
      stage = "REFUNDING";
      return ok(ret(refunding));
    });
    mount();
    fireEvent.click(await openReceive());
    expect(await screen.findByText("환불을 확인하고 있습니다. 3초마다 자동으로 새로 고칩니다.")).toBeInTheDocument();
    expect(screen.queryByText("처리할 수 있는 작업이 없습니다.")).not.toBeInTheDocument();
    stage = "APPROVED"; // 대사가 PG 거절을 확인해 반품을 되돌렸다.
    expect(await screen.findByText("환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요", {}, { timeout: 6000 })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "수거 확인" })).toBeEnabled();
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("결과를_확인하지_못했다는_안내는_상태가_APPROVED가_아니면_사라진다", async () => {
    let stage: "APPROVED" | "REFUNDING" = "APPROVED";
    h.GET.mockImplementation(async () => ok(ret({ status: stage })));
    h.PATCH.mockImplementation(async () => {
      stage = "REFUNDING";
      throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
    });
    mount();
    fireEvent.click(await openReceive());
    expect(await screen.findByText("환불을 확인하고 있습니다. 3초마다 자동으로 새로 고칩니다.")).toBeInTheDocument();
    expect(screen.queryByText(/아래 상태를 확인한 뒤 판단해 주세요/)).not.toBeInTheDocument();
  });

  it("구매자_상세_내용은_줄바꿈을_유지해_보이고_없으면_줄이_없다", async () => {
    h.GET.mockResolvedValue(ok(ret({ detail: "소매 봉제선이 뜯어져 있습니다.\n사진은 첨부하지 않았습니다." })));
    mount();
    const dd = (await screen.findByText(/소매 봉제선이 뜯어져 있습니다/)) as HTMLElement;
    expect(dd).toHaveClass("whitespace-pre-wrap");
    expect(screen.getByText("상세 내용")).toBeInTheDocument();
  });

  it("상세_내용이_없으면_상세_내용_줄을_그리지_않는다", async () => {
    mount();
    await screen.findByText("반품 사유");
    expect(screen.queryByText("상세 내용")).not.toBeInTheDocument();
  });

  it("정지_판매자는_버튼_대신_안내를_본다", async () => {
    h.sellerStatus.value = "SUSPENDED";
    h.GET.mockResolvedValue(ok(ret({ status: "REQUESTED" })));
    mount();
    expect(await screen.findByText("정지 상태에서는 반품을 처리할 수 없습니다. 관리자가 대신 처리합니다")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "승인" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "거절" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "수거 확인" })).not.toBeInTheDocument();
  });

  it("판매자_부담_반품비가_있으면_정산_차감_문구를_보인다", async () => {
    h.GET.mockResolvedValue(ok(ret(completed)));
    mount();
    expect(await screen.findByText("판매자 부담 반품비 3,000원 — 정산에서 차감됩니다")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "K20261104093015T6QH2K" })).toHaveAttribute("href", "/seller/orders/41");
  });
});
