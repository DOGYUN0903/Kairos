import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { AdminAdjustmentsPage } from "@/features/admin/settlements/components/AdminAdjustmentsPage";
import { AdminSettlementDetailPage } from "@/features/admin/settlements/components/AdminSettlementDetailPage";
import { AdminSettlementListPage } from "@/features/admin/settlements/components/AdminSettlementListPage";

const h = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn(), toast: vi.fn(), search: "" }));
vi.mock("@/api/client", async (o) => ({ ...(await o<typeof import("@/api/client")>()), api: { GET: h.GET, POST: h.POST, PATCH: h.PATCH, DELETE: h.DELETE } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));
vi.mock("next/navigation", () => ({
  notFound: vi.fn(),
  usePathname: () => "/admin/settlements/adjustments",
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(h.search),
}));

const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });
const wrap = (ui: React.ReactElement, qc = newClient()) => render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
const page = (content: unknown[]) => ({ data: { data: { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 } } });
const summary = { settlementId: 7, sellerId: 12, sellerName: "판매자 C", sellerStatus: "SUSPENDED", periodStart: "2026-11-02", periodEnd: "2026-11-09", payoutDate: "2026-11-11", status: "CONFIRMED", totalAmount: 12500, payableAmount: 12500, lineCount: 1 };
const detail = (over: object = {}) => ({ data: { data: { ...summary, salesAmount: 15000, sellerCouponDiscount: 0, commissionAmount: 1500, shippingFeeOwed: 0, returnFeeBorne: 1000, adjustmentAmount: 0, lines: [], adjustments: [], ...over } } });

describe("관리자 정산 목록", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.search = "";
  });

  it("승인이_아닌_판매자는_상태를_보이고_기간은_마지막_날로_보인다", async () => {
    h.GET.mockResolvedValue(page([summary]));
    wrap(<AdminSettlementListPage />);
    expect(await screen.findByRole("link", { name: "2026-11-02 ~ 2026-11-08" })).toHaveAttribute("href", "/admin/settlements/7");
    expect(screen.getByText("판매자 C")).toBeInTheDocument();
    expect(screen.getByText("정지")).toBeInTheDocument();
  });

  it("다시_읽기가_실패하면_이전_행과_함께_오류와_다시_시도를_보인다", async () => {
    h.GET.mockResolvedValueOnce(page([summary]));
    const qc = newClient();
    wrap(<AdminSettlementListPage />, qc);
    await screen.findByRole("link", { name: "2026-11-02 ~ 2026-11-08" });
    h.GET.mockRejectedValue(new ApiError(500, "INTERNAL_ERROR", "서버 오류", null));
    await qc.invalidateQueries({ queryKey: ["adminSettlements", "list"] });
    expect(await screen.findByRole("button", { name: /다시 시도/ })).toBeInTheDocument();
  });

  it("필터를_바꾸면_새_응답이_오기_전까지_이전_필터의_행을_보이지_않는다", async () => {
    h.GET.mockResolvedValueOnce(page([summary]));
    const qc = newClient();
    const ui = () => (
      <QueryClientProvider client={qc}>
        <AdminSettlementListPage />
      </QueryClientProvider>
    );
    const view = render(ui());
    await screen.findByText("판매자 C");
    h.GET.mockReturnValue(new Promise(() => {}));
    h.search = "sellerId=99";
    view.rerender(ui());
    await waitFor(() => expect(screen.getByLabelText("불러오는 중")).toBeInTheDocument());
    expect(screen.queryByText("판매자 C")).not.toBeInTheDocument();
  });

  it("필터_결과가_없으면_안내한다", async () => {
    h.search = "status=PAID";
    h.GET.mockResolvedValue(page([]));
    wrap(<AdminSettlementListPage />);
    expect(await screen.findByText(/조건에 맞는 정산이 없습니다/)).toBeInTheDocument();
  });
});

