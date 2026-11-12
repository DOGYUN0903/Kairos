import { describe, expect, it } from "vitest";
import { canAddImage } from "@/features/reviews/image-rules";

describe("canAddImage", () => {
  it("3장_미만이면_더_올릴_수_있다", () => {
    expect(canAddImage(0)).toBe(true);
    expect(canAddImage(2)).toBe(true);
  });
  it("3장이면_더_올릴_수_없다", () => {
    expect(canAddImage(3)).toBe(false);
  });
});
