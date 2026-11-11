"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ApiError } from "@/api/client";
import { AuthProvider } from "@/auth/AuthProvider";
import { Toaster } from "@/components/ui/toaster";

export function Providers({ children }: { children: ReactNode }) {
  // 재시도: GET 1회, 뮤테이션 0회(Global Constraints). 낙관적 업데이트 없음.
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          // 4xx 는 재시도해도 같은 결과다(401 은 클라이언트 미들웨어가 이미 한 번 재발급·재시도했다).
          queries: { retry: (count, err) => count < 1 && !(err instanceof ApiError && err.status >= 400 && err.status < 500), refetchOnWindowFocus: false, staleTime: 30_000 },
          mutations: { retry: 0 },
        },
      }),
  );
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
      <div aria-live="polite">
        <Toaster />
      </div>
    </QueryClientProvider>
  );
}
