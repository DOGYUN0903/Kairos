import { describe, expect, it } from "vitest";
import { commissionSchema, rejectReasonSchema } from "./reject-reason";

describe("rejectReasonSchema", () => {
  it("빈_사유와_공백만은_거부한다", () => {
    expect(rejectReasonSchema.safeParse({ reason: "" }).success).toBe(false);
    expect(rejectReasonSchema.safeParse({ reason: "   \n " }).success).toBe(false);
  });

  it("앞뒤_공백은_잘라서_통과한다", () => {
    expect(rejectReasonSchema.parse({ reason: "  이미지가 흐립니다 " }).reason).toBe("이미지가 흐립니다");
  });

  it("500자는_통과하고_501자는_거부한다", () => {
    expect(rejectReasonSchema.safeParse({ reason: "가".repeat(500) }).success).toBe(true);
    expect(rejectReasonSchema.safeParse({ reason: "가".repeat(501) }).success).toBe(false);
  });
});

describe("commissionSchema", () => {
  it("0과_10000은_통과하고_범위_밖_소수_공백은_거부한다", () => {
    expect(commissionSchema.parse({ commissionRateBp: "1000" }).commissionRateBp).toBe(1000);
    expect(commissionSchema.safeParse({ commissionRateBp: "0" }).success).toBe(true);
    expect(commissionSchema.safeParse({ commissionRateBp: "10000" }).success).toBe(true);
    expect(commissionSchema.safeParse({ commissionRateBp: "10001" }).success).toBe(false);
    expect(commissionSchema.safeParse({ commissionRateBp: "10.5" }).success).toBe(false);
    expect(commissionSchema.safeParse({ commissionRateBp: "-1" }).success).toBe(false);
    expect(commissionSchema.safeParse({ commissionRateBp: "" }).success).toBe(false);
  });
});
