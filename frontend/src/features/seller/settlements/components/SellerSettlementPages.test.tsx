import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SellerSettlementDetailPage } from "@/features/seller/settlements/components/SellerSettlementDetailPage";
import { SellerSettlementListPage } from "@/features/seller/settlements/components/SellerSettlementListPage";

const h = vi.hoisted(() => ({ GET: vi.fn() }));
vi.mock("@/api/client", async (o) => ({ ...(await o<typeof import("@/api/client")>()), api: { GET: h.GET } }));
vi.mock("next/navigation", () => ({ notFound: vi.fn(), useRouter: () => ({ push: vi.fn() }), useSearchParams: () => new URLSearchParams() }));

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>);
const summary = { settlementId: 7, sellerId: 1, sellerName: "A", sellerStatus: "APPROVED", periodStart: "2026-10-12", periodEnd: "2026-10-19", payoutDate: "2026-10-21", status: "PAID", totalAmount: -3000, payableAmount: 0, lineCount: 2 };

describe("판매자 정산 화면", () => {
  beforeEach(() => vi.clearAllMocks());

  it("목록은_기간_마지막_날과_음수_합계_지급액_0원", async () => {
    h.GET.mockResolvedValue({ data: { data: { content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 } } });
    wrap(<SellerSettlementListPage />);
    expect(await screen.findByRole("link", { name: "2026-10-12 ~ 2026-10-18" })).toHaveAttribute("href", "/seller/settlements/7");
    expect(screen.getByText("−3,000원")).toBeInTheDocument();
    expect(screen.getByText("0원")).toBeInTheDocument();
    expect(screen.getByText("2건")).toBeInTheDocument();
  });

  it("목록이_비면_안내", async () => {
    h.GET.mockResolvedValue({ data: { data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } } });
    wrap(<SellerSettlementListPage />);
    expect(await screen.findByText("아직 정산이 없습니다. 정산은 매주 월요일에 지난주 구매 확정 주문으로 만들어집니다")).toBeInTheDocument();
  });

  it("상세는_라인_0개_조정만_있는_정산과_지급_메모", async () => {
    h.GET.mockResolvedValue({
      data: { data: { ...summary, salesAmount: 0, sellerCouponDiscount: 0, commissionAmount: 0, shippingFeeOwed: 0, returnFeeBorne: 0, adjustmentAmount: -3000, paidAt: "2026-10-21T10:00:00", paidMemo: "이체 완료", lines: [], adjustments: [{ adjustmentId: 1, sellerId: 1, type: "CARRY_OVER", amount: -3000, reason: "이월" }] } },
    });
    wrap(<SellerSettlementDetailPage settlementId={7} />);
    expect(await screen.findByText("포함된 주문이 없습니다")).toBeInTheDocument();
    expect(screen.getByText(/다음 정산으로 넘어갑니다/)).toBeInTheDocument();
    expect(screen.getByText("이체 완료")).toBeInTheDocument();
    expect(screen.getByText("플랫폼이 부담한 쿠폰·포인트·무료배송은 정산에서 차감되지 않습니다")).toBeInTheDocument();
  });
});
