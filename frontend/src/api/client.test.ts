// @vitest-environment node
// jsdom 의 Blob/FormData 는 Node(undici) Request 직렬화와 섞이지 않아 멀티파트 검증이 불가능하므로 node 환경에서 돌린다.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { __resetClientStateForTest, ApiError, createApiClient, setOnAuthLost } from "@/api/client";
import { getAccessToken, setAccessToken } from "@/auth/token-store";

type Call = { method: string; path: string; auth: string | null; retried: string | null; xrw: string | null; body: string };

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });

const unauthorized = () => json(401, { code: "UNAUTHORIZED", message: "인증이 필요합니다.", timestamp: "2026-11-08T12:00:00" });

let calls: Call[];

/** handler 가 요청별 응답을 정한다. 요청 순서·헤더·본문을 기록한다. */
function stubFetch(handler: (c: Call) => Response | Promise<Response>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (req: Request) => {
      const c: Call = {
        method: req.method,
        path: new URL(req.url).pathname,
        auth: req.headers.get("Authorization"),
        retried: req.headers.get("X-Retried"),
        xrw: req.headers.get("X-Requested-With"),
        body: req.body ? await req.text() : "",
      };
      calls.push(c);
      return handler(c);
    }),
  );
}

const reissueOk = (token: string) => json(200, { data: { accessToken: token, refreshToken: "r" } });
const okMe = () => json(200, { data: { id: 1 } });

const ORIGIN = "http://localhost:3000";

