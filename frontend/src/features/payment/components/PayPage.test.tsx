import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PayPage } from "@/features/payment/components/PayPage";
import { recallOrderId } from "@/features/payment/order-ref-store";
import { queryKeys } from "@/lib/query-keys";

const ORDER_NO = "K20261112142110Q3ZT8B";

const h = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), replace: vi.fn(), push: vi.fn(), search: "" }));

vi.mock("@/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/client")>()),
  api: { GET: h.GET, POST: h.POST },
}));
vi.mock("next/navigation", () => {
  const router = { replace: h.replace, push: h.push };
  return { useRouter: () => router, useSearchParams: () => new URLSearchParams(h.search) };
});

function order(over: Record<string, unknown> = {}) {
  return {
    orderId: 42,
    orderNo: ORDER_NO,
    status: "PENDING_PAYMENT",
    totalAmount: 48000,
    expiresAt: "2999-01-01T00:00:00",
    payment: { amount: 48000, refundedAmount: 0, status: "READY" },
    sellerOrders: [{ items: [{ productName: "티셔츠" }, { productName: "양말" }] }],
    ...over,
  };
}

function mount(qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  return render(
    <StrictMode>
      <QueryClientProvider client={qc}>
        <PayPage orderId={42} />
      </QueryClientProvider>
    </StrictMode>,
  );
}

describe("PayPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    h.search = "";
    h.GET.mockResolvedValue({ data: { data: order() } });
  });

  it("주문_요약과_서버가_준_결제_금액을_보여주고_자동으로_결제창을_열지_않는다", async () => {
    mount();
    expect(await screen.findByRole("button", { name: "결제하기" })).toBeInTheDocument();
    expect(screen.getByText("티셔츠 외 1건")).toBeInTheDocument();
    expect(screen.getByText("48,000원")).toBeInTheDocument();
    expect(screen.getByRole("timer")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("auto면_결제창을_한_번_열고_결제를_연타해도_승인_화면으로_한_번만_간다", async () => {
    h.search = "auto=1";
    mount();
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("모의 결제");
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
    expect(recallOrderId(ORDER_NO)).toBe(42);

    const pay = screen.getByRole("button", { name: "결제" });
    await userEvent.dblClick(pay);
    expect(h.push).toHaveBeenCalledTimes(1);
    expect(h.push.mock.calls[0]?.[0]).toMatch(
      new RegExp(`^/checkout/success\\?oid=42&orderId=${ORDER_NO}&paymentKey=mock-[0-9a-f-]{36}&amount=48000$`),
    );
    expect(h.POST).not.toHaveBeenCalled();
  });

  it("닫기는_결제창만_닫고_아무것도_호출하지_않는다", async () => {
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "결제하기" }));
    await userEvent.click(screen.getByRole("button", { name: "닫기" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(h.push).not.toHaveBeenCalled();
    expect(h.replace).not.toHaveBeenCalled();
    expect(h.POST).not.toHaveBeenCalled();
    expect(h.GET).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["이미_결제된_주문", { status: "PAID", payment: { amount: 48000, refundedAmount: 0, status: "APPROVED" } }],
    ["취소된_주문", { status: "CANCELED" }],
    ["기한이_지난_주문", { expiresAt: "2020-01-01T00:00:00" }],
    ["결제가_진행_중인_주문", { payment: { amount: 48000, refundedAmount: 0, status: "IN_PROGRESS" } }],
    ["결제_결과를_모르는_주문", { payment: { amount: 48000, refundedAmount: 0, status: "UNKNOWN" } }],
  ])("%s은_결제창을_열지_않고_주문_상세로_보낸다", async (_name, over) => {
    h.search = "auto=1";
    h.GET.mockResolvedValue({ data: { data: order(over) } });
    mount();
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/42"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "결제하기" })).not.toBeInTheDocument();
  });

  it("캐시에_남은_결제_대기_상태로는_결제창을_열지_않는다", async () => {
    h.search = "auto=1";
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } });
    qc.setQueryData(queryKeys.orders.detail(42), order());
    h.GET.mockResolvedValue({ data: { data: order({ payment: { amount: 48000, refundedAmount: 0, status: "UNKNOWN" } }) } });
    mount(qc);
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/42"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
