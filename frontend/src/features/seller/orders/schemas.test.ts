import { describe, expect, it } from "vitest";
import { shipSchema } from "./schemas";

describe("shipSchema", () => {
  it("송장번호_앞뒤_공백을_없앤다", () => {
    const r = shipSchema.safeParse({ carrierCode: "04", trackingNumber: "  681233459021 " });
    expect(r.success && r.data.trackingNumber).toBe("681233459021");
  });

  it("빈_송장번호와_51자는_거절한다", () => {
    expect(shipSchema.safeParse({ carrierCode: "04", trackingNumber: "   " }).success).toBe(false);
    expect(shipSchema.safeParse({ carrierCode: "04", trackingNumber: "1".repeat(51) }).success).toBe(false);
    expect(shipSchema.safeParse({ carrierCode: "04", trackingNumber: "1".repeat(50) }).success).toBe(true);
  });

  it("목록에_없는_택배사는_거절한다", () => {
    expect(shipSchema.safeParse({ carrierCode: "99", trackingNumber: "123" }).success).toBe(false);
    expect(shipSchema.safeParse({ carrierCode: "", trackingNumber: "123" }).success).toBe(false);
  });
});
