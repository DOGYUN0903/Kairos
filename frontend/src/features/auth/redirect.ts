import type { Me } from "@/features/auth/api";

/** 오픈 리다이렉트 방지: 같은 출처의 절대 경로만 허용한다. */
export function safeNext(next: string | null | undefined): string | null {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return null;
  if (next.startsWith("/login") || next.startsWith("/signup")) return null;
  return next;
}

/** 로그인 성공 후 이동: next 가 있으면 거기, 없으면 ADMIN 은 /admin, 나머지는 /. */
export function afterLoginPath(me: Pick<Me, "role">, next: string | null | undefined): string {
  return safeNext(next) ?? (me.role === "ADMIN" ? "/admin" : "/");
}
