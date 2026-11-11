import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DemoBar } from "@/features/demo/DemoBar";

const calls: string[] = [];
const login = vi.fn(async (email: string) => {
  calls.push(`login:${email}`);
  return { memberId: 1, email, name: "x", role: email.startsWith("admin") ? "ADMIN" : "USER" };
});
const logout = vi.fn(async () => {
  calls.push("logout");
});
vi.mock("@/auth/useAuth", () => ({ useAuth: () => ({ login, logout }) }));
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
const reseedDemo = vi.fn(async () => {
  calls.push("reseed");
  return { members: 0, sellers: 0, brands: 0, categories: 0, products: 5, pendingReviews: 3 };
});
vi.mock("@/features/demo/api", () => ({ reseedDemo: () => reseedDemo() }));

function setup() {
  const qc = new QueryClient();
  const spy = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <DemoBar />
    </QueryClientProvider>,
  );
  return spy;
}

describe("DemoBar", () => {
  beforeEach(() => {
    calls.length = 0;
    vi.clearAllMocks();
  });

  it("판매자로_전환은_logout_뒤_seller_계정으로_login한다", async () => {
    setup();
    await userEvent.click(screen.getByRole("button", { name: "판매자로 전환" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/"));
    expect(calls).toEqual(["logout", "login:seller@kairos.demo"]);
  });

  it("관리자로_전환하면_관리자_기본_페이지로_이동한다", async () => {
    setup();
    await userEvent.click(screen.getByRole("button", { name: "관리자로 전환" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin"));
  });

  it("데이터_보충은_관리자_로그인_뒤_reseed를_부른다", async () => {
    const spy = setup();
    await userEvent.click(screen.getByRole("button", { name: "데모 데이터 보충" }));
    await waitFor(() => expect(spy).toHaveBeenCalled());
    expect(calls).toEqual(["logout", "login:admin@kairos.demo", "reseed"]);
  });
});
