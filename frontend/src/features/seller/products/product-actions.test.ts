import { describe, expect, it } from "vitest";
import { contentEditKind, statusActions } from "@/features/seller/products/product-actions";

describe("statusActions", () => {
  it("판매_중이면_일시_중지와_종료", () => {
    expect(statusActions("ON_SALE", true)).toEqual({ pause: true, resume: false, end: true });
  });
  it("일시_중지면_재개와_종료", () => {
    expect(statusActions("PAUSED", true)).toEqual({ pause: false, resume: true, end: true });
  });
  it("정지된_판매자는_재개할_수_없지만_중지와_종료는_가능", () => {
    expect(statusActions("PAUSED", false)).toEqual({ pause: false, resume: false, end: true });
    expect(statusActions("ON_SALE", false)).toEqual({ pause: true, resume: false, end: true });
  });
  it("검수_대기_반려_종료는_아무_작업도_없다", () => {
    for (const s of ["PENDING_REVIEW", "REJECTED", "ENDED"] as const) {
      expect(statusActions(s, true)).toEqual({ pause: false, resume: false, end: false });
    }
  });
});

describe("contentEditKind", () => {
  it("판매_중_일시_중지는_변경_요청", () => {
    expect(contentEditKind("ON_SALE")).toBe("change-request");
    expect(contentEditKind("PAUSED")).toBe("change-request");
  });
  it("검수_대기_반려는_직접_수정", () => {
    expect(contentEditKind("PENDING_REVIEW")).toBe("edit");
    expect(contentEditKind("REJECTED")).toBe("edit");
  });
  it("종료는_수정_불가", () => {
    expect(contentEditKind("ENDED")).toBeNull();
  });
});
