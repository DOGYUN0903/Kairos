import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { CancelItemResponse } from "@/features/orders/api";
import { ItemCancelDialog, type CancelDialogItem } from "@/features/orders/components/ItemCancelDialog";

const h = vi.hoisted(() => ({ toast: vi.fn() }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

const item: CancelDialogItem = { orderItemId: 7, productName: "램스울 크루넥 니트", brandName: "브랜드", optionDisplayName: "오트밀 / M", quantity: 1, thumbnailUrl: null };

function response(itemStatus: CancelItemResponse["itemStatus"], over: Partial<CancelItemResponse> = {}): CancelItemResponse {
  return { orderItemId: 7, itemStatus, refundAmount: 0, shippingFeeDeducted: 0, pointsRefund: 0, ...over } as CancelItemResponse;
}

type Cancel = (reason: string, key: string) => Promise<CancelItemResponse>;

function mount(cancel: Cancel, props: { canSubmit?: boolean; onSpecialError?: (e: unknown) => boolean } = {}) {
  const onClose = vi.fn();
  render(<ItemCancelDialog item={item} cancel={cancel} notice={<p>환불 안내</p>} onClose={onClose} {...props} />);
  return onClose;
}

async function fillAndSubmit(reason = "사이즈를 잘못 골랐어요") {
  await userEvent.type(screen.getByLabelText(/취소 사유/), reason);
  await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
}

describe("ItemCancelDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("제목과_버튼_문구가_세_화면에서_같고_안내_영역을_그대로_보여준다", () => {
    mount(vi.fn<Cancel>());
    expect(screen.getByRole("dialog", { name: "주문 항목 취소" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "취소하기" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "닫기" })).toBeInTheDocument();
    expect(screen.getByText("환불 안내")).toBeInTheDocument();
    expect(screen.getByText("램스울 크루넥 니트")).toBeInTheDocument();
  });

  it("사유가_없거나_500자를_넘으면_보내지_않는다", async () => {
    const cancel = vi.fn<Cancel>();
    mount(cancel);
    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    expect(await screen.findByText("취소 사유를 입력해 주세요.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/취소 사유/), { target: { value: "가".repeat(501) } });
    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    expect(await screen.findByText("취소 사유는 500자 이하로 입력해 주세요.")).toBeInTheDocument();
    expect(cancel).not.toHaveBeenCalled();
  });

  it("취소_성공은_응답의_실제_환불액을_알리고_창을_닫으며_두_번_눌러도_한_번만_보낸다", async () => {
    const cancel = vi.fn<Cancel>().mockResolvedValue(response("CANCELED", { refundAmount: 30000 }));
    const onClose = mount(cancel);
    await userEvent.type(screen.getByLabelText(/취소 사유/), " 사이즈 ");
    await userEvent.dblClick(screen.getByRole("button", { name: "취소하기" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(cancel).toHaveBeenCalledTimes(1);
    expect(cancel).toHaveBeenCalledWith("사이즈", expect.stringMatching(/^[0-9a-f-]{36}$/));
    expect(h.toast).toHaveBeenCalledWith({ title: "취소되었습니다. 환불 30,000원" });
  });

  it("배송비가_차감됐으면_환불액과_함께_차감액을_알린다", async () => {
    const cancel = vi.fn<Cancel>().mockResolvedValue(response("CANCELED", { refundAmount: 27000, shippingFeeDeducted: 3000 }));
    const onClose = mount(cancel);
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledWith({ title: "취소되었습니다. 환불 27,000원 (배송비 3,000원 차감)" });
  });

  it("취소_확인_중_응답은_확인_안내를_하고_창을_닫는다", async () => {
    const cancel = vi.fn<Cancel>().mockResolvedValue(response("CANCEL_REQUESTED"));
    const onClose = mount(cancel);
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledTimes(1);
    expect(h.toast).toHaveBeenCalledWith({ title: "취소를 확인하고 있습니다" });
  });

  it.each(["CANCELED", "CANCEL_REQUESTED"] as const)("접수된_뒤에는_다시_눌러도_취소_요청이_나가지_않는다 (%s)", async (status) => {
    const cancel = vi.fn<Cancel>().mockResolvedValue(response(status, { refundAmount: 30000 }));
    // onClose 가 창을 닫지 않는 동안(부모 상태 반영 전)에도 같은 항목으로 다시 보내면 안 된다.
    const onClose = mount(cancel);
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));

    const submit = screen.getByRole("button", { name: "취소하기" });
    expect(submit).toBeDisabled();
    await userEvent.click(submit);
    // 버튼이 막혀도 Enter·직접 제출로 들어오는 경로까지 막는다.
    fireEvent.submit(submit.closest("form")!);
    fireEvent.submit(submit.closest("form")!);
    await waitFor(() => expect(screen.getByRole("button", { name: "닫기" })).toBeEnabled());
    expect(cancel).toHaveBeenCalledTimes(1);
    expect(h.toast).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["응답이 ORDERED", () => Promise.resolve(response("ORDERED"))],
    ["409 PAYMENT_CANCEL_FAILED", () => Promise.reject(new ApiError(409, "PAYMENT_CANCEL_FAILED", "서버 메시지", null))],
  ])("취소가_거절되면_안내하고_창을_닫지_않는다 (%s)", async (_name, result) => {
    const cancel = vi.fn<Cancel>().mockImplementation(result);
    const onClose = mount(cancel);
    await fillAndSubmit();
    expect(await screen.findByText("취소가 거절되었습니다. 주문은 그대로입니다")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(h.toast).not.toHaveBeenCalled();
    // 거절은 서버가 답한 것이다. 다시 누르면 새 키로 다시 요청한다.
    await waitFor(() => expect(screen.getByRole("button", { name: "취소하기" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    await waitFor(() => expect(cancel).toHaveBeenCalledTimes(2));
    expect(cancel.mock.calls[1]![1]).not.toBe(cancel.mock.calls[0]![1]);
  });

  it("취소_불가_409는_서버_메시지를_알리고_창을_닫는다", async () => {
    const cancel = vi.fn<Cancel>().mockRejectedValue(new ApiError(409, "ORDER_ITEM_NOT_CANCELABLE", "이미 발송된 상품입니다.", null));
    const onClose = mount(cancel);
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledWith({ title: "이미 발송된 상품입니다.", variant: "destructive" });
  });

  it.each([
    ["네트워크 오류", new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null)],
    ["5xx", new ApiError(503, "INTERNAL_ERROR", "서버 오류", null)],
    ["처리 중 409", new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "처리 중", null)],
  ])("응답을_받지_못하면_결과를_확인하지_못했다고_안내한다 (%s)", async (_name, error) => {
    const cancel = vi.fn<Cancel>().mockRejectedValue(error);
    const onClose = mount(cancel);
    await fillAndSubmit();
    await waitFor(() => expect(h.toast).toHaveBeenCalledTimes(1));
    expect(h.toast).toHaveBeenCalledWith({ title: "취소 결과를 확인하지 못했습니다. 주문 상태를 확인해 주세요", variant: "destructive" });
    // 성공도 거절도 말하지 않고 창을 유지한다.
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.queryByText(/거절/)).not.toBeInTheDocument();
    // 결과를 모르는 요청은 같은 키로 다시 보내야 취소가 두 번 처리되지 않는다.
    await waitFor(() => expect(screen.getByRole("button", { name: "취소하기" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "취소하기" }));
    await waitFor(() => expect(cancel).toHaveBeenCalledTimes(2));
    expect(cancel.mock.calls[1]![1]).toBe(cancel.mock.calls[0]![1]);
  });

  it("특수_오류_처리기가_처리하면_공통_안내를_띄우지_않는다", async () => {
    const error = new ApiError(409, "SELLER_NOT_ACTIVE", "판매자 상태", null);
    const cancel = vi.fn<Cancel>().mockRejectedValue(error);
    const onSpecialError = vi.fn(() => true);
    const onClose = mount(cancel, { onSpecialError });
    await fillAndSubmit();
    await waitFor(() => expect(onSpecialError).toHaveBeenCalledWith(error));
    await waitFor(() => expect(screen.getByRole("button", { name: "닫기" })).toBeEnabled());
    expect(h.toast).not.toHaveBeenCalled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("특수_오류_처리기가_처리하지_않은_오류는_공통_안내로_간다", async () => {
    const cancel = vi.fn<Cancel>().mockRejectedValue(new ApiError(409, "ORDER_ITEM_NOT_CANCELABLE", "이미 발송된 상품입니다.", null));
    const onClose = mount(cancel, { onSpecialError: () => false });
    await fillAndSubmit();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(h.toast).toHaveBeenCalledWith({ title: "이미 발송된 상품입니다.", variant: "destructive" });
  });

  it("canSubmit이_false면_취소하기를_막는다", async () => {
    const cancel = vi.fn<Cancel>();
    mount(cancel, { canSubmit: false });
    const submit = screen.getByRole("button", { name: "취소하기" });
    expect(submit).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/취소 사유/), "사유");
    fireEvent.submit(submit.closest("form")!);
    await waitFor(() => expect(screen.getByRole("button", { name: "닫기" })).toBeEnabled());
    expect(cancel).not.toHaveBeenCalled();
  });
});
