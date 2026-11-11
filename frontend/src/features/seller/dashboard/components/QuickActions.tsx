"use client";

import Link from "next/link";
import { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";

const DISABLED_TITLE = "정지 중";

/** 링크처럼 보이는 버튼. 정지 중에는 링크가 아닌 비활성 요소로 그린다(링크는 disabled 가 없다). */
function ActionLink({ href, enabled, className, children }: { href: string; enabled: boolean; className: string; children: string }) {
  if (enabled) {
    return (
      <Link href={href} className={`btn btn-block ${className}`}>
        {children}
      </Link>
    );
  }
  return (
    <button type="button" disabled aria-disabled="true" title={DISABLED_TITLE} className={`btn btn-block ${className}`}>
      {children}
    </button>
  );
}

export function QuickActions() {
  const { canManage } = useSellerCapabilities();
  return (
    <section className="card" aria-labelledby="quick-h">
      <div className="border-b border-border px-5 py-4">
        <h2 id="quick-h" className="text-[15px] font-bold">
          바로 가기
        </h2>
      </div>
      <div className="flex flex-col gap-2 p-5">
        <ActionLink href="/seller/products/new" enabled={canManage} className="btn-primary">
          상품 등록
        </ActionLink>
        <ActionLink href="/seller/brands?new=1" enabled={canManage} className="">
          브랜드 신청
        </ActionLink>
        <Link href="/seller/products" className="btn btn-ghost btn-block">
          상품 관리
        </Link>
      </div>
    </section>
  );
}
