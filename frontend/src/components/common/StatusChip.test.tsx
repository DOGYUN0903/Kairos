import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusChip } from "@/components/common/StatusChip";

describe("StatusChip", () => {
  it("상품_PENDING_REVIEW는_검수_대기_텍스트", () => {
    render(<StatusChip kind="product" status="PENDING_REVIEW" />);
    expect(screen.getByText("검수 대기")).toHaveClass("chip-pending");
  });

  it("판매자_SUSPENDED는_정지", () => {
    render(<StatusChip kind="seller" status="SUSPENDED" />);
    expect(screen.getByText("정지")).toBeInTheDocument();
  });

  it("모르는_값은_원문을_paused_색으로", () => {
    render(<StatusChip kind="brand" status="WHATEVER" />);
    expect(screen.getByText("WHATEVER")).toHaveClass("chip-paused");
  });
});