describe("관리자 정산 상세", () => {
  beforeEach(() => vi.clearAllMocks());

  it("GENERATED는_확정만_CONFIRMED는_지급_완료만_보인다", async () => {
    h.GET.mockResolvedValue(detail({ status: "GENERATED" }));
    const first = wrap(<AdminSettlementDetailPage settlementId={7} />);
    expect(await screen.findByRole("button", { name: "정산 확정" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "지급 완료" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이 판매자의 조정 보기" })).toHaveAttribute("href", "/admin/settlements/adjustments?sellerId=12");
    first.unmount();
    h.GET.mockResolvedValue(detail({ status: "PAID" }));
    wrap(<AdminSettlementDetailPage settlementId={7} />);
    await screen.findByRole("heading", { name: /정산 2026-11-02/ });
    expect(screen.queryByRole("button", { name: "정산 확정" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "지급 완료" })).not.toBeInTheDocument();
  });

  it("확정_창에서_연타해도_요청은_한_번만_나간다", async () => {
    h.GET.mockResolvedValue(detail({ status: "GENERATED" }));
    let resolve: (v: unknown) => void = () => {};
    h.PATCH.mockReturnValue(new Promise((r) => (resolve = r)));
    wrap(<AdminSettlementDetailPage settlementId={7} />);
    await userEvent.click(await screen.findByRole("button", { name: "정산 확정" }));
    const dialog = await screen.findByRole("dialog");
    const ok = within(dialog).getByRole("button", { name: "확정" });
    fireEvent.click(ok);
    fireEvent.click(ok);
    resolve({});
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "정산을 확정했습니다" }));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("지급액이_0원이면_안내_문구가_바뀌고_메모는_비면_보내지_않는다", async () => {
    h.GET.mockResolvedValue(detail({ totalAmount: -3000, payableAmount: 0 }));
    h.PATCH.mockResolvedValue({});
    wrap(<AdminSettlementDetailPage settlementId={7} />);
    await userEvent.click(await screen.findByRole("button", { name: "지급 완료" }));
    expect(await screen.findByText("지급할 금액이 없는 정산입니다. 지급 완료로 닫습니다")).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "지급 완료" }));
    await waitFor(() => expect(h.PATCH).toHaveBeenCalledTimes(1));
    expect((h.PATCH.mock.calls[0]?.[1] as { body: object }).body).toEqual({});
  });

  it("지급_완료는_지급액_문구와_메모를_보낸다", async () => {
    h.GET.mockResolvedValue(detail());
    h.PATCH.mockResolvedValue({});
    wrap(<AdminSettlementDetailPage settlementId={7} />);
    await userEvent.click(await screen.findByRole("button", { name: "지급 완료" }));
    expect(await screen.findByText("지급액 12,500원을 지급 완료로 표시합니다(실제 이체는 따로 진행합니다)")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/메모/), "이체 완료");
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "지급 완료" }));
    await waitFor(() => expect(h.PATCH).toHaveBeenCalledTimes(1));
    expect((h.PATCH.mock.calls[0]?.[1] as { body: object }).body).toEqual({ memo: "이체 완료" });
  });

  it("상태_전이_409는_서버_메시지를_보이고_상세를_다시_읽는다", async () => {
    h.GET.mockResolvedValue(detail({ status: "GENERATED" }));
    h.PATCH.mockRejectedValue(new ApiError(409, "SETTLEMENT_INVALID_STATE_TRANSITION", "확정된 정산만 지급 완료로 처리할 수 있습니다.", null));
    wrap(<AdminSettlementDetailPage settlementId={7} />);
    await userEvent.click(await screen.findByRole("button", { name: "정산 확정" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "확정" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "확정된 정산만 지급 완료로 처리할 수 있습니다.", variant: "destructive" }));
    await waitFor(() => expect(h.GET.mock.calls.length).toBeGreaterThan(1));
  });
});

