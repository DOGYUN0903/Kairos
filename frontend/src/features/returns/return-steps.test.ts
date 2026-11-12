import { describe, expect, it } from "vitest";
import { returnEndNote, returnSteps } from "@/features/returns/return-steps";

const times = { requestedAt: "2026-11-10T10:00:00", decidedAt: "2026-11-11T10:00:00", receivedAt: "2026-11-12T10:00:00", completedAt: "2026-11-13T10:00:00" };
const states = (r: Parameters<typeof returnSteps>[0]) => returnSteps(r).map((s) => s.state);

describe("returnSteps", () => {
  it("열_단계_키_순서", () => {
    expect(returnSteps({ status: "REQUESTED", requestedAt: times.requestedAt }).map((s) => s.key)).toEqual(["requested", "approved", "received", "completed"]);
  });
  it("REQUESTED", () => {
    const s = returnSteps({ status: "REQUESTED", requestedAt: times.requestedAt });
    expect(s.map((x) => x.state)).toEqual(["done", "current", "todo", "todo"]);
    expect(s[0]!.at).toBe(times.requestedAt);
    expect(s[1]!.at).toBeUndefined();
  });
  it("APPROVED", () => {
    expect(states({ status: "APPROVED", requestedAt: times.requestedAt, decidedAt: times.decidedAt })).toEqual(["done", "done", "current", "todo"]);
  });
  it("REFUNDING", () => {
    expect(states({ status: "REFUNDING", ...times })).toEqual(["done", "done", "done", "current"]);
  });
  it("COMPLETED", () => {
    const s = returnSteps({ status: "COMPLETED", ...times });
    expect(s.map((x) => x.state)).toEqual(["done", "done", "done", "done"]);
    expect(s.map((x) => x.at)).toEqual([times.requestedAt, times.decidedAt, times.receivedAt, times.completedAt]);
  });
  it("REJECTED는_승인_칸이_멈춘다", () => {
    expect(states({ status: "REJECTED", requestedAt: times.requestedAt, decidedAt: times.decidedAt })).toEqual(["done", "stopped", "todo", "todo"]);
  });
  it("WITHDRAWN은_승인_칸이_멈춘다", () => {
    expect(states({ status: "WITHDRAWN", requestedAt: times.requestedAt })).toEqual(["done", "stopped", "todo", "todo"]);
  });
  it("CANCELED_BY_ADMIN은_진행된_다음_칸이_멈춘다", () => {
    expect(states({ status: "CANCELED_BY_ADMIN", requestedAt: times.requestedAt })).toEqual(["done", "stopped", "todo", "todo"]);
    expect(states({ status: "CANCELED_BY_ADMIN", requestedAt: times.requestedAt, decidedAt: times.decidedAt })).toEqual(["done", "done", "stopped", "todo"]);
    expect(states({ status: "CANCELED_BY_ADMIN", ...times, completedAt: undefined })).toEqual(["done", "done", "done", "stopped"]);
  });
});

describe("returnEndNote", () => {
  it("멈춘_상태만_문구", () => {
    expect(returnEndNote("REJECTED")).toBe("판매자가 반품을 거절했습니다");
    expect(returnEndNote("WITHDRAWN")).toBe("반품 요청을 철회했습니다");
    expect(returnEndNote("CANCELED_BY_ADMIN")).toBe("관리자가 반품을 중단했습니다");
    expect(returnEndNote("REQUESTED")).toBeNull();
    expect(returnEndNote("COMPLETED")).toBeNull();
  });
});
