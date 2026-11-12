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

  it("주문_항목_RETURN_REFUNDING은_반품_환불_중", () => {
    render(<StatusChip kind="orderItem" status="RETURN_REFUNDING" />);
    expect(screen.getByText("반품 환불 중")).toBeInTheDocument();
  });

  it("주문_계열_kind의_모르는_값은_원문", () => {
    render(<StatusChip kind="payment" status="WHATEVER" />);
    expect(screen.getByText("WHATEVER")).toHaveClass("chip-paused");
  });
});
