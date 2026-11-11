import { describe, expect, it } from "vitest";
import { afterLoginPath, safeNext } from "@/features/auth/redirect";

describe("로그인 후 이동", () => {
  it("next가_있으면_next로_없으면_역할별_기본", () => {
    expect(afterLoginPath({ role: "USER" }, "/seller")).toBe("/seller");
    expect(afterLoginPath({ role: "ADMIN" }, null)).toBe("/admin");
    expect(afterLoginPath({ role: "USER" }, null)).toBe("/");
  });

  it("외부_주소와_프로토콜_상대_주소는_무시한다", () => {
    expect(safeNext("https://evil.example")).toBeNull();
    expect(safeNext("//evil.example")).toBeNull();
    expect(safeNext("/\\evil.example")).toBeNull();
    expect(safeNext("/login?next=/x")).toBeNull();
  });
});
