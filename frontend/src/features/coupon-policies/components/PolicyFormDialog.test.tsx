import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { PolicyFormDialog } from "@/features/coupon-policies/components/PolicyFormDialog";

vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

const onSubmit = vi.fn();
const onClose = vi.fn();

function fillValid() {
  fireEvent.change(screen.getByLabelText(/^쿠폰 이름/), { target: { value: "연말 쿠폰" } });
  fireEvent.change(screen.getByLabelText(/^할인 금액/), { target: { value: "1000" } });
  fireEvent.change(screen.getByLabelText(/^시작/), { target: { value: "2026-12-01T00:00" } });
  fireEvent.change(screen.getByLabelText(/^종료/), { target: { value: "2026-12-31T23:59" } });
}

async function submit() {
  await userEvent.click(screen.getByRole("button", { name: "쿠폰 등록" }));
}

describe("PolicyFormDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onSubmit.mockResolvedValue(undefined);
    render(<PolicyFormDialog scope="seller" notice={[]} onSubmit={onSubmit} onClose={onClose} />);
    fillValid();
  });

  it("유효한_양식을_제출하면_비어_있지_않은_키와_함께_onSubmit을_부른다", async () => {
    await submit();
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const [body, key] = onSubmit.mock.calls[0] as [{ name: string; discountValue: number }, string];
    expect(body.name).toBe("연말 쿠폰");
    expect(body.discountValue).toBe(1000);
    expect(key).toEqual(expect.any(String));
    expect(key.length).toBeGreaterThan(0);
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("네트워크_오류_뒤_같은_값으로_다시_제출하면_같은_키를_쓴다", async () => {
    onSubmit.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    await submit();
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    await submit();
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(2));
    expect(onSubmit.mock.calls[1]?.[1]).toBe(onSubmit.mock.calls[0]?.[1]);
    expect(onClose).not.toHaveBeenCalled();
  });

  it("서버가_4xx로_답한_뒤에는_새_키를_쓴다", async () => {
    onSubmit.mockRejectedValue(new ApiError(400, "COUPON_POLICY_INVALID", "잘못된 정책", null));
    await submit();
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    await submit();
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(2));
    expect(onSubmit.mock.calls[1]?.[1]).not.toBe(onSubmit.mock.calls[0]?.[1]);
  });
});
