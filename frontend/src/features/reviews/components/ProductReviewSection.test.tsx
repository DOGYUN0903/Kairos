import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProductReviewSection } from "@/features/reviews/components/ProductReviewSection";

const h = vi.hoisted(() => ({ GET: vi.fn(), replace: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET } }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: h.replace }),
  usePathname: () => "/products/9",
  useSearchParams: () => new URLSearchParams(),
}));

const review = {
  reviewId: 1, orderItemId: 1, productId: 9, productName: "니트", rating: 4, content: "두께감이 좋아서 가을에 입기 딱 좋아요", imageUrls: ["https://i/1.png"],
  images: [{ fileId: 1, url: "https://i/1.png" }], writerName: "김*지", replyContent: "감사합니다", repliedAt: "2026-11-09T10:00:00", rewardPoints: 500,
  createdAt: "2026-11-08T10:00:00", modifiedAt: "2026-11-08T10:00:00",
};

function mount(reviewCount: number, averageRating: number) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ProductReviewSection productId={9} reviewCount={reviewCount} averageRating={averageRating} />
    </QueryClientProvider>,
  );
}

describe("ProductReviewSection", () => {
  beforeEach(() => vi.clearAllMocks());

  it("후기가_없으면_빈_문구만_보인다", () => {
    mount(0, 0);
    expect(screen.getByText("아직 후기가 없습니다")).toBeInTheDocument();
    expect(h.GET).toHaveBeenCalledTimes(1); // 훅은 항상 불리지만 화면에는 목록이 없다
  });

  it("서버_평균과_개수를_그대로_보이고_목록을_그린다", async () => {
    h.GET.mockResolvedValue({ data: { data: { content: [review], page: 0, size: 5, totalElements: 1, totalPages: 1 } } });
    mount(128, 4.6);
    expect(screen.getByText("4.6")).toBeInTheDocument();
    expect(screen.getByText("후기 128개")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "평균 별점 5점 만점에 4.6점" })).toBeInTheDocument();
    expect(await screen.findByText("김*지")).toBeInTheDocument();
    expect(screen.getByText("감사합니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "사진 1 크게 보기" })).toBeInTheDocument();
  });

  it("목록을_못_읽으면_영역_안에서만_오류를_보인다", async () => {
    h.GET.mockRejectedValue(new Error("boom"));
    mount(3, 4.0);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("후기 3개")).toBeInTheDocument();
  });
});
