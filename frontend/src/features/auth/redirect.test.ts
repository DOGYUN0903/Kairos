import { describe, expect, it } from "vitest";
import { afterLoginPath, isAuthPage, safeNext } from "@/features/auth/redirect";

const O = "http://localhost:3000";

describe("로그인 후 이동", () => {
  it("next가_있으면_next로_없으면_역할별_기본", () => {
    expect(afterLoginPath({ role: "USER" }, "/seller")).toBe("/seller");
    expect(afterLoginPath({ role: "ADMIN" }, null)).toBe("/admin");
    expect(afterLoginPath({ role: "USER" }, "//evil.com")).toBe("/");
  });

  it("외부로_나가는_next는_전부_거부한다", () => {
    for (const bad of [
      decodeURIComponent("/%09/evil.com"),
      "/\t/evil.com",
      "/\n/evil.com",
      "/\\/evil.com",
      "//evil.com",
      "https://evil.com",
      "javascript:alert(1)",
      "/\\evil.com",
      "/ /evil.com",
    ]) {
      expect(safeNext(bad, O), JSON.stringify(bad)).toBeNull();
    }
  });

  it("같은_origin_경로는_쿼리와_해시까지_유지한다", () => {
    expect(safeNext("/seller?tab=a#x", O)).toBe("/seller?tab=a#x");
  });

  it("인증_화면은_정확한_경로로만_판정한다", () => {
    expect(safeNext("/login?next=/x", O)).toBeNull();
    expect(safeNext("/login/", O)).toBeNull();
    expect(safeNext("/login-help", O)).toBe("/login-help");
    expect(isAuthPage("/signup/")).toBe(true);
    expect(isAuthPage("/signup-x")).toBe(false);
  });
});
