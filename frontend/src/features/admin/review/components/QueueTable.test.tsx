import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { toQueueRow } from "@/features/admin/review/rows";
import { QueueTable } from "./QueueTable";

const cr = (id: number, changedFields: ("NAME" | "OPTIONS" | "IMAGES" | "ORIGINAL_PRICE")[]) =>
  toQueueRow({
    tab: "change-requests",
    raw: { changeRequestId: id, productId: 1000 + id, sellerId: 1, name: `상품 ${id}`, originalPrice: 1000, status: "PENDING", requestedAt: "2026-11-07T14:20:00", sellerName: "판매자 A", changedFields },
  });

describe("QueueTable", () => {
  it("변경_요청_행에_변경_항목_배지를_보인다", () => {
    render(<QueueTable tab="change-requests" rows={[cr(1, ["NAME", "OPTIONS", "IMAGES"]), cr(2, [])]} selectedId={1} onSelect={() => {}} />);
    expect(screen.getByRole("columnheader", { name: "변경 항목" })).toBeInTheDocument();
    const [, first, second] = screen.getAllByRole("row");
    expect(within(first!).getByText("상품명")).toBeInTheDocument();
    expect(within(first!).getByText("옵션")).toBeInTheDocument();
    expect(within(first!).getByText("이미지")).toBeInTheDocument();
    expect(first).toHaveAttribute("aria-current", "true");
    expect(within(second!).getByText("-")).toBeInTheDocument();
  });

  it("상품_탭에는_변경_항목_열이_없다", () => {
    render(<QueueTable tab="products" rows={[]} selectedId={null} onSelect={() => {}} />);
    expect(screen.queryByRole("columnheader", { name: "변경 항목" })).toBeNull();
  });
});
