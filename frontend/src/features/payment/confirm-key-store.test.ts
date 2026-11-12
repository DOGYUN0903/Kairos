import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

async function load() {
  return (await import("@/features/payment/confirm-key-store")).confirmKeyFor;
}

describe("confirmKeyFor", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.resetModules();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("같은_paymentKey는_항상_같은_키를_준다", async () => {
    const first = await load();
    const key = first("mock-a");
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    expect(first("mock-a")).toBe(key);

    // 새로고침: 모듈 상태는 사라지고 sessionStorage 만 남는다.
    vi.resetModules();
    const reloaded = await load();
    expect(reloaded).not.toBe(first);
    expect(reloaded("mock-a")).toBe(key);
    expect(sessionStorage.getItem("kairos:confirm:mock-a")).toBe(key);
  });

  it("다른_paymentKey는_다른_키를_준다", async () => {
    const confirmKeyFor = await load();
    expect(confirmKeyFor("mock-a")).not.toBe(confirmKeyFor("mock-b"));
  });

  it("sessionStorage가_막혀도_동작한다", async () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    const confirmKeyFor = await load();
    const key = confirmKeyFor("mock-a");
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    expect(confirmKeyFor("mock-a")).toBe(key);
    expect(confirmKeyFor("mock-b")).not.toBe(key);
  });
});
