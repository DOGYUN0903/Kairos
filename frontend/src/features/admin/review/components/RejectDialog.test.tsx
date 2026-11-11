import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RejectDialog } from "./RejectDialog";

function setup(onSubmit = vi.fn(async () => true)) {
  render(<RejectDialog open title="테스트 반려" submitting={false} onSubmit={onSubmit} onCancel={() => {}} />);
  return onSubmit;
}

describe("RejectDialog", () => {
  it("사유가_비어_있으면_오류를_보이고_제출하지_않는다", async () => {
    const onSubmit = setup();
    await userEvent.click(screen.getByRole("button", { name: "반려" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("반려 사유를 입력해 주세요.");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("공백_앞뒤를_자른_사유로_한_번_제출한다", async () => {
    const onSubmit = setup();
    await userEvent.type(screen.getByLabelText(/반려 사유/), "  이미지가 흐립니다 ");
    await userEvent.click(screen.getByRole("button", { name: "반려" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith("이미지가 흐립니다");
  });

  it("처리_중에는_반려_버튼이_막힌다", () => {
    render(<RejectDialog open title="t" submitting onSubmit={async () => true} onCancel={() => {}} />);
    expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
  });
});
