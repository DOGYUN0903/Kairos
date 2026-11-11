import type { FieldValues, Path, UseFormReturn } from "react-hook-form";
import { resolveError } from "@/api/errors";
import { toast } from "@/hooks/use-toast";

/**
 * resolveError.kind 에 따라 서버 에러를 폼에 붙인다. 모든 폼이 공통으로 쓴다.
 * - field: 해당 필드 에러(폼에 그 필드가 없으면 폼 상단)
 * - form: 폼 상단(root.server)
 * - 나머지(toast/page/banner): 토스트. page/banner 의 화면 처리는 호출자 몫이다.
 */
export function applyFormError<T extends FieldValues, TOut = T>(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- RHF 의 TContext 기본값과 같다
  form: UseFormReturn<T, any, TOut>,
  err: unknown,
): void {
  const r = resolveError(err);
  if (r.kind === "field" && r.field && r.field in form.getValues()) {
    form.setError(r.field as Path<T>, { type: "server", message: r.message }, { shouldFocus: true });
    return;
  }
  if (r.kind === "field" || r.kind === "form") {
    form.setError("root.server", { type: "server", message: r.message });
    return;
  }
  toast({
    title: r.message,
    ...(r.traceId ? { description: `문의 시 전달: ${r.traceId}` } : {}),
  });
}
