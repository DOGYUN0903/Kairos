export type ReturnStep = { key: "requested" | "approved" | "received" | "completed"; state: "done" | "current" | "todo" | "stopped"; at?: string };

type StepState = ReturnStep["state"];

/** 진행 막대의 네 칸. 시각은 서버 값 그대로 붙인다. */
export function returnSteps(r: { status: string; requestedAt: string; decidedAt?: string | null; receivedAt?: string | null; completedAt?: string | null }): ReturnStep[] {
  // 몇 번째 칸까지 끝났는가(요청은 항상 끝남), 그다음 칸이 current 인가 stopped 인가.
  let done = 1;
  let next: StepState = "todo";
  switch (r.status) {
    case "REQUESTED": next = "current"; break;
    case "APPROVED": done = 2; next = "current"; break;
    case "REFUNDING": done = 3; next = "current"; break;
    case "COMPLETED": done = 4; break;
    case "REJECTED":
    case "WITHDRAWN": next = "stopped"; break;
    case "CANCELED_BY_ADMIN":
      done = r.receivedAt ? 3 : r.decidedAt ? 2 : 1;
      next = "stopped";
      break;
    default: break;
  }
  const keys = ["requested", "approved", "received", "completed"] as const;
  const times = [r.requestedAt, r.decidedAt, r.receivedAt, r.completedAt];
  return keys.map((key, i): ReturnStep => {
    const state: StepState = i < done ? "done" : i === done ? next : "todo";
    const at = times[i];
    // 멈춘 칸에는 결정 시각이 있을 때만(거절 등) 붙인다.
    return state === "done" || (state === "stopped" && at) ? { key, state, ...(at ? { at } : {}) } : { key, state };
  });
}

export function returnEndNote(status: string): string | null {
  switch (status) {
    case "REJECTED": return "판매자가 반품을 거절했습니다";
    case "WITHDRAWN": return "반품 요청을 철회했습니다";
    case "CANCELED_BY_ADMIN": return "관리자가 반품을 중단했습니다";
    default: return null;
  }
}
