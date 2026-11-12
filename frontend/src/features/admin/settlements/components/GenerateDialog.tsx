"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useGenerateSettlements } from "@/features/admin/settlements/api";
import {
  generateResultText,
  PERIOD_GUIDANCE,
  PERIOD_START_MESSAGE,
  validatePeriodStart,
  type GenerateSettlementResult,
} from "@/features/admin/settlements/generate";
import { toast } from "@/hooks/use-toast";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { lastFinishedWeekMonday, periodLastDay, weekLastDay } from "@/lib/datetime";

type Values = { periodStart: string };

/** 주간 정산 생성 창. 열려 있는 동안만 마운트된다(닫으면 상태가 사라진다). 결과는 창 안에서 보여 준다. */
export function GenerateDialog({ onClose }: { onClose: () => void }) {
  // 창을 연 시각 기준으로 한 번만 정한다(기본값과 검사가 같은 시각을 쓴다).
  const [now] = useState(() => Date.now());
  const form = useForm<Values>({ defaultValues: { periodStart: lastFinishedWeekMonday(now) } });
  const generate = useGenerateSettlements();
  const [result, setResult] = useState<GenerateSettlementResult | null>(null);
  const [formError, setFormError] = useState<{ message: string; guidance: boolean } | null>(null);

  const value = form.watch("periodStart");
  const problem = validatePeriodStart(value, now);

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      setFormError(null);
      // 화면에서 먼저 막은 값은 보내지 않는다(제출 직전에도 한 번 더 확인).
      if (validatePeriodStart(values.periodStart, now) !== null) return;
      setResult(await generate(values.periodStart, key));
    },
    {
      onError: (e) => {
        const r = resolveError(e);
        if (r.kind === "form") setFormError({ message: r.message, guidance: e instanceof ApiError && e.code === "SETTLEMENT_PERIOD_INVALID" });
        else toast({ title: r.message, variant: "destructive" });
      },
    },
  );

  const text = result ? generateResultText(result) : null;

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-[520px]">
        <DialogHeader>
          <DialogTitle>{result ? "정산 생성 결과" : "주간 정산 생성"}</DialogTitle>
          <DialogDescription className="sr-only">정산을 만들 주의 월요일을 고릅니다.</DialogDescription>
        </DialogHeader>

        {result && text ? (
          <div className="flex flex-col gap-4">
            <div className="rounded border border-border bg-surface px-4 py-3 text-sm">
              <div className="text-xs text-text-2">기간</div>
              <div className="font-bold tabular-nums">
                {result.periodStart} ~ {periodLastDay(result.periodEnd)}
              </div>
              <div className="mt-1 text-text-2">
                대상 판매자 <b className="text-foreground">{result.sellerCount}명</b>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-2 text-center">
              {[
                ["생성", result.createdCount],
                ["건너뜀", result.skippedCount],
                ["실패", result.failedCount],
              ].map(([label, n]) => (
                <div key={label} className="rounded border border-border px-2 py-3">
                  <strong className="block text-xl tabular-nums">{n}</strong>
                  <span className="text-xs text-text-2">{label}</span>
                </div>
              ))}
            </div>
            <div role={text.tone === "warning" ? "alert" : "status"} className={text.tone === "warning" ? "notice notice-warn" : "notice"}>
              <span className="flex flex-col gap-1">
                {text.lines.map((l) => (
                  <span key={l}>{l}</span>
                ))}
              </span>
            </div>
            <div className="flex justify-end gap-2">
              {result.failedCount > 0 && !result.alreadyGenerated && (
                <button type="button" className="btn btn-ghost" onClick={() => setResult(null)}>
                  다시 생성
                </button>
              )}
              <button type="button" className="btn btn-primary" onClick={onClose}>
                {text.tone === "success" ? "목록 보기" : "닫기"}
              </button>
            </div>
          </div>
        ) : (
          <form
            noValidate
            className="flex flex-col gap-4"
            onSubmit={(e) => {
              if (submitting || problem !== null) {
                e.preventDefault();
                return;
              }
              void onSubmit(e);
            }}
          >
            {formError && (
              <div role="alert" className="notice notice-warn">
                <span className="flex flex-col gap-1">
                  <span>{formError.message}</span>
                  {formError.guidance && <span>{PERIOD_GUIDANCE}</span>}
                </span>
              </div>
            )}
            <div className="field">
              <label htmlFor="gen-period">기간 시작일 (월요일)</label>
              <input id="gen-period" type="date" className="input w-48" disabled={submitting} aria-invalid={problem ? true : undefined} aria-describedby="gen-period-msg" {...form.register("periodStart")} />
              {problem ? (
                <span id="gen-period-msg" role="alert" className="field-error">
                  {PERIOD_START_MESSAGE[problem]}
                </span>
              ) : (
                <span id="gen-period-msg" className="hint">
                  기본값은 가장 최근에 끝난 주의 월요일입니다.
                </span>
              )}
            </div>
            {!problem && (
              <div className="rounded border border-border bg-surface px-4 py-3 text-sm">
                정산 기간{" "}
                <b className="tabular-nums">
                  {value} ~ {weekLastDay(value)}
                </b>{" "}
                (월요일 ~ 일요일)
              </div>
            )}
            <div className="flex justify-end gap-2">
              <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onClose}>
                취소
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting || problem !== null}>
                정산 생성
              </button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
