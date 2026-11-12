import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ReturnSteps } from "@/features/returns/components/ReturnSteps";

describe("ReturnSteps", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-12T10:00:00+09:00"));
  });
  afterEach(() => vi.useRealTimers());

  it("완료는_네_단계와_일시를_그린다", () => {
    render(
      <ReturnSteps
        r={{ status: "COMPLETED", requestedAt: "2026-11-09T14:22:00", decidedAt: "2026-11-09T16:05:00", decidedBy: "ADMIN", receivedAt: "2026-11-11T10:40:00", completedAt: "2026-11-11T10:41:00" }}
      />,
    );
    expect(screen.getAllByRole("listitem")).toHaveLength(4);
    expect(screen.getByText("관리자")).toBeInTheDocument();
    expect(screen.getByText("11월 9일 14:22")).toBeInTheDocument();
  });

  it("거절은_그_자리에서_끝나고_안내_문구를_보인다", () => {
    render(<ReturnSteps r={{ status: "REJECTED", requestedAt: "2026-11-08T11:02:00", decidedAt: "2026-11-09T09:30:00", decidedBy: "SELLER" }} />);
    expect(screen.getByText("판매자가 반품을 거절했습니다")).toBeInTheDocument();
    expect(screen.getByText("거절")).toBeInTheDocument();
  });

  it("요청됨은_승인_칸에_판매자가_확인_중", () => {
    render(<ReturnSteps r={{ status: "REQUESTED", requestedAt: "2026-11-12T14:22:00" }} />);
    expect(screen.getByText("판매자가 확인 중")).toBeInTheDocument();
  });

  it("자동_승인은_승인_주체로_보인다", () => {
    render(<ReturnSteps r={{ status: "APPROVED", requestedAt: "2026-11-10T14:22:00", decidedAt: "2026-11-13T14:22:00", decidedBy: "SYSTEM" }} />);
    expect(screen.getByText("자동 승인")).toBeInTheDocument();
  });

  it("관리자가_중단한_반품은_중단_단계에_관리자를_보인다", () => {
    render(<ReturnSteps r={{ status: "CANCELED_BY_ADMIN", requestedAt: "2026-11-09T14:22:00", decidedAt: "2026-11-10T09:00:00", decidedBy: "SELLER" }} />);
    expect(screen.getByText("관리자")).toBeInTheDocument();
    expect(screen.getByText("관리자").closest("li")).toHaveTextContent("중단");
  });

  it("철회는_중단_단계에_구매자를_보인다", () => {
    render(<ReturnSteps r={{ status: "WITHDRAWN", requestedAt: "2026-11-09T14:22:00" }} />);
    expect(screen.getAllByText("구매자").length).toBeGreaterThan(0);
  });

  it("거절은_결정한_주체를_보인다", () => {
    render(<ReturnSteps r={{ status: "REJECTED", requestedAt: "2026-11-08T11:02:00", decidedAt: "2026-11-09T09:30:00", decidedBy: "SELLER" }} />);
    expect(screen.getByText("판매자")).toBeInTheDocument();
  });

  it("단계마다_상태_단어를_스크린리더용으로_둔다", () => {
    render(<ReturnSteps r={{ status: "APPROVED", requestedAt: "2026-11-10T14:22:00", decidedAt: "2026-11-11T14:22:00", decidedBy: "SELLER" }} />);
    expect(screen.getAllByText(/\(완료\)/)).toHaveLength(2);
    expect(screen.getByText(/\(진행 중\)/)).toBeInTheDocument();
    expect(screen.getAllByText(/\(예정\)/)).toHaveLength(1);
  });

  it("올해가_아닌_일시에는_연도를_붙인다", () => {
    render(<ReturnSteps r={{ status: "REQUESTED", requestedAt: "2025-12-30T09:00:00" }} />);
    expect(screen.getByText("2025년 12월 30일 09:00")).toBeInTheDocument();
  });
});
