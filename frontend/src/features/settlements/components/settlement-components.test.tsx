import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SettlementAdjustmentsTable } from "@/features/settlements/components/SettlementAdjustmentsTable";
import { SettlementBreakdown } from "@/features/settlements/components/SettlementBreakdown";
import { SettlementLinesTable } from "@/features/settlements/components/SettlementLinesTable";

const base = { salesAmount: 95000, sellerCouponDiscount: 5000, commissionAmount: 9000, shippingFeeOwed: 6000, returnFeeBorne: 6000, adjustmentAmount: 0, totalAmount: 81000, payableAmount: 81000 };
const line = (o: Record<string, unknown> = {}) =>
  ({ lineId: 1, sellerOrderId: 11, orderId: 1, orderNo: "K1", confirmedAt: "2026-11-04T10:00:00", salesAmount: 80000, sellerCouponDiscount: 5000, commissionBase: 75000, commissionRateBp: 1000, commissionAmount: 7500, shippingFeeOwed: 3000, returnFeeBorne: 0, lineAmount: 70500, ...o }) as never;

describe("SettlementBreakdown", () => {
  it("여덟_줄이_고정_순서로_0_포함_서버_값_그대로", () => {
    render(<SettlementBreakdown detail={base as never} />);
    const items = screen.getAllByRole("listitem").map((li) => li.textContent);
    expect(items).toEqual(["상품 매출95,000원", "−판매자 쿠폰 할인−5,000원", "−수수료−9,000원", "+배송비+6,000원", "−판매자 부담 반품비−6,000원", "±조정0원", "=합계81,000원", "지급액81,000원"]);
  });
  it("음수_합계는_이월_안내", () => {
    render(<SettlementBreakdown detail={{ ...base, totalAmount: -3000, payableAmount: 0 } as never} />);
    expect(screen.getByText("합계가 0원보다 적어 이번 지급액은 0원입니다. 부족한 금액은 다음 정산으로 넘어갑니다")).toBeInTheDocument();
    expect(screen.getByText("−3,000원")).toBeInTheDocument();
  });
  it("음수_합계에서_지급액은_0원이고_조정_기호는_±_유지", () => {
    render(<SettlementBreakdown detail={{ ...base, adjustmentAmount: -10000, totalAmount: -3000, payableAmount: 0 } as never} />);
    const items = screen.getAllByRole("listitem");
    expect(items[7]!.textContent).toBe("지급액0원");
    expect(items[5]!.textContent).toBe("±조정−10,000원");
  });
  it("기호는_aria-hidden", () => {
    const { container } = render(<SettlementBreakdown detail={base as never} />);
    expect(container.querySelectorAll("span.w-5:not([aria-hidden=true])")).toHaveLength(0);
  });
  it("정상_합계에는_안내가_없다", () => {
    render(<SettlementBreakdown detail={base as never} />);
    expect(screen.queryByText(/다음 정산으로 넘어갑니다/)).toBeNull();
  });
});

describe("SettlementLinesTable", () => {
  it("음수_라인과_줄마다_다른_수수료율", () => {
    render(<SettlementLinesTable lines={[line(), line({ lineId: 2, orderNo: "K2", commissionRateBp: 800, lineAmount: -3000 })]} orderHref={(l) => `/seller/orders/${l.sellerOrderId}`} />);
    const rows = screen.getAllByRole("row");
    expect(within(rows[1]!).getByText("10%")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("8%")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("−3,000원", { selector: "td:last-child" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "K1" })).toHaveAttribute("href", "/seller/orders/11");
  });
  it("0원_줄은_0원이고_-0원이_아니다", () => {
    render(<SettlementLinesTable lines={[line({ salesAmount: 0, sellerCouponDiscount: 0, commissionBase: 0, commissionAmount: 0, shippingFeeOwed: 0, returnFeeBorne: 0, lineAmount: 0 })]} />);
    const cells = within(screen.getAllByRole("row")[1]!).getAllByRole("cell");
    expect(cells.slice(2).map((c) => c.textContent)).toEqual(["0원", "0원", "0원", "10%", "0원", "0원", "0원", "0원"]);
  });
  it("건수는_lineCount_우선", () => {
    render(<SettlementLinesTable lines={[line()]} lineCount={5} />);
    expect(screen.getByText("구매 확정 주문 5건")).toBeInTheDocument();
  });
  it("링크가_없으면_글자만", () => {
    render(<SettlementLinesTable lines={[line()]} />);
    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.getByText("K1")).toBeInTheDocument();
  });
  it("라인_0개", () => {
    render(<SettlementLinesTable lines={[]} />);
    expect(screen.getByText("포함된 주문이 없습니다")).toBeInTheDocument();
  });
});

describe("SettlementAdjustmentsTable", () => {
  it("종류_금액_사유", () => {
    render(<SettlementAdjustmentsTable adjustments={[{ adjustmentId: 1, sellerId: 1, type: "MANUAL", amount: -10000, reason: "파손 보상" }, { adjustmentId: 2, sellerId: 1, type: "CARRY_OVER", amount: -3000, reason: "이월" }] as never} />);
    expect(screen.getByText("수동 조정")).toBeInTheDocument();
    expect(screen.getByText("−10,000원")).toBeInTheDocument();
    expect(screen.getByText("파손 보상")).toBeInTheDocument();
    expect(screen.getAllByText("이월").length).toBeGreaterThan(0);
  });
  it("비면_안내", () => {
    render(<SettlementAdjustmentsTable adjustments={[]} />);
    expect(screen.getByText("조정이 없습니다")).toBeInTheDocument();
  });
});
