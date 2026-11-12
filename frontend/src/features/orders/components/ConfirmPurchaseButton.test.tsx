import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { ConfirmPurchaseButton } from "@/features/orders/components/ConfirmPurchaseButton";

const h = vi.hoisted(() => ({ PATCH: vi.fn(), toast: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { PATCH: h.PATCH } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

describe("ConfirmPurchaseButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.PATCH.mockResolvedValue({ data: { data: {} } });
  });

  it("구매_확정을_연달아_눌러도_요청은_한_번만_나간다", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "구매 확정" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.dblClick(within(dialog).getByRole("button", { name: "구매 확정" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "구매가 확정되었습니다." })));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });

  it("확인_창에_적립_안내_문구가_보인다", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "구매 확정" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/구매 확정 시 결제 금액의 1%가 포인트로 적립됩니다/)).toBeInTheDocument();
  });

  it("성공_토스트에_포인트_내역_링크가_있고_포인트를_무효화한다", async () => {
    const qc = new QueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    render(
      <QueryClientProvider client={qc}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "구매 확정" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "구매 확정" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalled());
    const arg = h.toast.mock.calls[0]?.[0] as { action: React.ReactElement<{ children: React.ReactElement<{ href: string }> }> };
    expect(arg.action.props.children.props.href).toBe("/my/points");
    expect(spy).toHaveBeenCalledWith({ queryKey: ["points"] });
  });

  it("확인_창에_반품_불가_안내가_보인다", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "구매 확정" }));
    expect(within(await screen.findByRole("dialog")).getByText(/확정하면 이 판매자의 상품은 반품할 수 없습니다/)).toBeInTheDocument();
  });

  it("열린_반품이_있으면_버튼이_비활성이고_이유가_보인다", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} blockedReason="반품이 진행 중인 상품이 있어 확정할 수 없습니다" />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("button", { name: "구매 확정" })).toBeDisabled();
    expect(screen.getByText("반품이 진행 중인 상품이 있어 확정할 수 없습니다")).toBeInTheDocument();
  });

  it("서버가_열린_반품으로_거절하면_안내_문구를_토스트로_보인다", async () => {
    h.PATCH.mockRejectedValue(new ApiError(409, "SELLER_ORDER_HAS_OPEN_RETURN", "서버 문구", null));
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "구매 확정" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "구매 확정" }));
    await waitFor(() =>
      expect(h.toast).toHaveBeenCalledWith({
        title: "반품이 진행 중인 상품이 있어 확정할 수 없습니다. 반품을 철회하거나 완료된 뒤 다시 시도해 주세요",
        variant: "destructive",
      }),
    );
  });
});
