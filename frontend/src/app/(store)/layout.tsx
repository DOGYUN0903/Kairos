"use client";

import { useQuery } from "@tanstack/react-query";
import { usePathname, useSearchParams } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { CategoryNav } from "@/components/layout/CategoryNav";
import { Footer } from "@/components/layout/Footer";
import { StoreHeader } from "@/components/layout/StoreHeader";
import { isAuthPage } from "@/features/auth/redirect";
import { queryKeys } from "@/lib/query-keys";

type CategoryNode = components["schemas"]["CategoryNode"];

function StoreCategoryNav() {
  const params = useSearchParams();
  const onList = usePathname() === "/products";
  const raw = params.get("categoryId");
  const categoryId = raw && /^\d+$/.test(raw) ? Number(raw) : null;
  const { data } = useQuery({
    queryKey: queryKeys.catalog.categories,
    queryFn: async () => unwrap<CategoryNode[]>(await api.GET("/api/v1/categories")),
    staleTime: 5 * 60_000,
  });
  // 카테고리를 못 불러와도 내비만 비울 뿐 화면은 막지 않는다.
  return <CategoryNav categories={data ?? []} categoryId={onList ? categoryId : undefined} />;
}

export default function StoreLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  // 로그인·가입은 시안처럼 로고만 있는 헤더 + surface 배경.
  if (isAuthPage(pathname)) {
    return (
      <div className="min-h-screen bg-surface">
        <StoreHeader minimal />
        <main>{children}</main>
      </div>
    );
  }
  return (
    <>
      <StoreHeader />
      <Suspense fallback={<CategoryNav categories={[]} />}>
        <StoreCategoryNav />
      </Suspense>
      <main>{children}</main>
      <Footer />
    </>
  );
}
