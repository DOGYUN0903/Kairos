import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { OrderItemResult } from "@/features/orders/api";
import { CancelItemDialog } from "@/features/orders/components/CancelItemDialog";

const h = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), toast: vi.fn() }));

vi.mock("@/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/client")>()),
  api: { GET: h.GET, POST: h.POST },
}));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

const item = { orderItemId: 7, productName: "램스울 크루넥 니트", brandName: "브랜드", optionDisplayName: "오트밀 / M", quantity: 1, thumbnailUrl: null } as OrderItemResult;
const preview = { orderItemId: 7, paidAmount: 30000, shippingFeeRefund: 0, shippingFeeDeducted: 3000, shippingFeeReturned: 0, refundAmount: 27000, pointsRefund: 0 };

function mount(onClose = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <CancelItemDialog orderId={42} item={item} onClose={onClose} />
    </QueryClientProvider>,
  );
  return onClose;
}

async function fillAndSubmit() {
  await userEvent.type(await screen.findByLabelText(/취소 사유/), "사이즈를 잘못 골랐어요");
  await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
}

describe("CancelItemDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.GET.mockResolvedValue({ data: { data: preview } });
  });

  it("열리면_서버_미리보기를_보여주고_사유가_없으면_보내지_않는다", async () => {
    mount();
    expect(await screen.findByText("27,000원")).toBeInTheDocument();
    expect(screen.getByText("무료배송 기준 금액 아래로 내려가 배송비가 차감됩니다.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    expect(await screen.findByText("취소 사유를 입력해 주세요.")).toBeInTheDocument();
    expect(h.POST).not.toHaveBeenCalled();
  });

  it("취소_성공은_응답의_실제_환불액을_알리고_창을_닫으며_두_번_눌러도_한_번만_보낸다", async () => {
    h.POST.mockResolvedValue({ data: { data: { orderItemId: 7, itemStatus: "CANCELED", refundAmount: 27000, shippingFeeDeducted: 3000, pointsRefund: 0 } } });
    const onClose = mount();
    await userEvent.type(await screen.findByLabelText(/취소 사유/), "사이즈");
    await userEvent.dblClick(screen.getByRole("button", { name: "취소하기" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.POST).toHaveBeenCalledTimes(1);
    expect(h.toast).toHaveBeenCalledWith({ title: "취소되었습니다. 환불 27,000원 (배송비 3,000원 차감)" });
  });

  it("취소_확인_중_응답은_확인_안내를_하고_창을_닫는다", async () => {
    h.POST.mockResolvedValue({ data: { data: { orderItemId: 7, itemStatus: "CANCEL_REQUESTED", refundAmount: 0, shippingFeeDeducted: 0, pointsRefund: 0 } } });
    const onClose = mount();
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledWith({ title: "취소를 확인하고 있습니다" });
  });

  it("취소_거절은_창을_유지하고_안내한다", async () => {
    h.POST.mockRejectedValue(new ApiError(409, "PAYMENT_CANCEL_FAILED", "서버 메시지", null));
    const onClose = mount();
    await fillAndSubmit();
    expect(await screen.findByText("취소가 거절되었습니다. 주문은 그대로입니다")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("취소_불가_409는_서버_메시지를_알리고_창을_닫는다", async () => {
    h.POST.mockRejectedValue(new ApiError(409, "ORDER_ITEM_NOT_CANCELABLE", "이미 발송된 상품입니다.", null));
    const onClose = mount();
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledWith({ title: "이미 발송된 상품입니다.", variant: "destructive" });
  });

  it("미리보기가_409면_서버_메시지를_보이고_취소하기를_막는다", async () => {
    h.GET.mockRejectedValue(new ApiError(409, "ORDER_ITEM_NOT_CANCELABLE", "이미 발송된 상품은 취소할 수 없습니다.", null));
    mount();
    expect(await screen.findByText("이미 발송된 상품은 취소할 수 없습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "취소하기" })).toBeDisabled();
  });
});
