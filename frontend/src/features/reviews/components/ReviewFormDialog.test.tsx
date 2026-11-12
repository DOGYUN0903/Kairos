import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { ReviewFormDialog } from "@/features/reviews/components/ReviewFormDialog";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ writeReview: vi.fn(), editReview: vi.fn(), uploadImage: vi.fn(), toast: vi.fn() }));

vi.mock("@/features/reviews/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/reviews/api")>()),
  writeReview: h.writeReview,
  editReview: h.editReview,
}));
vi.mock("@/features/files/api", () => ({ uploadImage: h.uploadImage }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

const product = { name: "와이드 데님 팬츠", optionDisplayName: "인디고 / 32", thumbnailUrl: null };

function mountCreate() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  const onSubmitted = vi.fn();
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <ReviewFormDialog mode="create" orderId={42} orderItemId={7} productId={9} product={product} onSubmitted={onSubmitted} onClose={onClose} />
    </QueryClientProvider>,
  );
  return { invalidate, onSubmitted, onClose };
}

async function fillValid() {
  await userEvent.click(screen.getByRole("radio", { name: "4점" }));
  await userEvent.type(screen.getByLabelText(/후기 내용/), "생각보다 두께감이 있어서 가을에 입기 좋아요");
}

describe("ReviewFormDialog", () => {
  beforeEach(() => vi.clearAllMocks());

  it("사진을_올리는_동안_제출_버튼이_막힌다", async () => {
    let resolveUpload: (v: { fileId: number; url: string }) => void = () => {};
    h.uploadImage.mockReturnValue(new Promise((r) => (resolveUpload = r)));
    mountCreate();
    await fillValid();
    expect(screen.getByRole("button", { name: "후기 등록" })).toBeEnabled();

    const file = new File(["x"], "a.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText(/사진 추가/), file);

    expect(await screen.findByText("사진을 올리는 중입니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /후기 등록|사진을 올리는 중/ })).toBeDisabled();

    resolveUpload({ fileId: 11, url: "https://img/11.png" });
    await waitFor(() => expect(screen.getByRole("button", { name: "후기 등록" })).toBeEnabled());
    expect(screen.getByRole("button", { name: "사진 1 삭제" })).toBeInTheDocument();
  });

  it("이미_작성한_후기면_창을_닫고_주문_상세를_다시_읽는다", async () => {
    h.writeReview.mockRejectedValue(new ApiError(409, "REVIEW_ALREADY_WRITTEN", "이미 후기를 작성한 항목입니다", null));
    const { invalidate, onSubmitted, onClose } = mountCreate();
    await fillValid();
    await userEvent.click(screen.getByRole("button", { name: "후기 등록" }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(onSubmitted).not.toHaveBeenCalled();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.orders.detail(42) });
    expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "이미 후기를 작성한 항목입니다" }));
  });

  it("성공하면_적립_안내_토스트와_관련_캐시를_무효화한다", async () => {
    h.writeReview.mockResolvedValue({ reviewId: 3, rewardPoints: 100 });
    const { invalidate, onSubmitted } = mountCreate();
    await fillValid();
    await userEvent.click(screen.getByRole("button", { name: "후기 등록" }));

    await waitFor(() => expect(onSubmitted).toHaveBeenCalled());
    expect(h.writeReview).toHaveBeenCalledWith(42, 7, { rating: 4, content: expect.any(String), imageFileIds: [] }, expect.any(String));
    expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "후기를 등록했습니다. 100P가 적립되었습니다." }));
    for (const key of [queryKeys.orders.detail(42), queryKeys.reviews.mineRoot, queryKeys.points.root, queryKeys.reviews.productRoot(9), queryKeys.catalog.product(9)]) {
      expect(invalidate).toHaveBeenCalledWith({ queryKey: key });
    }
  });

  it("검증_오류_REVIEW_INVALID는_폼_상단에_보인다", async () => {
    h.writeReview.mockRejectedValue(new ApiError(400, "REVIEW_INVALID", "후기 내용이 올바르지 않습니다", null));
    const { onClose } = mountCreate();
    await fillValid();
    await userEvent.click(screen.getByRole("button", { name: "후기 등록" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("후기 내용이 올바르지 않습니다");
    expect(onClose).not.toHaveBeenCalled();
  });

  it("별점이_없으면_보내지_않고_안내한다", async () => {
    mountCreate();
    await userEvent.type(screen.getByLabelText(/후기 내용/), "생각보다 두께감이 있어서 가을에 입기 좋아요");
    await userEvent.click(screen.getByRole("button", { name: "후기 등록" }));
    expect(await screen.findByText("별점을 선택해 주세요", { selector: ".field-error" })).toBeInTheDocument();
    expect(h.writeReview).not.toHaveBeenCalled();
  });

  it("수정은_기존_사진의_fileId를_유지해_보낸다", async () => {
    h.editReview.mockResolvedValue(undefined);
    const qc = new QueryClient();
    const onSubmitted = vi.fn();
    render(
      <QueryClientProvider client={qc}>
        <ReviewFormDialog
          mode="edit"
          reviewId={5}
          productId={9}
          product={product}
          initial={{ rating: 3, content: "처음 쓴 후기 내용입니다 열 자 넘음", images: [{ fileId: 21, url: "https://img/21.png" }] }}
          onSubmitted={onSubmitted}
          onClose={vi.fn()}
        />
      </QueryClientProvider>,
    );
    expect(screen.getByText("수정해도 추가 적립은 없습니다")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "수정 완료" }));
    await waitFor(() => expect(onSubmitted).toHaveBeenCalled());
    expect(h.editReview).toHaveBeenCalledWith(5, { rating: 3, content: "처음 쓴 후기 내용입니다 열 자 넘음", imageFileIds: [21] });
    expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "후기를 수정했습니다" }));
  });
});
