"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createContext, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { ApiError, reissueOnce, setOnAuthLost } from "@/api/client";
import { setAccessToken } from "@/auth/token-store";
import { fetchMe, loginRequest, logoutRequest, type Me } from "@/features/auth/api";

export type AuthStatus = "loading" | "anonymous" | "authenticated";

export type AuthContextValue = {
  status: AuthStatus;
  me: Me | null;
  login(email: string, password: string): Promise<Me>;
  logout(): Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

const AUTH_PAGES = ["/login", "/signup"];

/** 재시도된 요청까지 401 이면 세션이 끝난 것으로 본다. 로그인 실패(AUTH_INVALID_CREDENTIALS)는 제외. */
function isSessionLost(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401 && (err.code === "UNAUTHORIZED" || err.code === "AUTH_INVALID_TOKEN");
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [me, setMe] = useState<Me | null>(null);
  // 한 번의 실패한 재발급에 onAuthLost 가 N 번 불릴 수 있다. 세션당 한 번만 처리한다.
  // 로그인 성공(또는 부트스트랩 성공) 때만 다시 false 가 된다.
  const lostHandled = useRef(true);

  const handleAuthLost = useCallback(() => {
    if (lostHandled.current) return;
    lostHandled.current = true;
    setAccessToken(null);
    setMe(null);
    setStatus("anonymous");
    queryClient.clear();
    const { pathname, search } = window.location;
    if (!AUTH_PAGES.includes(pathname)) {
      router.replace(`/login?next=${encodeURIComponent(pathname + search)}`);
    }
  }, [queryClient, router]);

  useEffect(() => {
    setOnAuthLost(handleAuthLost);
    const onError = (err: unknown) => {
      if (isSessionLost(err)) handleAuthLost();
    };
    const unsubQ = queryClient.getQueryCache().subscribe((e) => {
      if (e.type === "updated" && e.action.type === "error") onError(e.action.error);
    });
    const unsubM = queryClient.getMutationCache().subscribe((e) => {
      if (e.type === "updated" && e.action.type === "error") onError(e.action.error);
    });
    return () => {
      unsubQ();
      unsubM();
      setOnAuthLost(() => {});
    };
  }, [handleAuthLost, queryClient]);

  // 부트스트랩: 쿠키로 조용히 재발급 → /auth/me. reissueOnce 는 진행 중 Promise 를 공유하므로 StrictMode 이중 실행에도 한 번만 나간다.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await reissueOnce();
      let current: Me | null = null;
      if (token) {
        try {
          current = await fetchMe();
        } catch {
          setAccessToken(null);
        }
      }
      if (cancelled) return;
      if (current) {
        lostHandled.current = false;
        setMe(current);
        setStatus("authenticated");
      } else {
        setStatus("anonymous");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const token = await loginRequest(email, password);
      setAccessToken(token);
      let current: Me;
      try {
        current = await fetchMe();
      } catch (e) {
        setAccessToken(null);
        throw e;
      }
      queryClient.clear();
      lostHandled.current = false;
      setMe(current);
      setStatus("authenticated");
      return current;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    // 로그아웃 도중 뜨는 401 이 /login 으로 보내지 않도록 먼저 막는다.
    lostHandled.current = true;
    try {
      await logoutRequest();
    } catch {
      // 서버 실패와 무관하게 클라이언트 세션은 끝낸다.
    }
    setAccessToken(null);
    setMe(null);
    setStatus("anonymous");
    queryClient.clear();
    router.push("/");
  }, [queryClient, router]);

  const value = useMemo(() => ({ status, me, login, logout }), [status, me, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
