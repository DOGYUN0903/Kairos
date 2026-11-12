import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { ConfirmPage } from "@/features/payment/components/ConfirmPage";

const ORDER_NO = "K20261112142110Q3ZT8B";

const h = vi.hoisted(() => ({
  POST: vi.fn(),
  GET: vi.fn(),
  replace: vi.fn(),
  search: "",
  status: "authenticated" as "loading" | "anonymous" | "authenticated",
}));

vi.mock("@/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/client")>()),
  api: { POST: h.POST, GET: h.GET },
}));
vi.mock("next/navigation", () => {
  const router = { replace: h.replace, push: vi.fn() };
  return { useRouter: () => router, useSearchParams: () => new URLSearchParams(h.search) };
});
vi.mock("@/auth/useAuth", () => ({ useAuth: () => ({ status: h.status }) }));

function approved() {
  return { data: { data: { orderId: 42, orderNo: ORDER_NO, orderStatus: "PAID", paymentStatus: "APPROVED" } } };
}

function pendingOrder() {
  return {
    data: {
      data: {
        orderId: 42,
        orderNo: ORDER_NO,
        status: "PENDING_PAYMENT",
        payment: { amount: 48000, refundedAmount: 0, status: "IN_PROGRESS" },
        sellerOrders: [],
      },
    },
  };
}

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <StrictMode>
      <QueryClientProvider client={qc}>
        <ConfirmPage />
      </QueryClientProvider>
    </StrictMode>,
  );
}

const sentKeys = () => h.POST.mock.calls.map((c) => (c[1] as { params: { header: Record<string, string> } }).params.header["Idempotency-Key"]);

describe("ConfirmPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    h.status = "authenticated";
    h.search = `oid=42&orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`;
  });

  it("승인_요청은_마운트당_한_번만_나간다", async () => {
    h.POST.mockResolvedValue(approved());
    mount();

    // StrictMode 의 이중 effect 에서도 응답을 버리지 않고 완료 화면으로 간다.
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/42/complete"));
    expect(h.POST).toHaveBeenCalledTimes(1);
    expect(h.POST).toHaveBeenCalledWith("/api/v1/payments/confirm", {
      params: { header: { "Idempotency-Key": expect.stringMatching(/^[0-9a-f-]{36}$/) } },
      body: { orderNo: ORDER_NO, paymentKey: "mock-a", amount: 48000 },
    });
    expect(h.replace).toHaveBeenCalledTimes(1);
  });

  it("다시_마운트해도_같은_Idempotency_Key를_보낸다", async () => {
    h.POST.mockResolvedValue(approved());
    const first = mount();
    await waitFor(() => expect(h.replace).toHaveBeenCalledTimes(1));
    first.unmount();

    mount();
    await waitFor(() => expect(h.replace).toHaveBeenCalledTimes(2));
    expect(h.POST).toHaveBeenCalledTimes(2);
    const keys = sentKeys();
    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBe(keys[0]);
  });

  it("네트워크_오류면_확인_중을_보여주고_완료나_실패_문구가_없다", async () => {
    h.POST.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    h.GET.mockResolvedValue(pendingOrder());
    mount();

    expect(await screen.findByText("결제 확인 중")).toBeInTheDocument();
    expect(screen.getByText(/결제 결과를 확인하고 있습니다/)).toBeInTheDocument();
    await waitFor(() => expect(h.GET).toHaveBeenCalled());
    expect(document.body.textContent).not.toMatch(/완료|실패|거절|취소/);
    expect(screen.getByRole("status")).toHaveAttribute("aria-live", "polite");
    expect(h.POST).toHaveBeenCalledTimes(1);
    expect(h.replace).not.toHaveBeenCalled();
  });

  it("인증_확인이_끝나기_전에는_승인을_보내지_않는다", async () => {
    h.status = "loading";
    h.POST.mockResolvedValue(approved());
    const view = mount();
    expect(h.POST).not.toHaveBeenCalled();

    h.status = "authenticated";
    view.rerender(
      <StrictMode>
        <QueryClientProvider client={new QueryClient()}>
          <ConfirmPage />
        </QueryClientProvider>
      </StrictMode>,
    );
    await waitFor(() => expect(h.POST).toHaveBeenCalledTimes(1));
  });

  it("거절되면_주문_취소와_장바구니_안내를_보여준다", async () => {
    h.POST.mockRejectedValue(new ApiError(402, "PAYMENT_REJECTED", "카드사 거절", null));
    mount();

    expect(await screen.findByText(/결제가 거절되어 주문이 취소되었습니다/)).toBeInTheDocument();
    expect(screen.getByText(/담아 둔 상품은 장바구니에 그대로 있습니다/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "장바구니로" })).toHaveAttribute("href", "/cart");
    expect(h.replace).not.toHaveBeenCalled();
  });

  it("확인_중에_주문이_결제_완료로_바뀌면_완료_화면으로_간다", async () => {
    h.POST.mockRejectedValue(new ApiError(500, "INTERNAL_ERROR", "서버 오류", null));
    h.GET.mockResolvedValue({ data: { data: { ...pendingOrder().data.data, status: "PAID", payment: { amount: 48000, refundedAmount: 0, status: "APPROVED" } } } });
    mount();
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/42/complete"));
  });

  it("확인_중에_주문이_취소되면_거절_안내로_바뀐다", async () => {
    h.POST.mockRejectedValue(new ApiError(409, "PAYMENT_IN_PROGRESS", "진행 중", null));
    h.GET.mockResolvedValue({ data: { data: { ...pendingOrder().data.data, status: "CANCELED", payment: { amount: 48000, refundedAmount: 0, status: "FAILED" } } } });
    mount();
    expect(await screen.findByText(/결제가 거절되어 주문이 취소되었습니다/)).toBeInTheDocument();
  });

  it("조회한_주문의_주문번호가_다르면_그_주문의_상태를_믿지_않는다", async () => {
    h.POST.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    h.GET.mockResolvedValue({ data: { data: { ...pendingOrder().data.data, orderNo: "KOTHERORDER0000000000", status: "PAID" } } });
    mount();

    expect(await screen.findByText("결제 확인 중")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "주문 내역" })).toHaveAttribute("href", "/my/orders");
    expect(h.replace).not.toHaveBeenCalled();
  });

  it("주문_ID를_모르면_확인_중과_주문_내역_링크를_보여준다", async () => {
    h.search = `orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`;
    h.POST.mockResolvedValue({ data: { data: { orderId: 42, orderNo: ORDER_NO, orderStatus: "PENDING_PAYMENT", paymentStatus: "UNKNOWN" } } });
    mount();

    expect(await screen.findByText("결제 확인 중")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문 내역" })).toHaveAttribute("href", "/my/orders");
    expect(h.GET).not.toHaveBeenCalled();
  });

  it("쿼리가_잘못되면_승인을_보내지_않고_주문_내역으로_안내한다", () => {
    h.search = `oid=42&orderId=${ORDER_NO}&paymentKey=mock-a&amount=abc`;
    mount();
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문 내역" })).toHaveAttribute("href", "/my/orders");
    expect(h.POST).not.toHaveBeenCalled();
  });

  it("서버가_답한_오류는_메시지와_주문_상세_링크를_보여준다", async () => {
    h.POST.mockRejectedValue(new ApiError(400, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 주문 금액과 다릅니다", null));
    mount();
    expect(await screen.findByText("결제 금액이 주문 금액과 다릅니다")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문 상세" })).toHaveAttribute("href", "/my/orders/42");
  });
});
