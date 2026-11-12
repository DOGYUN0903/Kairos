import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReturnSteps } from "@/features/returns/components/ReturnSteps";

describe("ReturnSteps", () => {
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
});
