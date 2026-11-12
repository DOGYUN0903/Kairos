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

  it("F3_쿠폰·정책·답글_상태", () => {
    const cases: [Parameters<typeof StatusChip>[0]["kind"], string, string][] = [
      ["coupon", "AVAILABLE", "사용 가능"],
      ["coupon", "USED", "사용 완료"],
      ["coupon", "EXPIRED", "기간 만료"],
      ["couponPolicy", "ACTIVE", "진행 중"],
      ["couponPolicy", "ENDED", "종료"],
      ["couponPolicy", "PERIOD_OVER", "기간 종료"],
      ["reply", "UNANSWERED", "미답변"],
    ];
    for (const [kind, status, label] of cases) {
      const { unmount } = render(<StatusChip kind={kind} status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });
});
