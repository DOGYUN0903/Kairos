import { describe, expect, it } from "vitest";
import { RETURN_TABS, parseReturnTab } from "./tabs";

const sp = (s: string) => new URLSearchParams(s);

describe("RETURN_TABS", () => {
  it("전체_요청_승인_환불확인중_완료_거절_순서다", () => {
    expect(RETURN_TABS.map((t) => [t.status, t.label])).toEqual([
      [undefined, "전체"],
      ["REQUESTED", "요청"],
      ["APPROVED", "승인 · 수거 대기"],
      ["REFUNDING", "환불 확인 중"],
      ["COMPLETED", "완료"],
      ["REJECTED", "거절"],
    ]);
  });
});

describe("parseReturnTab", () => {
  it("없으면_전체다", () => {
    expect(parseReturnTab(sp(""))).toBeUndefined();
  });

  it("알려진_상태를_읽는다", () => {
    expect(parseReturnTab(sp("status=REFUNDING"))).toBe("REFUNDING");
  });

  it("모르는_값은_전체다", () => {
    expect(parseReturnTab(sp("status=foo"))).toBeUndefined();
    expect(parseReturnTab(sp("status=WITHDRAWN"))).toBeUndefined();
    expect(parseReturnTab(sp("status=requested"))).toBeUndefined();
  });
});
