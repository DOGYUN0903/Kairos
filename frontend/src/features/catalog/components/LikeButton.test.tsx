import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { queryKeys } from "@/lib/query-keys";
import { LikeButton } from "./LikeButton";

const put = vi.fn();
const del = vi.fn();
vi.mock("@/api/client", () => ({
  api: { PUT: (...a: unknown[]) => put(...a), DELETE: (...a: unknown[]) => del(...a) },
  unwrap: (r: unknown) => r,
  ApiError: class extends Error {},
}));
let authStatus: "loading" | "anonymous" | "authenticated" = "authenticated";
vi.mock("@/auth/useAuth", () => ({ useAuth: () => ({ status: authStatus, me: null }) }));
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

function setup(liked: boolean | null, likeCount: number | null = 1204, extra: { tone?: "default" | "navy"; label?: string } = {}) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: 0 } } });
  const spy = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <LikeButton productId={7} liked={liked} {...(likeCount !== null ? { likeCount } : {})} {...extra} />
    </QueryClientProvider>,
  );
  return spy;
}

describe("LikeButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authStatus = "authenticated";
    put.mockResolvedValue({});
    del.mockResolvedValue({});
  });

  it("이름은_좋아요이고_개수는_설명으로_읽힌다", () => {
    setup(false);
    const btn = screen.getByRole("button", { name: "좋아요" });
    expect(btn).toHaveAccessibleDescription("좋아요 1,204개");
    expect(btn).toHaveAttribute("aria-pressed", "false");
  });

  it("눌린_상태는_기본이면_hot이고_navy_톤이면_navy다", () => {
    setup(true, null);
    expect(screen.getByRole("button", { name: "좋아요" })).toHaveClass("border-hot", "text-hot");
  });

  it("navy_톤은_hot_색을_쓰지_않고_접근성_이름을_바꿀_수_있다", () => {
    setup(true, null, { tone: "navy", label: "샘플 상품 좋아요 취소" });
    const btn = screen.getByRole("button", { name: "샘플 상품 좋아요 취소" });
    expect(btn).toHaveClass("border-navy", "text-navy");
    expect(btn).not.toHaveClass("border-hot");
    expect(btn).not.toHaveClass("text-hot");
  });

  it("likeCount가_없으면_숫자를_그리지_않는다", () => {
    setup(true, null);
    const btn = screen.getByRole("button", { name: "좋아요" });
    expect(btn).not.toHaveAccessibleDescription();
    expect(btn).not.toHaveTextContent(/\d/);
  });

  it("liked가_null이면_선택되지_않은_상태", () => {
    setup(null);
    expect(screen.getByRole("button", { name: "좋아요" })).toHaveAttribute("aria-pressed", "false");
  });

  it("로그인_상태에서_좋아요하지_않았으면_PUT을_보내고_상세와_목록을_다시_불러온다", async () => {
    const spy = setup(false);
    await userEvent.click(screen.getByRole("button", { name: "좋아요" }));
    await waitFor(() => expect(put).toHaveBeenCalledTimes(1));
    expect(del).not.toHaveBeenCalled();
    expect(put.mock.calls[0]?.[1]).toEqual({ params: { path: { productId: 7 } } });
    await waitFor(() => expect(spy).toHaveBeenCalledTimes(3));
    expect(spy).toHaveBeenCalledWith({ queryKey: queryKeys.likes.root });
  });

  it("이미_좋아요했으면_DELETE를_보낸다", async () => {
    setup(true);
    expect(screen.getByRole("button", { name: "좋아요" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(screen.getByRole("button", { name: "좋아요" }));
    await waitFor(() => expect(del).toHaveBeenCalledTimes(1));
    expect(put).not.toHaveBeenCalled();
  });

  it("요청이_진행_중이면_버튼이_잠기고_연타해도_한_번만_보낸다", async () => {
    let finish: () => void = () => {};
    put.mockReturnValue(new Promise<void>((r) => (finish = r)));
    setup(false);
    const btn = screen.getByRole("button", { name: "좋아요" });
    await userEvent.click(btn);
    await waitFor(() => expect(btn).toBeDisabled());
    await userEvent.click(btn);
    expect(put).toHaveBeenCalledTimes(1);
    finish();
    await waitFor(() => expect(btn).toBeEnabled());
  });

  it("비로그인이면_API_호출_없이_로그인_화면으로_보낸다", async () => {
    authStatus = "anonymous";
    setup(null);
    await userEvent.click(screen.getByRole("button", { name: "좋아요" }));
    expect(put).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith(expect.stringMatching(/^\/login\?next=/));
  });

  it("인증_확인_중에는_아무것도_하지_않는다", async () => {
    authStatus = "loading";
    setup(false);
    await userEvent.click(screen.getByRole("button", { name: "좋아요" }));
    expect(put).not.toHaveBeenCalled();
    expect(push).not.toHaveBeenCalled();
  });
});
