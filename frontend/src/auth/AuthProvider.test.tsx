import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { __resetClientStateForTest } from "@/api/client";
import { AuthProvider } from "@/auth/AuthProvider";
import { getAccessToken, setAccessToken } from "@/auth/token-store";
import { useAuth } from "@/auth/useAuth";


// 모듈 기본 api 는 baseUrl "/" 라 Node Request 가 받지 못한다. 테스트에서는 절대 origin 으로 바꾼다.
vi.mock("@/api/client", async (importOriginal) => {
  const orig = await importOriginal<typeof import("@/api/client")>();
  return { ...orig, api: orig.createApiClient("http://localhost:3000") };
});

const router = { replace: vi.fn(), push: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const unauthorized = () => json(401, { code: "UNAUTHORIZED", message: "인증이 필요합니다.", timestamp: "2026-11-11T00:00:00" });
const me = { memberId: 1, email: "a@b.co", name: "김민지", role: "USER" };

let paths: string[];
function stubFetch(handler: (path: string, req: Request) => Response) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (req: Request) => {
      const path = new URL(req.url).pathname;
      paths.push(path);
      return handler(path, req);
    }),
  );
}

function Probe() {
  const { status, me: current, logout } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="name">{current?.name ?? ""}</span>
      <button onClick={() => void logout()}>로그아웃</button>
    </div>
  );
}

function renderWith(qc = new QueryClient()) {
  render(
    <QueryClientProvider client={qc}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
  return qc;
}

describe("AuthProvider", () => {
  beforeEach(() => {
    paths = [];
    __resetClientStateForTest();
    setAccessToken(null);
    router.replace.mockReset();
    router.push.mockReset();
    window.history.replaceState(null, "", "/seller/products?page=2");
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    setAccessToken(null);
  });

  it("reissue_200이면_me를_불러_authenticated", async () => {
    stubFetch((path) => {
      if (path === "/api/v1/auth/reissue") return json(200, { data: { accessToken: "t1", refreshToken: "r" } });
      if (path === "/api/v1/auth/me") return json(200, { data: me });
      return json(404, {});
    });
    renderWith();
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(screen.getByTestId("name")).toHaveTextContent("김민지");
    expect(paths).toEqual(["/api/v1/auth/reissue", "/api/v1/auth/me"]);
  });

  it("reissue_401이면_anonymous이고_me를_부르지_않는다", async () => {
    stubFetch(() => unauthorized());
    renderWith();
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(paths).toEqual(["/api/v1/auth/reissue"]);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("me가_실패하면_토큰을_비우고_anonymous", async () => {
    stubFetch((path) => {
      if (path === "/api/v1/auth/reissue") return json(200, { data: { accessToken: "t1", refreshToken: "r" } });
      return json(401, { code: "AUTH_INVALID_TOKEN", message: "x", timestamp: "2026-11-11T00:00:00" });
    });
    renderWith();
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(getAccessToken()).toBeNull();
  });

  it("logout은_토큰을_비우고_쿼리를_초기화한다", async () => {
    stubFetch((path) => {
      if (path === "/api/v1/auth/reissue") return json(200, { data: { accessToken: "t1", refreshToken: "r" } });
      if (path === "/api/v1/auth/me") return json(200, { data: me });
      if (path === "/api/v1/auth/logout") return new Response(null, { status: 204 });
      return json(404, {});
    });
    const qc = renderWith();
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    qc.setQueryData(["seller", "me"], { x: 1 });
    await act(async () => screen.getByText("로그아웃").click());
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(getAccessToken()).toBeNull();
    expect(qc.getQueryData(["seller", "me"])).toBeUndefined();
    expect(paths).toContain("/api/v1/auth/logout");
    expect(router.push).toHaveBeenCalledWith("/");
  });

  it("세션이_끊겨_onAuthLost가_여러_번_불려도_로그인_이동은_한_번", async () => {
    let reissues = 0;
    stubFetch((path) => {
      if (path === "/api/v1/auth/reissue") {
        reissues += 1;
        return reissues === 1 ? json(200, { data: { accessToken: "t1", refreshToken: "r" } }) : unauthorized();
      }
      if (path === "/api/v1/auth/me") return json(200, { data: me });
      return unauthorized();
    });
    const qc = renderWith();
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    qc.setQueryData(["seller", "me"], { x: 1 });
    const { api } = await import("@/api/client");
    // 동시에 세 건이 401 → reissue 실패 → onAuthLost 가 세 번 불린다.
    await act(async () => {
      await Promise.allSettled([
        api.GET("/api/v1/seller/me"),
        api.GET("/api/v1/seller/brands"),
        api.GET("/api/v1/seller/me"),
      ]);
    });
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(router.replace).toHaveBeenCalledTimes(1);
    expect(router.replace).toHaveBeenCalledWith("/login?next=" + encodeURIComponent("/seller/products?page=2"));
    expect(qc.getQueryData(["seller", "me"])).toBeUndefined();
  });

  it("재시도된_요청의_UNAUTHORIZED_쿼리_에러도_세션_종료로_처리한다", async () => {
    stubFetch((path) => {
      if (path === "/api/v1/auth/reissue") return json(200, { data: { accessToken: "t1", refreshToken: "r" } });
      if (path === "/api/v1/auth/me") return json(200, { data: me });
      return unauthorized();
    });
    const qc = renderWith();
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    const { api, unwrap } = await import("@/api/client");
    await act(async () => {
      await Promise.allSettled([
        qc.fetchQuery({ queryKey: ["a"], queryFn: async () => unwrap(await api.GET("/api/v1/seller/me")) }),
        qc.fetchQuery({ queryKey: ["b"], queryFn: async () => unwrap(await api.GET("/api/v1/seller/me")) }),
      ]);
    });
    await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
    expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
  });
});
