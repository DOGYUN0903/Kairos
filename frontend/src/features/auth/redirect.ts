import type { Me } from "@/features/auth/api";

const AUTH_PAGES = ["/login", "/signup"];

/** /login, /login/ 처럼 정확히 인증 화면인지(/login-help 는 아님). */
export function isAuthPage(pathname: string): boolean {
  const p = pathname.length > 1 && pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
  return AUTH_PAGES.includes(p);
}

function appOrigin(): string {
  return globalThis.location?.origin ?? "http://localhost";
}

/**
 * 오픈 리다이렉트 방지: 제어 문자·공백이 있으면 거부하고, 앱 origin 기준으로 해석해
 * 같은 origin 일 때만 pathname+search+hash 를 돌려준다. 인증 화면 자체는 거부.
 */
export function safeNext(next: string | null | undefined, origin: string = appOrigin()): string | null {
  if (!next || !next.startsWith("/")) return null;
  if (/[\u0000-\u001F\u007F\s\\]/.test(next)) return null;
  let url: URL;
  try {
    url = new URL(next, origin);
  } catch {
    return null;
  }
  if (url.origin !== new URL(origin).origin) return null;
  if (isAuthPage(url.pathname)) return null;
  return url.pathname + url.search + url.hash;
}

/** 로그인 성공 후 이동: next 가 있으면 거기, 없으면 ADMIN 은 /admin, 나머지는 /. */
export function afterLoginPath(me: Pick<Me, "role">, next: string | null | undefined): string {
  return safeNext(next) ?? (me.role === "ADMIN" ? "/admin" : "/");
}
