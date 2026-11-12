import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { OrderItemResult } from "@/features/orders/api";
import { ReturnRequestDialog } from "@/features/returns/components/ReturnRequestDialog";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), toast: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/api/client")>()),
  api: { GET: h.GET, POST: h.POST },
}));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

const item = { orderItemId: 7, productName: "베이직 롱 슬리브 티셔츠", brandName: "브랜드 A", optionDisplayName: "화이트 / L", quantity: 1, thumbnailUrl: null } as OrderItemResult;
const preview = (over: Record<string, unknown> = {}) => ({
  data: {
    data: {
      orderItemId: 7,
      reason: "CHANGE_OF_MIND",
      feeBearer: "BUYER",
      paidAmount: 20000,
      returnFee: 3000,
      outboundFeeRecovered: 3000,
      shippingFeeRefund: 0,
      refundAmount: 14000,
      pointsRefund: 0,
      productCouponReturnable: false,
      ...over,
    },
  },
});

function mount(onClose = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <ReturnRequestDialog orderId={42} item={item} onClose={onClose} />
    </QueryClientProvider>,
  );
  return { onClose, invalidate };
}

const pick = (name: string) => userEvent.click(screen.getByRole("radio", { name }));
const submitBtn = () => screen.getByRole("button", { name: "반품 요청" });

describe("ReturnRequestDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.GET.mockResolvedValue(preview());
  });

  it("사유를_고르기_전에는_안내만_보이고_미리보기를_부르지_않는다", () => {
    mount();
    expect(screen.getByText("사유를 선택하면 환불 예정 금액을 보여 드립니다")).toBeInTheDocument();
    expect(h.GET).not.toHaveBeenCalled();
  });

  it("사유를_고르면_서버_미리보기를_예상_금액으로_보여준다", async () => {
    mount();
    await pick("단순 변심");
    expect(await screen.findByText("14,000원")).toBeInTheDocument();
    expect(screen.getByText("반품 배송비는 구매자 부담입니다")).toBeInTheDocument();
    expect(screen.getByText("무료배송으로 받은 상품이라 발송 배송비가 함께 차감됩니다")).toBeInTheDocument();
    expect(screen.getByText(/실제 환불 금액은 판매자가 상품을 받은 뒤 확정됩니다/)).toBeInTheDocument();
    expect(screen.queryByText(/사용한 상품 쿠폰/)).toBeNull();
  });

  it("사유를_바꾸면_미리보기를_다시_요청한다", async () => {
    mount();
    await pick("단순 변심");
    await screen.findByText("14,000원");
    h.GET.mockResolvedValue(preview({ reason: "DEFECTIVE", feeBearer: "SELLER", returnFee: 0, outboundFeeRecovered: 0, refundAmount: 20000, productCouponReturnable: true }));
    await pick("상품 불량");
    await waitFor(() => expect(screen.getAllByText("20,000원")).toHaveLength(2));
    expect(h.GET).toHaveBeenCalledTimes(2);
    expect(h.GET).toHaveBeenLastCalledWith("/api/v1/orders/{orderId}/items/{orderItemId}/return-preview", {
      params: { path: { orderId: 42, orderItemId: 7 }, query: { reason: "DEFECTIVE" } },
    });
    expect(screen.getByText("판매자 부담이라 차감이 없습니다")).toBeInTheDocument();
    expect(screen.getByText("사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다")).toBeInTheDocument();
  });

  it("미리보기가_409면_요청_버튼이_막힌다", async () => {
    h.GET.mockRejectedValue(new ApiError(409, "RETURN_WINDOW_EXPIRED", "반품 가능 기간이 지났습니다.", null));
    mount();
    await pick("단순 변심");
    expect(await screen.findByText("반품 가능 기간이 지났습니다.")).toBeInTheDocument();
    expect(submitBtn()).toBeDisabled();
  });

  it("미리보기가_네트워크_오류여도_요청할_수_있다", async () => {
    h.GET.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    h.POST.mockResolvedValue({ data: { data: { returnId: 5 } } });
    const { onClose } = mount();
    await pick("단순 변심");
    expect(await screen.findByText(/환불 예정 금액을 불러오지 못했습니다/)).toBeInTheDocument();
    expect(submitBtn()).toBeEnabled();
    await userEvent.click(submitBtn());
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.POST).toHaveBeenCalledTimes(1);
  });

  it("사유를_고르지_않고_요청하면_안내를_보이고_보내지_않는다", async () => {
    mount();
    await userEvent.click(submitBtn());
    expect(await screen.findByText("반품 사유를 선택해 주세요")).toBeInTheDocument();
    expect(h.POST).not.toHaveBeenCalled();
  });

  it("요청은_멱등_키와_함께_한_번만_나가고_성공하면_토스트와_무효화를_한다", async () => {
    h.POST.mockResolvedValue({ data: { data: { returnId: 5 } } });
    const { onClose, invalidate } = mount();
    await pick("상품 불량");
    await screen.findByText("14,000원");
    await userEvent.type(screen.getByLabelText(/상세 내용/), "단추가 떨어졌어요");
    await userEvent.dblClick(submitBtn());
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.POST).toHaveBeenCalledTimes(1);
    expect(h.POST).toHaveBeenCalledWith("/api/v1/orders/{orderId}/items/{orderItemId}/returns", {
      params: { path: { orderId: 42, orderItemId: 7 }, header: { "Idempotency-Key": expect.stringMatching(/^[0-9a-f-]{36}$/) } },
      body: { reason: "DEFECTIVE", detail: "단추가 떨어졌어요" },
    });
    expect(h.toast).toHaveBeenCalledWith({ title: "반품을 요청했습니다. 판매자가 3일 안에 확인합니다" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.detail(42) });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.returns.root });
  });

  it("상세_내용을_비우면_본문에_detail이_없다", async () => {
    h.POST.mockResolvedValue({ data: { data: { returnId: 5 } } });
    mount();
    await pick("오배송");
    await userEvent.click(submitBtn());
    await waitFor(() => expect(h.POST).toHaveBeenCalledTimes(1));
    expect(h.POST.mock.calls[0]?.[1]).toMatchObject({ body: { reason: "WRONG_DELIVERY" } });
    expect(h.POST.mock.calls[0]?.[1].body).not.toHaveProperty("detail");
  });

  it("요청이_409면_창을_닫고_주문_상세를_다시_읽는다", async () => {
    h.POST.mockRejectedValue(new ApiError(409, "RETURN_ALREADY_REQUESTED", "이미 반품이 요청된 상품입니다.", null));
    const { onClose, invalidate } = mount();
    await pick("단순 변심");
    await userEvent.click(submitBtn());
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledWith({ title: "이미 반품이 요청된 상품입니다.", variant: "destructive" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.detail(42) });
  });

  it("요청이_400이면_창_안에_안내하고_닫지_않는다", async () => {
    h.POST.mockRejectedValue(new ApiError(400, "RETURN_INVALID", "반품 요청 내용을 확인해 주세요.", null));
    const { onClose } = mount();
    await pick("단순 변심");
    await userEvent.click(submitBtn());
    expect(await screen.findByRole("alert")).toHaveTextContent("반품 요청 내용을 확인해 주세요.");
    expect(onClose).not.toHaveBeenCalled();
  });
});
