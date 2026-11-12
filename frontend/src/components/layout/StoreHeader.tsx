"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Search } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/auth/useAuth";
import { useCartCount } from "@/features/cart/api";

const utilLink = "flex h-[52px] min-w-14 items-center justify-center rounded px-3 text-[13px] text-text-2 hover:bg-surface hover:text-navy";

export function Logo({ size = "lg" }: { size?: "lg" | "sm" }) {
  return (
    <Link href="/" aria-label="KAIROS 홈" className={size === "lg" ? "text-[22px] font-black tracking-[4px] text-navy" : "text-base font-black tracking-[4px] text-navy"}>
      KAIROS
    </Link>
  );
}

export function StoreHeader({ minimal = false }: { minimal?: boolean }) {
  const { status, me, logout } = useAuth();
  const router = useRouter();
  const [keyword, setKeyword] = useState("");
  const cartCount = useCartCount();

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    const q = keyword.trim();
    router.push(q ? `/products?keyword=${encodeURIComponent(q)}` : "/products");
  };

  return (
    <header className="sticky top-0 z-10 border-b border-border bg-bg">
      <div className="container flex h-[72px] items-center gap-6">
        <Logo />
        {!minimal && (
          <>
            <form role="search" onSubmit={onSearch} className="mx-auto flex max-w-[480px] flex-1">
              <label htmlFor="header-q" className="sr-only">
                상품명 또는 브랜드 검색
              </label>
              <input
                id="header-q"
                type="search"
                name="keyword"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="상품명, 브랜드를 검색하세요"
                className="h-11 flex-1 rounded-l border border-r-0 border-navy bg-bg px-3.5 placeholder:text-text-3 focus:outline-none"
              />
              <button type="submit" aria-label="검색" className="grid h-11 w-12 place-items-center rounded-r border border-navy bg-navy text-white">
                <Search className="h-5 w-5" aria-hidden />
              </button>
            </form>
            <nav aria-label="사용자 메뉴" className="ml-auto flex gap-1">
              {status === "loading" && <Skeleton className="h-9 w-40" />}
              {status === "anonymous" && (
                <>
                  <Link className={utilLink} href="/login">
                    로그인
                  </Link>
                  <Link className={utilLink} href="/signup">
                    가입
                  </Link>
                </>
              )}
              {status === "authenticated" && (
                <>
                  <Link className={`${utilLink} relative`} href="/cart" aria-label={cartCount ? `장바구니 ${cartCount}개` : "장바구니"}>
                    장바구니
                    {cartCount !== null && cartCount > 0 && (
                      <span
                        aria-hidden
                        className="absolute right-0.5 top-1.5 min-w-4 rounded-lg bg-navy px-1 text-center text-[10px] font-bold leading-4 text-white"
                      >
                        {cartCount}
                      </span>
                    )}
                  </Link>
                  <Link className={utilLink} href="/my/orders">
                    마이
                  </Link>
                  <Link className={utilLink} href="/seller">
                    판매자 센터
                  </Link>
                  {me?.role === "ADMIN" && (
                    <Link className={utilLink} href="/admin">
                      관리자
                    </Link>
                  )}
                  <button type="button" className={utilLink} onClick={() => void logout()}>
                    로그아웃
                  </button>
                </>
              )}
            </nav>
          </>
        )}
      </div>
    </header>
  );
}
