import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SellerDetail } from "@/features/seller/apply/api";
import { QuickActions } from "@/features/seller/dashboard/components/QuickActions";
import { SellerGate } from "./SellerGate";

type MeState = { status: "loading" | "none" | SellerDetail["status"]; seller: SellerDetail | null; error: unknown };
let me: MeState;
vi.mock("@/features/seller/gate/useSellerMe", () => ({
  useSellerMe: () => ({ ...me, refetch: vi.fn() }),
}));
let pathname = "/seller";
vi.mock("next/navigation", () => ({
  usePathname: () => pathname,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));
vi.mock("@/auth/useAuth", () => ({ useAuth: () => ({ me: { name: "홍길동" }, logout: vi.fn() }) }));
vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

const seller = (over: Partial<SellerDetail>): SellerDetail => ({
  createdAt: "2026-11-08T10:00:00",
  sellerId: 1,
  memberId: 1,
  companyName: "판매자 A 주식회사",
  businessNumber: "123-45-67890",
  representativeName: "박서준",
  phone: "02-1234-5678",
  bank: "국민은행",
  accountNumber: "123456-01-234567",
  holder: "판매자 A 주식회사",
  commissionRateBp: 0,
  status: "PENDING",
  ...over,
});

function renderGate() {
  const qc = new QueryClient();
  return render(
    <QueryClientProvider client={qc}>
      <SellerGate>
        <p>자식 화면</p>
      </SellerGate>
    </QueryClientProvider>,
  );
}

describe("SellerGate", () => {
  beforeEach(() => {
    pathname = "/seller";
  });

  it("404면_입점_안내와_신청_링크", () => {
    me = { status: "none", seller: null, error: null };
    renderGate();
    expect(screen.getByRole("link", { name: "입점 신청" })).toHaveAttribute("href", "/seller/apply");
    expect(screen.queryByText("자식 화면")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "상품 관리" })).not.toBeInTheDocument();
  });

  it("PENDING이면_심사_중_안내와_읽기_전용", () => {
    me = { status: "PENDING", seller: seller({}), error: null };
    renderGate();
    expect(screen.getByText(/심사 중입니다/)).toBeInTheDocument();
    expect(screen.getByText("123-45-67890")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "수정" })).toHaveAttribute("href", "/seller/apply");
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.queryByText("자식 화면")).not.toBeInTheDocument();
  });

  it("REJECTED면_사유와_수정_폼", () => {
    me = { status: "REJECTED", seller: seller({ status: "REJECTED", rejectReason: "서류가 일치하지 않습니다" }), error: null };
    renderGate();
    expect(screen.getByRole("alert")).toHaveTextContent("서류가 일치하지 않습니다");
    expect(screen.getByLabelText("회사명")).toHaveValue("판매자 A 주식회사");
    expect(screen.getByRole("button", { name: "다시 신청" })).toBeInTheDocument();
  });

  it("SUSPENDED면_정지_배너와_children", () => {
    me = { status: "SUSPENDED", seller: seller({ status: "SUSPENDED", suspendReason: "위조 상품 의심" }), error: null };
    renderGate();
    expect(screen.getByRole("alert")).toHaveTextContent("판매자 정지 중: 위조 상품 의심");
    expect(screen.getByText("자식 화면")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "상품 관리" })).toBeInTheDocument();
  });

  it("APPROVED면_배너_없이_children", () => {
    me = { status: "APPROVED", seller: seller({ status: "APPROVED" }), error: null };
    renderGate();
    expect(screen.getByText("자식 화면")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("미신청이어도_apply_경로에서는_children을_보여준다", () => {
    pathname = "/seller/apply";
    me = { status: "none", seller: null, error: null };
    renderGate();
    expect(screen.getByText("자식 화면")).toBeInTheDocument();
  });

  it("SUSPENDED면_등록_버튼이_disabled이고_툴팁이_정지_중", () => {
    me = { status: "SUSPENDED", seller: seller({ status: "SUSPENDED" }), error: null };
    render(<QuickActions />);
    const register = screen.getByText("상품 등록");
    const brand = screen.getByText("브랜드 신청");
    for (const el of [register, brand]) {
      expect(el).toHaveAttribute("aria-disabled", "true");
      expect(el).toHaveAttribute("title", "정지 중");
      expect(el.closest("a")).toBeNull();
    }
  });

  it("APPROVED면_등록_버튼은_링크", () => {
    me = { status: "APPROVED", seller: seller({ status: "APPROVED" }), error: null };
    render(<QuickActions />);
    expect(screen.getByRole("link", { name: "상품 등록" })).toHaveAttribute("href", "/seller/products/new");
  });
});
