import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { OrderItemResult } from "@/features/orders/api";
import { ReturnStatusLine } from "@/features/returns/components/ReturnStatusLine";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ DELETE: vi.fn(), toast: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { DELETE: h.DELETE } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

const item = (status: string, itemStatus = "RETURN_REQUESTED", refundAmount = 0, returnFee = 0) =>
  ({
    orderItemId: 7,
    productName: "와이드 코튼 팬츠",
    status: itemStatus,
    returnRequest: { returnId: 11, reason: "CHANGE_OF_MIND", status, feeBearer: "BUYER", refundAmount, returnFee, requestedAt: "2026-11-10T10:00:00" },
  }) as unknown as OrderItemResult;

function mount(it: OrderItemResult) {
  const qc = new QueryClient();
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <ReturnStatusLine orderId={42} item={it} />
    </QueryClientProvider>,
  );
  return { invalidate };
}

describe("ReturnStatusLine", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.DELETE.mockResolvedValue({ data: { data: {} } });
  });

  it("철회된_반품은_아무것도_그리지_않는다", () => {
    mount(item("WITHDRAWN", "ORDERED"));
    expect(screen.queryByText(/반품/)).toBeNull();
  });

  it("완료된_반품은_서버_환불_금액과_반품_상세_링크를_보인다", () => {
    mount(item("COMPLETED", "RETURNED", 14000));
    expect(screen.getByText((_, el) => el?.tagName === "SPAN" && el.textContent === "환불 14,000원")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "반품 상세" })).toHaveAttribute("href", "/my/returns/11");
    expect(screen.queryByRole("button", { name: "철회" })).toBeNull();
  });

  it("거절된_항목은_상태_칩을_보인다", () => {
    mount(item("REJECTED", "ORDERED"));
    expect(screen.getByText("반품 거절")).toBeInTheDocument();
  });

  it("승인된_반품에는_철회가_없다", () => {
    mount(item("APPROVED", "RETURN_APPROVED"));
    expect(screen.queryByRole("button", { name: "철회" })).toBeNull();
  });

  it("철회는_확인_창_뒤에_한_번만_나가고_주문_상세를_다시_읽는다", async () => {
    const { invalidate } = mount(item("REQUESTED"));
    await userEvent.click(screen.getByRole("button", { name: "철회" }));
    const dialog = await screen.findByRole("dialog");
    expect(h.DELETE).not.toHaveBeenCalled();
    await userEvent.dblClick(within(dialog).getByRole("button", { name: "반품 철회" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "반품 요청을 철회했습니다" }));
    expect(h.DELETE).toHaveBeenCalledTimes(1);
    expect(h.DELETE).toHaveBeenCalledWith("/api/v1/returns/{returnId}", { params: { path: { returnId: 11 } } });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.detail(42) });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.returns.root });
  });

  it("그사이_승인되어_409면_서버_메시지를_보이고_다시_읽는다", async () => {
    h.DELETE.mockRejectedValue(new ApiError(409, "RETURN_INVALID_STATE_TRANSITION", "이미 판매자가 승인한 반품이라 철회할 수 없습니다.", null));
    const { invalidate } = mount(item("REQUESTED"));
    await userEvent.click(screen.getByRole("button", { name: "철회" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "반품 철회" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "이미 판매자가 승인한 반품이라 철회할 수 없습니다.", variant: "destructive" }));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.detail(42) });
  });
});
