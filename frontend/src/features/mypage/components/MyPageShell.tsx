"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { MY_MENU, activeMenuHref } from "@/features/mypage/menu";

export function MyPageShell({ children }: { children: ReactNode }) {
  const active = activeMenuHref(usePathname());
  return (
    <div className="container grid grid-cols-[200px_minmax(0,1fr)] items-start gap-8 pb-16 pt-9">
      <nav aria-label="마이페이지 메뉴">
        <h2 className="mb-3 text-[18px] font-bold tracking-tight">마이페이지</h2>
        <ul className="flex flex-col gap-1">
          {MY_MENU.map((m) => {
            const current = m.href === active;
            return (
              <li key={m.href}>
                <Link
                  href={m.href}
                  {...(current ? { "aria-current": "page" as const } : {})}
                  className={`block rounded-md px-3 py-2 text-[14px] hover:bg-bg-2 ${current ? "bg-bg-2 font-bold text-navy" : "text-text-2"}`}
                >
                  {m.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
