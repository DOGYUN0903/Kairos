import type { ReactNode } from "react";

/** 마이페이지 본문 제목줄(시안 .my-head): 제목 + 오른쪽 보조 영역, 아래에 네이비 2px 선. */
export function MyPageHead({ title, aside }: { title: string; aside?: ReactNode }) {
  return (
    <div className="flex items-end justify-between gap-4 border-b-2 border-navy pb-3.5">
      <h1 className="text-[22px] font-bold tracking-tight">{title}</h1>
      {aside}
    </div>
  );
}
