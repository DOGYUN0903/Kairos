"use client";

import { Coins, Heart, Receipt, Star, Ticket, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { MY_MENU, activeMenuHref } from "@/features/mypage/menu";

const ICONS: Record<string, LucideIcon> = {
  "/my/orders": Receipt,
  "/my/coupons": Ticket,
  "/my/points": Coins,
  "/my/reviews": Star,
  "/my/likes": Heart,
};

/**
 * 마이페이지 틀(시안 user-mypage.html). 왼쪽 메뉴 + 본문.
 * `meta` 는 메뉴 항목 오른쪽에 붙일 값(쿠폰 장수·포인트·좋아요 수 등)을 href 로 받는 확장 지점이다.
 */
export function MyPageShell({ children, meta }: { children: ReactNode; meta?: Partial<Record<string, ReactNode>> }) {
  const active = activeMenuHref(usePathname());
  return (
    <div className="container grid grid-cols-[220px_minmax(0,1fr)] items-start gap-10 pb-16 pt-8">
      <aside aria-label="마이페이지 메뉴" className="sticky top-24">
        <h2 className="mb-3.5 text-[20px] font-bold">마이페이지</h2>
        <nav>
          <ul className="flex flex-col">
            {MY_MENU.map((m) => {
              const current = m.href === active;
              const Icon = ICONS[m.href];
              const extra = meta?.[m.href];
              return (
                <li key={m.href}>
                  <Link
                    href={m.href}
                    {...(current ? { "aria-current": "page" as const } : {})}
                    className={`flex min-h-12 items-center gap-2.5 rounded px-3 font-medium ${current ? "bg-navy text-white" : "text-text-2 hover:bg-surface hover:text-navy"}`}
                  >
                    {Icon && <Icon className="h-4 w-4" aria-hidden />}
                    {m.label}
                    {extra != null && <span className="ml-auto text-xs tabular-nums opacity-85">{extra}</span>}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
