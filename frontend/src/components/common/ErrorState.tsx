import { resolveError } from "@/api/errors";

export function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const { message, traceId } = resolveError(error);
  return (
    <div role="alert" className="flex flex-col items-center gap-3 px-6 py-16 text-center">
      <p className="text-text-2">{message || "문제가 발생했습니다."}</p>
      <button type="button" className="btn" onClick={onRetry}>
        다시 시도
      </button>
      {traceId && <p className="text-xs text-text-3">문의 시 전달: {traceId}</p>}
    </div>
  );
}
