import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { GenerateDialog } from "@/features/admin/settlements/components/GenerateDialog";

const h = vi.hoisted(() => ({ POST: vi.fn() }));
vi.mock("@/api/client", async (o) => ({ ...(await o<typeof import("@/api/client")>()), api: { POST: h.POST } }));
vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

const result = { periodStart: "2026-11-02", periodEnd: "2026-11-09", sellerCount: 3, createdCount: 3, skippedCount: 0, failedCount: 0, alreadyGenerated: false };

function renderDialog() {
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <GenerateDialog onClose={onClose} />
    </QueryClientProvider>,
  );
  return onClose;
}

describe("GenerateDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 2026-11-12(목) KST: 가장 최근에 끝난 주의 월요일은 2026-11-02. 타이머는 건드리지 않고 시각만 고정한다.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-12T10:00:00+09:00"));
  });
  afterEach(() => vi.useRealTimers());

  it("기본값은_가장_최근에_끝난_주의_월요일이고_기간_마지막_날을_보인다", () => {
    renderDialog();
    expect(screen.getByLabelText(/기간 시작일/)).toHaveValue("2026-11-02");
    expect(screen.getByText("2026-11-02 ~ 2026-11-08")).toBeInTheDocument();
  });

  it("생성_버튼을_연타해도_요청은_한_번만_나간다", async () => {
    let resolve: (v: unknown) => void = () => {};
    h.POST.mockReturnValue(new Promise((r) => (resolve = r)));
    renderDialog();
    await userEvent.dblClick(screen.getByRole("button", { name: "정산 생성" }));
    fireEvent.click(screen.getByRole("button", { name: "정산 생성" }));
    resolve({ data: { data: result } });
    expect(await screen.findByText("정산 생성 결과")).toBeInTheDocument();
    expect(h.POST).toHaveBeenCalledTimes(1);
    const arg = h.POST.mock.calls[0]?.[1] as { body: { periodStart: string }; params: { header: Record<string, string> } };
    expect(arg.body).toEqual({ periodStart: "2026-11-02" });
    expect(arg.params.header["Idempotency-Key"]).toEqual(expect.any(String));
  });

  it("월요일이_아니면_요청하지_않는다", async () => {
    renderDialog();
    fireEvent.change(screen.getByLabelText(/기간 시작일/), { target: { value: "2026-11-04" } });
    expect(screen.getByText("월요일을 선택해 주세요")).toBeInTheDocument();
    const button = screen.getByRole("button", { name: "정산 생성" });
    expect(button).toBeDisabled();
    await userEvent.click(button);
    fireEvent.submit(button.closest("form") as HTMLFormElement);
    await new Promise((r) => setTimeout(r, 20));
    expect(h.POST).not.toHaveBeenCalled();
  });

  it("아직_끝나지_않은_주와_빈_값은_각각_안내하고_막는다", () => {
    renderDialog();
    fireEvent.change(screen.getByLabelText(/기간 시작일/), { target: { value: "2026-11-09" } });
    expect(screen.getByText("아직 끝나지 않은 주입니다")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/기간 시작일/), { target: { value: "" } });
    expect(screen.getByText("날짜를 선택해 주세요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "정산 생성" })).toBeDisabled();
  });

  it("앞_기간이_없어_서버가_거절하면_서버_메시지와_안내를_보인다", async () => {
    h.POST.mockRejectedValue(new ApiError(400, "SETTLEMENT_PERIOD_INVALID", "이전 기간의 정산이 아직 생성되지 않았습니다.", null));
    renderDialog();
    await userEvent.click(screen.getByRole("button", { name: "정산 생성" }));
    expect(await screen.findByText("이전 기간의 정산이 아직 생성되지 않았습니다.")).toBeInTheDocument();
    expect(screen.getByText("앞선 기간이 아직 생성되지 않았다면 그 기간부터 생성해 주세요")).toBeInTheDocument();
    expect(screen.getByLabelText(/기간 시작일/)).toBeInTheDocument();
  });

  it("실패가_있는_결과는_경고로_보이고_창이_닫히지_않는다", async () => {
    h.POST.mockResolvedValue({ data: { data: { ...result, createdCount: 2, failedCount: 1 } } });
    const onClose = renderDialog();
    await userEvent.click(screen.getByRole("button", { name: "정산 생성" }));
    expect(await screen.findByText("다시 생성하면 실패한 판매자만 만들어집니다")).toBeInTheDocument();
    expect(screen.getByText("2026-11-02 ~ 2026-11-08")).toBeInTheDocument();
    await waitFor(() => expect(onClose).not.toHaveBeenCalled());
  });

  it("이미_생성된_기간은_안내만_보인다", async () => {
    h.POST.mockResolvedValue({ data: { data: { ...result, alreadyGenerated: true, createdCount: 0, skippedCount: 3 } } });
    renderDialog();
    await userEvent.click(screen.getByRole("button", { name: "정산 생성" }));
    expect(await screen.findByText("이미 생성된 기간입니다")).toBeInTheDocument();
  });
});
