import Link from "next/link";

/** 시안의 로그인/회원가입 탭. 실제로는 두 경로를 오가는 링크라 tablist 대신 nav + aria-current 를 쓴다. */
export function AuthTabs({ current, next }: { current: "login" | "signup"; next?: string | null }) {
  const q = next ? `?next=${encodeURIComponent(next)}` : "";
  const tab =
    "flex min-h-[44px] flex-1 items-center justify-center border-b-2 border-transparent -mb-px font-medium text-text-2 aria-[current=page]:border-navy aria-[current=page]:font-bold aria-[current=page]:text-navy";
  return (
    <nav aria-label="로그인 또는 회원가입" className="flex gap-1 border-b border-border">
      <Link href={`/login${q}`} className={tab} aria-current={current === "login" ? "page" : undefined}>
        로그인
      </Link>
      <Link href={`/signup${q}`} className={tab} aria-current={current === "signup" ? "page" : undefined}>
        회원가입
      </Link>
    </nav>
  );
}
