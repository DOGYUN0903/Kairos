import { decidedByLabel } from "@/features/returns/return-text";
import { returnEndNote, returnSteps, type ReturnStep } from "@/features/returns/return-steps";
import { formatDateTimeMinute } from "@/lib/datetime";
import { cn } from "@/lib/utils";

type StepsInput = {
  status: string;
  requestedAt: string;
  decidedAt?: string | null;
  decidedBy?: "SELLER" | "SYSTEM" | "ADMIN" | null;
  receivedAt?: string | null;
  completedAt?: string | null;
};

const STOP_LABEL: Record<string, string> = { REJECTED: "거절", WITHDRAWN: "철회", CANCELED_BY_ADMIN: "중단" };
const LABEL: Record<ReturnStep["key"], string> = { requested: "요청", approved: "승인", received: "수거 확인", completed: "환불 완료" };

function whoOf(step: ReturnStep, r: StepsInput): string {
  if (step.key === "requested") return "구매자";
  if (step.state === "stopped") return r.status === "WITHDRAWN" ? "구매자" : r.decidedBy ? decidedByLabel(r.decidedBy) : "";
  if (step.key === "approved") {
    if (step.state === "current") return "판매자가 확인 중";
    return step.state === "done" && r.decidedBy ? decidedByLabel(r.decidedBy) : "";
  }
  if (step.key === "received") return step.state === "done" ? "판매자" : "";
  return "";
}

/** 반품 진행 단계(요청 → 승인 → 수거 확인 → 환불 완료). 거절·철회·중단은 그 자리에서 끝난 것으로 보인다. 판매자·관리자 상세도 쓴다. */
export function ReturnSteps({ r }: { r: StepsInput }) {
  const steps = returnSteps(r);
  const note = returnEndNote(r.status);
  return (
    <div>
      <ol aria-label="반품 진행 단계" className="grid grid-cols-4 gap-3">
        {steps.map((s) => {
          const stopped = s.state === "stopped";
          const skipped = s.state === "todo" && r.status in STOP_LABEL;
          return (
            <li key={s.key} aria-current={s.state === "current" ? "step" : undefined} className={cn("flex flex-col gap-1 border-t-2 pt-2 text-[13px]", s.state === "done" ? "border-navy" : stopped ? "border-destructive" : s.state === "current" ? "border-navy/60" : "border-border", skipped && "opacity-40")}>
              <b className={cn("text-sm", stopped && "text-destructive", (s.state === "todo") && "text-text-2")}>{stopped ? (STOP_LABEL[r.status] ?? LABEL[s.key]) : LABEL[s.key]}</b>
              <span className="tabular-nums text-text-2">{s.at ? formatDateTimeMinute(s.at) : ""}</span>
              <span className="text-text-2">{whoOf(s, r)}</span>
            </li>
          );
        })}
      </ol>
      {note && <p className="mt-3 text-sm text-text-2">{note}</p>}
    </div>
  );
}
