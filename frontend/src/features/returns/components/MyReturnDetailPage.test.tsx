import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { MyReturnDetailPage } from "@/features/returns/components/MyReturnDetailPage";

const h = vi.hoisted(() => ({ GET: vi.fn(), DELETE: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET, DELETE: h.DELETE } }));
vi.mock("next/navigation", () => ({ notFound: vi.fn(), useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

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
    status: "REQUESTED",
    returnFee: 0,
    outboundFeeRecovered: 0,
    shippingFeeRefund: 0,
    sellerBorneFee: 0,
    refundAmount: 0,
    detail: null,
    pointsRefund: 0,
    requestedAt: "2026-11-09T11:02:00",
    thumbnailUrl: null,
    optionDisplayName: "오트밀 / M",
    orderNo: "K20261104093015T6QH2K",
    sellerName: "판매자 A",
    paidAmount: 30000,
    autoApproveAt: "2026-11-12T11:02:00",
    ...over,
  }) as ReturnRequestResponse;
const ok = (data: ReturnRequestResponse) => ({ data: { data } });

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <MyReturnDetailPage returnId={5} />
    </QueryClientProvider>,
  );
}

// 서버가 만드는 상태별 금액: 30,000원 불량, 구매자가 낸 배송비 3,000원.
const refunding = { status: "REFUNDING", decidedAt: "2026-11-09T13:40:00", decidedBy: "SELLER", receivedAt: "2026-11-12T10:40:00", refundAmount: 33000, shippingFeeRefund: 3000, sellerBorneFee: 3000 } as const;

describe("MyReturnDetailPage", () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => vi.useRealTimers());

  it("요청됨은_확정_전_안내와_철회_버튼을_보인다", async () => {
    h.GET.mockResolvedValue(ok(ret()));
    mount();
    expect(await screen.findByText("환불 금액은 판매자가 상품을 받은 뒤 확정됩니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "철회" })).toBeInTheDocument();
  });

  it("환불_확인_중은_자동_새로고침_안내를_보이고_2분_뒤에는_새로고침_버튼을_보인다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    h.GET.mockResolvedValue(ok(ret(refunding)));
    mount();
    expect(await screen.findByText("환불을 확인하고 있습니다. 이 화면은 3초마다 자동으로 새로 고쳐집니다.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "새로고침" })).not.toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(120_000);
    });
    expect(await screen.findByRole("button", { name: "새로고침" })).toBeInTheDocument();
    expect(screen.getByText(/환불 확인이 아직 끝나지 않았습니다/)).toBeInTheDocument();
  });

  it("완료는_서버가_준_금액으로_환불_내역을_보인다", async () => {
    h.GET.mockResolvedValue(ok(ret({ ...refunding, status: "COMPLETED", completedAt: "2026-11-12T10:41:00" })));
    mount();
    expect(await screen.findByText("결제 금액")).toBeInTheDocument();
    expect(screen.getByText("30,000원")).toBeInTheDocument();
    expect(screen.getByText("+3,000원")).toBeInTheDocument();
    expect(screen.getByText("33,000원")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "철회" })).not.toBeInTheDocument();
  });
});
