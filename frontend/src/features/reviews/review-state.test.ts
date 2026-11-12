import { describe, expect, it } from "vitest";
import { reviewAction, rewardNotice } from "@/features/reviews/review-state";

describe("reviewAction", () => {
  it("작성_가능이면_기한과_함께_write", () => {
    expect(reviewAction({ status: "WRITABLE", writableUntil: "2026-12-09T14:30:00" })).toEqual({ kind: "write", until: "2026-12-09T14:30:00" });
  });
  it("작성_완료는_view", () => {
    expect(reviewAction({ status: "WRITTEN", reviewId: 7 })).toEqual({ kind: "view", reviewId: 7 });
  });
  it("기한_경과", () => {
    expect(reviewAction({ status: "EXPIRED", writableUntil: "2026-12-09T14:30:00" })).toEqual({ kind: "text", text: "후기 작성 기한이 지났습니다" });
  });
  it("삭제함", () => {
    expect(reviewAction({ status: "DELETED" })).toEqual({ kind: "text", text: "삭제한 후기는 다시 작성할 수 없습니다" });
  });
  it("대상_아님_null_undefined는_none", () => {
    expect(reviewAction({ status: "NOT_ELIGIBLE" })).toEqual({ kind: "none" });
    expect(reviewAction(null)).toEqual({ kind: "none" });
    expect(reviewAction(undefined)).toEqual({ kind: "none" });
  });
  it("WRITABLE인데_기한이_없으면_none", () => {
    expect(reviewAction({ status: "WRITABLE" })).toEqual({ kind: "none" });
  });
  it("WRITTEN인데_reviewId가_없으면_none", () => {
    expect(reviewAction({ status: "WRITTEN" })).toEqual({ kind: "none" });
  });
});

describe("rewardNotice", () => {
  it("적립_포인트를_서버_값으로_알린다", () => {
    expect(rewardNotice(500)).toBe("후기를 등록했습니다. 500P가 적립되었습니다.");
    expect(rewardNotice(100)).toBe("후기를 등록했습니다. 100P가 적립되었습니다.");
  });
  it("0이면_적립_문구_없이", () => {
    expect(rewardNotice(0)).toBe("후기를 등록했습니다.");
  });
});