describe("관리자 정산 조정", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.search = "sellerId=12";
  });
  const manual = { adjustmentId: 1, sellerId: 12, type: "MANUAL", amount: -10000, reason: "파손 보상", settlementId: null, refSellerOrderId: 4817 };
  const applied = { adjustmentId: 2, sellerId: 12, type: "MANUAL", amount: 5000, reason: "환급", settlementId: 3 };
  const carry = { adjustmentId: 3, sellerId: 12, type: "CARRY_OVER", amount: -3000, reason: "이월", settlementId: null };

  it("판매자를_고르지_않으면_안내한다", () => {
    h.search = "";
    wrap(<AdminAdjustmentsPage />);
    expect(screen.getByText(/판매자를 선택해 주세요/)).toBeInTheDocument();
    expect(h.GET).not.toHaveBeenCalled();
  });

  it("삭제_버튼은_미부착_수동_조정에만_있다", async () => {
    h.GET.mockResolvedValue(page([manual, applied, carry]));
    wrap(<AdminAdjustmentsPage />);
    expect(await screen.findByText("파손 보상")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "삭제" })).toHaveLength(1);
    expect(screen.getAllByText("다음 정산에 반영 예정")).toHaveLength(2);
    expect(screen.getByRole("link", { name: /정산에 반영됨/ })).toHaveAttribute("href", "/admin/settlements/3");
    expect(screen.getByText("−10,000원")).toBeInTheDocument();
  });

  it("다시_읽기가_실패하면_이전_조정과_함께_오류를_보인다", async () => {
    h.GET.mockResolvedValueOnce(page([manual]));
    const qc = newClient();
    wrap(<AdminAdjustmentsPage />, qc);
    await screen.findByText("파손 보상");
    h.GET.mockRejectedValue(new ApiError(500, "INTERNAL_ERROR", "서버 오류", null));
    await qc.invalidateQueries({ queryKey: ["adminAdjustments", 12] });
    expect(await screen.findByRole("button", { name: /다시 시도/ })).toBeInTheDocument();
  });

  it("주소의_판매자_ID가_바뀌면_입력_칸도_따라간다", () => {
    h.GET.mockReturnValue(new Promise(() => {}));
    const qc = newClient();
    const ui = () => (
      <QueryClientProvider client={qc}>
        <AdminAdjustmentsPage />
      </QueryClientProvider>
    );
    const view = render(ui());
    expect(screen.getByLabelText("판매자 ID")).toHaveValue("12");
    h.search = "sellerId=34";
    view.rerender(ui());
    expect(screen.getByLabelText("판매자 ID")).toHaveValue("34");
  });

  it("이미_반영된_조정_삭제_409는_안내하고_목록을_다시_읽는다", async () => {
    h.GET.mockResolvedValue(page([manual]));
    h.DELETE.mockRejectedValue(new ApiError(409, "SETTLEMENT_ADJUSTMENT_APPLIED", "서버 메시지", null));
    wrap(<AdminAdjustmentsPage />);
    await userEvent.click(await screen.findByRole("button", { name: "삭제" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "삭제" }));
    expect(await screen.findByText("이미 정산에 반영된 조정은 삭제할 수 없습니다")).toBeInTheDocument();
    await waitFor(() => expect(h.GET.mock.calls.length).toBeGreaterThan(1));
  });

  it("차감_등록은_음수_금액과_키를_보낸다", async () => {
    h.GET.mockResolvedValue(page([]));
    h.POST.mockResolvedValue({ data: { data: { adjustmentId: 9 } } });
    wrap(<AdminAdjustmentsPage />);
    await userEvent.click(await screen.findByRole("button", { name: "조정 등록" }));
    await userEvent.click(screen.getByLabelText("차감"));
    await userEvent.type(screen.getByLabelText(/^금액/), "10,000");
    expect(screen.getByText("차감은 정산에서 −10,000원으로 반영됩니다.")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/^사유/), "파손 보상");
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    await waitFor(() => expect(h.POST).toHaveBeenCalledTimes(1));
    const arg = h.POST.mock.calls[0]?.[1] as { body: object; params: { header: Record<string, string> } };
    expect(arg.body).toEqual({ amount: -10000, reason: "파손 보상" });
    expect(arg.params.header["Idempotency-Key"]).toEqual(expect.any(String));
  });

  it("금액이_0이면_보내지_않고_서버_400은_폼_위에_보인다", async () => {
    h.GET.mockResolvedValue(page([]));
    wrap(<AdminAdjustmentsPage />);
    await userEvent.click(await screen.findByRole("button", { name: "조정 등록" }));
    await userEvent.type(screen.getByLabelText(/^금액/), "0");
    await userEvent.type(screen.getByLabelText(/^사유/), "x");
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(await screen.findByText("금액은 1원 이상의 정수로 입력해 주세요")).toBeInTheDocument();
    expect(h.POST).not.toHaveBeenCalled();
    await userEvent.clear(screen.getByLabelText(/^금액/));
    await userEvent.type(screen.getByLabelText(/^금액/), "100");
    h.POST.mockRejectedValue(new ApiError(400, "SETTLEMENT_ADJUSTMENT_INVALID", "조정 금액이 올바르지 않습니다.", null));
    await userEvent.click(screen.getByRole("button", { name: "등록" }));
    expect(await screen.findByText("조정 금액이 올바르지 않습니다.")).toBeInTheDocument();
  });
});
