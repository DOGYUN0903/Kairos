import { describe, expect, it } from "vitest";
import { menuMetaLabels } from "@/features/mypage/menu-meta";

describe("menuMetaLabels", () => {
  it("세_값을_메뉴_href별_문구로_만든다", () => {
    expect(menuMetaLabels({ coupons: 5, points: 20000, likes: 8 })).toEqual({
      "/my/coupons": "5장",
      "/my/points": "20,000P",
      "/my/likes": "8",
    });
  });
  it("값이_없는_항목은_빠진다", () => {
    expect(menuMetaLabels({ points: 100 })).toEqual({ "/my/points": "100P" });
    expect(menuMetaLabels({})).toEqual({});
  });
  it("0도_그대로_보인다", () => {
    expect(menuMetaLabels({ coupons: 0, points: 0, likes: 0 })).toEqual({
      "/my/coupons": "0장",
      "/my/points": "0P",
      "/my/likes": "0",
    });
  });
});
