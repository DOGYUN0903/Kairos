import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { OrderItemResult } from "@/features/orders/api";
import { OrderItemReviewAction, OrderItemReviewNote } from "@/features/reviews/components/OrderItemReviewAction";

const base = { orderItemId: 7, productId: 9, productName: "니트", optionDisplayName: "M", thumbnailUrl: null } as OrderItemResult;
const item = (review: OrderItemResult["review"]) => ({ ...base, review }) as OrderItemResult;

function mount(i: OrderItemResult) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <OrderItemReviewNote item={i} />
      <OrderItemReviewAction orderId={1} item={i} />
    </QueryClientProvider>,
  );
}

describe("주문 항목의 후기 상태", () => {
  it("작성_가능이면_버튼과_기한", () => {
    mount(item({ status: "WRITABLE", writableUntil: "2026-12-09T14:30:00" }));
    expect(screen.getByRole("button", { name: "후기 작성" })).toBeInTheDocument();
    expect(screen.getByText("12월 9일 14:30")).toBeInTheDocument();
    expect(screen.getByText(/까지 작성 가능/)).toBeInTheDocument();
  });
  it("작성했으면_내_후기_보기_링크", () => {
    mount(item({ status: "WRITTEN", reviewId: 3 }));
    expect(screen.getByRole("link", { name: "내 후기 보기" })).toHaveAttribute("href", "/my/reviews");
  });
  it("기한_경과와_삭제는_문구만", () => {
    mount(item({ status: "EXPIRED", writableUntil: "2026-12-09T14:30:00" }));
    expect(screen.getByText("후기 작성 기한이 지났습니다")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });
  it("대상이_아니면_아무것도_없다", () => {
    mount(item({ status: "NOT_ELIGIBLE" }));
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.queryByRole("link")).toBeNull();
  });
});
