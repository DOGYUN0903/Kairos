import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "@/api/schema";
import { getAccessToken, setAccessToken } from "@/auth/token-store";

export type ErrorCode = components["schemas"]["ErrorResponse"]["code"];

export const REQUESTED_WITH = { "X-Requested-With": "kairos-web" } as const;

const AUTH_PREFIX = "/api/v1/auth/";
const REISSUE_PATH = "/api/v1/auth/reissue";
const RETRIED_HEADER = "X-Retried";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ErrorCode,
    message: string,
    readonly traceId: string | null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

let onAuthLost: () => void = () => {};

/** Task 7 AuthProvider 가 등록한다: QueryClient 초기화 + /login?next= 이동. */
export function setOnAuthLost(fn: () => void): void {
  onAuthLost = fn;
}

let inflightReissue: Promise<string | null> | null = null;

function origin(): string {
  return globalThis.location?.origin ?? "";
}

/**
 * 진행 중인 재발급 Promise 를 공유한다. 동시에 N 건이 401 을 받아도 reissue 는 한 번만 나간다.
 * 리프레시 토큰은 HttpOnly 쿠키(Path=/api/v1/auth)라 본문 없이 X-Requested-With 만 보낸다.
 */
export function reissueOnce(): Promise<string | null> {
  if (!inflightReissue) {
    inflightReissue = doReissue().finally(() => {
      inflightReissue = null;
    });
  }
  return inflightReissue;
}

async function doReissue(): Promise<string | null> {
  try {
    const res = await globalThis.fetch(
      new Request(`${origin()}${REISSUE_PATH}`, {
        method: "POST",
        headers: { ...REQUESTED_WITH },
        credentials: "same-origin",
      }),
    );
    if (res.ok) {
      const body = (await res.json()) as { data?: { accessToken?: string } | null };
      const token = body.data?.accessToken ?? null;
      setAccessToken(token);
      return token;
    }
  } catch {
    // 네트워크 실패도 재발급 실패로 본다.
  }
  setAccessToken(null);
  return null;
}

async function toApiError(response: Response): Promise<ApiError> {
  const traceId = response.headers.get("X-Trace-Id");
  try {
    const body = (await response.clone().json()) as Partial<components["schemas"]["ErrorResponse"]>;
    if (body && typeof body.code === "string") {
      return new ApiError(response.status, body.code, body.message ?? "", traceId);
    }
  } catch {
    // 본문이 JSON 이 아니면 아래 기본값.
  }
  return new ApiError(response.status, "INTERNAL_ERROR", `요청이 실패했습니다 (${response.status})`, traceId);
}

function bearerOf(request: Request): string | null {
  const header = request.headers.get("Authorization");
  return header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
}

function createAuthMiddleware(): Middleware {
  // onResponse 시점의 request 는 본문이 이미 소비됐으므로, 전송 전에 복제본을 보관한다(FormData 포함).
  const pristine = new WeakMap<Request, Request>();

  return {
    onRequest({ request }) {
      const token = getAccessToken();
      if (token && !request.headers.has("Authorization")) {
        request.headers.set("Authorization", `Bearer ${token}`);
      }
      const path = new URL(request.url).pathname;
      if (!path.startsWith(AUTH_PREFIX)) {
        pristine.set(request, request.clone());
      }
      return request;
    },
    async onResponse({ request, response }) {
      let finalResponse = response;
      const path = new URL(request.url).pathname;
      const copy = pristine.get(request);
      pristine.delete(request);

      if (response.status === 401 && !path.startsWith(AUTH_PREFIX) && !request.headers.has(RETRIED_HEADER) && copy) {
        // 이미 다른 요청이 토큰을 갱신했다면 재발급 없이 현재 토큰으로 재시도한다.
        const current = getAccessToken();
        const sent = bearerOf(request);
        const token = current && current !== sent ? current : await reissueOnce();
        if (!token) {
          onAuthLost();
          throw await toApiError(response);
        }
        copy.headers.set("Authorization", `Bearer ${token}`);
        copy.headers.set(RETRIED_HEADER, "1");
        try {
          finalResponse = await globalThis.fetch(copy);
        } catch {
          throw networkError();
        }
      }

      if (!finalResponse.ok) {
        throw await toApiError(finalResponse);
      }
      return finalResponse;
    },
    onError() {
      return networkError();
    },
  };
}

function networkError(): ApiError {
  return new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
}

export function createApiClient(baseUrl: string) {
  const client = createClient<paths>({
    baseUrl,
    // createClient 는 기본 fetch 를 생성 시점에 캡처하므로, 호출 시점의 전역 fetch 를 쓰도록 감싼다.
    fetch: (request: Request) => globalThis.fetch(request),
    credentials: "same-origin",
  });
  client.use(createAuthMiddleware());
  return client;
}

export const api = createApiClient("/");

/** ApiResponse 언래핑. 에러 응답은 미들웨어가 ApiError 로 던지므로 여기 도달하지 않는다. */
export function unwrap<T>(r: { data?: { data?: T } }): T {
  if (r.data === undefined) {
    throw new ApiError(0, "INTERNAL_ERROR", "응답 본문이 없습니다", null);
  }
  return r.data.data as T;
}

/** 테스트 전용: 모듈 상태 초기화. */
export function __resetClientStateForTest(): void {
  inflightReissue = null;
  onAuthLost = () => {};
}
