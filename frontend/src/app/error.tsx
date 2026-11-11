"use client";

import { ApiError } from "@/api/client";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const traceId = error instanceof ApiError ? error.traceId : null;
  return (
    <main className="container flex flex-col items-center gap-4 py-24 text-center">
      <h1 className="text-2xl font-bold">문제가 발생했습니다</h1>
      <button type="button" className="btn btn-primary" onClick={() => reset()}>
        다시 시도
      </button>
      {traceId && <p className="text-xs text-text-3">문의 시 전달: {traceId}</p>}
    </main>
  );
}
