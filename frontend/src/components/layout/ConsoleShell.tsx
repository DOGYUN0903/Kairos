"use client";

import type { ReactNode } from "react";
import { useAuth } from "@/auth/useAuth";
import { Logo } from "@/components/layout/StoreHeader";
import { SideNav, type NavItem } from "@/components/layout/SideNav";

export type { NavItem };

export function ConsoleShell({
  role,
  nav,
  userLabel,
  title,
  banner,
  children,
}: {
  role: "판매자 센터" | "관리자 콘솔";
  nav: NavItem[];
  userLabel: string;
  /** console__top h1. 없으면 역할 이름. */
  title?: string;
  /** 본문 위 전폭 배너(판매자 정지 중, 서버 409 SELLER_NOT_ACTIVE 등). */
  banner?: ReactNode;
  children: ReactNode;
}) {
  const { logout } = useAuth();
  return (
    <div className="grid min-h-screen grid-cols-[232px_1fr]">
      <aside className="border-r border-border bg-surface px-4 py-6">
        <div className="mx-2 mb-1">
          <Logo />
        </div>
        <div className="mx-2 mb-6 text-xs text-text-2">{role}</div>
        <SideNav items={nav} label={role === "판매자 센터" ? "판매자 메뉴" : "관리자 메뉴"} />
      </aside>
      <div className="min-w-0">
        <header className="flex h-16 items-center justify-between border-b border-border px-8">
          <h1 className="text-lg font-bold">{title ?? role}</h1>
          <div className="flex items-center gap-2.5 text-[13px] text-text-2">
            <span>{userLabel}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void logout()}>
              로그아웃
            </button>
          </div>
        </header>
        <main className="max-w-[1200px] px-8 pb-16 pt-7">
          {banner && <div className="mb-5">{banner}</div>}
          {children}
        </main>
      </div>
    </div>
  );
}
