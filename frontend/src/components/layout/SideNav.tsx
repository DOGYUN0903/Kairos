"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export type NavItem = { href: string; label: string; badge?: number; disabled?: boolean; soonLabel?: "준비 중" };

const link = "flex min-h-[44px] items-center gap-2.5 rounded px-3 font-medium text-text-2";

/** 루트 항목(/seller, /admin)은 정확히 같을 때만, 나머지는 startsWith 로 현재 위치를 표시한다. */
function isCurrent(pathname: string, href: string, rootHref: string): boolean {
  if (href === rootHref) return pathname === href;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function SideNav({ items, label }: { items: NavItem[]; label: string }) {
  const pathname = usePathname();
  const rootHref = items[0]?.href ?? "";
  return (
    <nav aria-label={label} className="flex flex-col gap-0.5">
      {items.map((item) =>
        item.disabled ? (
          <span key={item.href} aria-disabled="true" className={`${link} cursor-not-allowed text-text-3`}>
            {item.label}
            {item.soonLabel && (
              <span className="ml-auto rounded-sm border border-border px-1 py-px text-[10px] font-bold text-text-3">{item.soonLabel}</span>
            )}
          </span>
        ) : (
          <Link
            key={item.href}
            href={item.href}
            aria-current={isCurrent(pathname, item.href, rootHref) ? "page" : undefined}
            className={`${link} hover:bg-surface-2 hover:text-navy aria-[current=page]:bg-navy aria-[current=page]:text-white`}
          >
            {item.label}
            {item.badge != null && item.badge > 0 && (
              <span className="ml-auto min-w-5 rounded-full bg-danger px-1.5 text-center text-[11px] font-bold leading-5 text-white" aria-label={`대기 ${item.badge}건`}>
                {item.badge}
              </span>
            )}
          </Link>
        ),
      )}
    </nav>
  );
}