describe("api client", () => {
  const api = createApiClient(ORIGIN);

  beforeEach(() => {
    vi.stubGlobal("location", { origin: ORIGIN });
    calls = [];
    __resetClientStateForTest();
    setAccessToken("old");
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    setAccessToken(null);
  });

  it("요청에_Bearer가_붙는다", async () => {
    stubFetch(() => okMe());
    await api.GET("/api/v1/auth/me");
    expect(calls[0]?.auth).toBe("Bearer old");
  });

  it("401이면_reissue_한_번_뒤_원_요청을_한_번_재시도한다", async () => {
    stubFetch((c) => {
      if (c.path === "/api/v1/auth/reissue") return reissueOk("new");
      return c.auth === "Bearer new" ? json(200, { data: { content: [] } }) : unauthorized();
    });
    const r = await api.GET("/api/v1/seller/brands");
    expect(r.response.status).toBe(200);
    expect(calls.map((c) => `${c.path}|${c.auth}|${c.retried}`)).toEqual([
      "/api/v1/seller/brands|Bearer old|null",
      "/api/v1/auth/reissue|null|null",
      "/api/v1/seller/brands|Bearer new|1",
    ]);
    expect(calls[1]?.xrw).toBe("kairos-web");
    expect(calls[1]?.body).toBe("");
    expect(getAccessToken()).toBe("new");
  });

  it("동시_401_세_건은_reissue를_한_번만_호출하고_모두_같은_토큰으로_재시도한다", async () => {
    let releaseReissue: () => void = () => {};
    const gate = new Promise<void>((r) => (releaseReissue = r));
    let firstWaves = 0;
    stubFetch(async (c) => {
      if (c.path === "/api/v1/auth/reissue") {
        await gate;
        return reissueOk("new");
      }
      if (c.auth === "Bearer new") return okMe();
      firstWaves += 1;
      if (firstWaves === 3) setTimeout(releaseReissue, 0);
      return unauthorized();
    });
    const results = await Promise.all([
      api.GET("/api/v1/seller/brands"),
      api.GET("/api/v1/seller/me"),
      api.GET("/api/v1/seller/products"),
    ]);
    expect(results.every((r) => r.response.status === 200)).toBe(true);
    expect(calls.filter((c) => c.path === "/api/v1/auth/reissue")).toHaveLength(1);
    const retries = calls.filter((c) => c.retried === "1");
    expect(retries).toHaveLength(3);
    expect(retries.every((c) => c.auth === "Bearer new")).toBe(true);
  });

  it("auth_경로의_401은_재시도하지_않는다", async () => {
    const lost = vi.fn();
    setOnAuthLost(lost);
    stubFetch(() =>
      json(401, { code: "AUTH_INVALID_CREDENTIALS", message: "틀림", timestamp: "2026-11-08T12:00:00" }),
    );
    await expect(
      api.POST("/api/v1/auth/login", { body: { email: "a@b.c", password: "x" } }),
    ).rejects.toMatchObject({ status: 401, code: "AUTH_INVALID_CREDENTIALS" });
    expect(calls).toHaveLength(1);
    expect(lost).not.toHaveBeenCalled();
  });

  it("reissue_실패면_토큰을_비우고_onAuthLost를_부른다", async () => {
    const lost = vi.fn();
    setOnAuthLost(lost);
    stubFetch((c) => (c.path === "/api/v1/auth/reissue" ? unauthorized() : unauthorized()));
    await expect(api.GET("/api/v1/seller/brands")).rejects.toBeInstanceOf(ApiError);
    expect(getAccessToken()).toBeNull();
    expect(lost).toHaveBeenCalledTimes(1);
    expect(calls.map((c) => c.path)).toEqual(["/api/v1/seller/brands", "/api/v1/auth/reissue"]);
  });

  it("재시도도_401이면_다시_reissue하지_않는다", async () => {
    stubFetch((c) => (c.path === "/api/v1/auth/reissue" ? reissueOk("new") : unauthorized()));
    await expect(api.GET("/api/v1/seller/brands")).rejects.toMatchObject({ status: 401, code: "UNAUTHORIZED" });
    expect(calls.map((c) => c.path)).toEqual([
      "/api/v1/seller/brands",
      "/api/v1/auth/reissue",
      "/api/v1/seller/brands",
    ]);
  });

  it("재시도에_JSON_본문을_다시_보낸다", async () => {
    stubFetch((c) => {
      if (c.path === "/api/v1/auth/reissue") return reissueOk("new");
      return c.retried ? json(201, { data: { id: 1 } }) : unauthorized();
    });
    await api.POST("/api/v1/seller/brands", {
      body: { name: "브랜드" } as never,
      headers: { "Idempotency-Key": "k-1" },
    });
    const posts = calls.filter((c) => c.path === "/api/v1/seller/brands");
    expect(posts).toHaveLength(2);
    expect(posts[1]?.body).toBe(posts[0]?.body);
    expect(posts[1]?.body).toContain("브랜드");
  });

  it("FormData_본문도_재시도된다", async () => {
    stubFetch((c) => {
      if (c.path === "/api/v1/auth/reissue") return reissueOk("new");
      return c.retried ? json(201, { data: { id: 1 } }) : unauthorized();
    });
    const fd = new FormData();
    fd.append("file", new Blob(["IMAGEBYTES"], { type: "image/webp" }), "a.webp");
    await api.POST("/api/v1/files/images" as never, { body: fd, bodySerializer: (b: FormData) => b } as never);
    const uploads = calls.filter((c) => c.path === "/api/v1/files/images");
    expect(uploads).toHaveLength(2);
    expect(uploads[1]?.body).toContain("IMAGEBYTES");
    expect(uploads[1]?.body).toBe(uploads[0]?.body);
  });

  it("에러_응답은_ApiError에_code와_X_Trace_Id를_담는다", async () => {
    stubFetch(() =>
      json(409, { code: "BRAND_NAME_DUPLICATED", message: "중복", timestamp: "2026-11-08T12:00:00" }, { "X-Trace-Id": "trace-9" }),
    );
    const err = await api.GET("/api/v1/seller/brands").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 409, code: "BRAND_NAME_DUPLICATED", message: "중복", traceId: "trace-9" });
  });

  it("네트워크_실패는_status_0_ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("failed to fetch"))));
    await expect(api.GET("/api/v1/seller/brands")).rejects.toMatchObject({ status: 0, message: "네트워크 오류" });
  });
});
