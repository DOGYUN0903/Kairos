import { describe, expect, it } from "vitest";
import { newIdempotencyKey } from "@/api/idempotency";

describe("idempotency", () => {
  it("키는_UUID_v4_형식", () => {
    const a = newIdempotencyKey();
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(newIdempotencyKey()).not.toBe(a);
  });
});
