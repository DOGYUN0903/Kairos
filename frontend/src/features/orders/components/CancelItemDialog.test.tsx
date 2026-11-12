import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { OrderItemResult } from "@/features/orders/api";
import { CancelItemDialog } from "@/features/orders/components/CancelItemDialog";
import { queryKeys } from "@/lib/query-keys";

// 공통 동작(접수 가드·거절·응답 없음·특수 오류)은 ItemCancelDialog.test.tsx 가 본다. 여기는 구매자 래퍼(환불 미리보기, 요청 경로, 무효화)만 본다.
const h = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), toast: vi.fn() }));

vi.mock("@/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/client")>()),
  api: { GET: h.GET, POST: h.POST },
}));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

const item = { orderItemId: 7, productName: "램스울 크루넥 니트", brandName: "브랜드", optionDisplayName: "오트밀 / M", quantity: 1, thumbnailUrl: null } as OrderItemResult;
const preview = { orderItemId: 7, paidAmount: 30000, shippingFeeRefund: 0, shippingFeeDeducted: 3000, shippingFeeReturned: 0, refundAmount: 27000, pointsRefund: 0 };

function mount(onClose = vi.fn(), opts: { item?: OrderItemResult; hasCartCoupon?: boolean } = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <CancelItemDialog orderId={42} item={opts.item ?? item} hasCartCoupon={opts.hasCartCoupon} onClose={onClose} />
    </QueryClientProvider>,
  );
  return { onClose, invalidate };
}

async function fillAndSubmit() {
  await userEvent.type(await screen.findByLabelText(/취소 사유/), "사이즈를 잘못 골랐어요");
  await waitFor(() => expect(screen.getByRole("button", { name: "취소하기" })).toBeEnabled());
  await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
}

function expectOrdersInvalidated(invalidate: ReturnType<typeof mount>["invalidate"]) {
  expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.detail(42) });
  expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.list() });
  // 취소하면 쿠폰이 돌아오고 포인트가 환불될 수 있다.
  expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.coupons.mineRoot });
  expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.points.root });
}

describe("CancelItemDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.GET.mockResolvedValue({ data: { data: preview } });
  });

  it("열리면_서버_미리보기를_보여주고_사유가_없으면_보내지_않는다", async () => {
    mount();
    expect(await screen.findByText("27,000원")).toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "주문 항목 취소" })).toBeInTheDocument();
    expect(screen.getByText("무료배송 기준 금액 아래로 내려가 배송비가 차감됩니다.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    expect(await screen.findByText("취소 사유를 입력해 주세요.")).toBeInTheDocument();
    expect(h.POST).not.toHaveBeenCalled();
  });

  it("항목에_포인트와_상품_쿠폰이_있고_주문에_장바구니_쿠폰이_있으면_안내_문구를_보인다", async () => {
    mount(vi.fn(), { item: { ...item, pointsAllocated: 5625, productCouponId: 3 } as OrderItemResult, hasCartCoupon: true });
    expect(await screen.findByText("사용한 포인트 5,625P가 함께 돌아옵니다")).toBeInTheDocument();
    expect(screen.getByText("사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다")).toBeInTheDocument();
    expect(screen.getByText("장바구니 쿠폰은 주문 전체를 취소해야 돌아옵니다")).toBeInTheDocument();
  });

  it("혜택이_없는_항목에는_안내_문구가_없다", async () => {
    mount();
    await screen.findByText("27,000원");
    expect(screen.queryByLabelText("쿠폰·포인트 안내")).toBeNull();
  });

  it("미리보기를_확인하기_전에는_취소하기를_막는다", async () => {
    h.GET.mockReturnValue(new Promise(() => {}));
    mount();
    expect(await screen.findByText(/환불 예정 금액을 확인하고 있습니다/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "취소하기" })).toBeDisabled();
  });

  it("취소_성공은_응답의_실제_환불액을_알리고_창을_닫으며_두_번_눌러도_한_번만_보낸다", async () => {
    h.POST.mockResolvedValue({ data: { data: { orderItemId: 7, itemStatus: "CANCELED", refundAmount: 27000, shippingFeeDeducted: 3000, pointsRefund: 0 } } });
    const { onClose, invalidate } = mount();
    await userEvent.type(await screen.findByLabelText(/취소 사유/), "사이즈");
    await waitFor(() => expect(screen.getByRole("button", { name: "취소하기" })).toBeEnabled());
    await userEvent.dblClick(screen.getByRole("button", { name: "취소하기" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.POST).toHaveBeenCalledTimes(1);
    expect(h.POST).toHaveBeenCalledWith("/api/v1/orders/{orderId}/items/{orderItemId}/cancel", {
      params: { path: { orderId: 42, orderItemId: 7 }, header: { "Idempotency-Key": expect.stringMatching(/^[0-9a-f-]{36}$/) } },
      body: { reason: "사이즈" },
    });
    expect(h.toast).toHaveBeenCalledWith({ title: "취소되었습니다. 환불 27,000원 (배송비 3,000원 차감)" });
    expectOrdersInvalidated(invalidate);
  });

  it.each([
    ["서버가 거절", new ApiError(409, "PAYMENT_CANCEL_FAILED", "서버 메시지", null)],
    ["응답 없음", new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null)],
  ])("취소_요청이_실패해도_주문_상세와_목록을_다시_읽게_한다 (%s)", async (_name, error) => {
    h.POST.mockRejectedValue(error);
    const { onClose, invalidate } = mount();
    await fillAndSubmit();
    await waitFor(() => expect(h.POST).toHaveBeenCalledTimes(1));
    await waitFor(() => expectOrdersInvalidated(invalidate));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("미리보기가_409면_서버_메시지를_보이고_취소하기를_막는다", async () => {
    h.GET.mockRejectedValue(new ApiError(409, "ORDER_ITEM_NOT_CANCELABLE", "이미 발송된 상품은 취소할 수 없습니다.", null));
    mount();
    expect(await screen.findByText("이미 발송된 상품은 취소할 수 없습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "취소하기" })).toBeDisabled();
  });
});
