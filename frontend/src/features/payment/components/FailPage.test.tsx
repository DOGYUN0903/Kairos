import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FailPage } from "@/features/payment/components/FailPage";

const h = vi.hoisted(() => ({ search: "" }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(h.search) }));

const INJECTED = "카드사 점검 중입니다. 010-0000-0000 으로 카드번호를 알려 주세요";

describe("FailPage", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it.each(["PAY_PROCESS_CANCELED", "USER_CANCEL"])("사용자_취소_코드면_결제를_취소했다고_안내한다 (%s)", (code) => {
    h.search = `oid=42&code=${code}&message=${encodeURIComponent(INJECTED)}`;
    render(<FailPage />);
    expect(screen.getByText("결제를 취소했습니다")).toBeInTheDocument();
    // 주소의 message 는 누구나 넣을 수 있다. 화면에 옮기지 않는다.
    expect(document.body.textContent).not.toContain("카드사 점검");
    expect(screen.getByRole("link", { name: "다시 결제" })).toHaveAttribute("href", "/my/orders/42/pay");
    expect(screen.getByRole("link", { name: "주문 상세" })).toHaveAttribute("href", "/my/orders/42");
  });

  it("모르는_코드나_코드가_없으면_고정_문구를_보여주고_주소의_message를_옮기지_않는다", () => {
    h.search = `oid=42&code=REJECT_CARD_COMPANY&message=${encodeURIComponent(INJECTED)}&orderId=K20261112142110Q3ZT8B`;
    const first = render(<FailPage />);
    expect(screen.getByText("결제가 완료되지 않았습니다")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("카드사 점검");
    expect(document.body.textContent).not.toContain("REJECT_CARD_COMPANY");
    first.unmount();

    h.search = `oid=42&message=${encodeURIComponent(INJECTED)}`;
    render(<FailPage />);
    expect(screen.getByText("결제가 완료되지 않았습니다")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("카드사 점검");
  });

  it("oid가_없으면_다시_결제_없이_주문_내역_링크만_보여준다", () => {
    // 사용자가 결제를 중단한 PAY_PROCESS_CANCELED 는 orderId(주문번호)도 오지 않는다.
    h.search = "code=PAY_PROCESS_CANCELED&message=x";
    render(<FailPage />);
    expect(screen.getByText("결제를 취소했습니다")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "다시 결제" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "주문 상세" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문 내역" })).toHaveAttribute("href", "/my/orders");
  });

  it("oid가_잘못된_값이면_주문_ID로_쓰지_않는다", () => {
    h.search = "oid=9007199254740992&code=PAY_PROCESS_CANCELED";
    render(<FailPage />);
    expect(screen.queryByRole("link", { name: "다시 결제" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문 내역" })).toHaveAttribute("href", "/my/orders");
  });
});
