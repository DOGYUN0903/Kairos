"use client";

import { notFound } from "next/navigation";
import { ApiError } from "@/api/client";
import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useBrand } from "@/features/catalog/api";

export function BrandHeader({ brandId }: { brandId: number }) {
  const { data, isPending, isError, error, refetch } = useBrand(brandId);
  if (isError && error instanceof ApiError && error.code === "BRAND_NOT_FOUND") notFound();
  if (isPending) {
    return (
      <div aria-busy="true" aria-label="불러오는 중" className="container flex items-center gap-4 py-8">
        <Skeleton className="h-16 w-16 rounded-full" />
        <Skeleton className="h-8 w-56" />
      </div>
    );
  }
  if (isError) return <ErrorState error={error} onRetry={() => void refetch()} />;
  return (
    <header className="container flex items-center gap-4 pt-8">
      {data.logoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로
        <img src={data.logoUrl} alt={`${data.name} 로고`} className="h-16 w-16 rounded-full border border-border object-cover" />
      ) : (
        <div aria-hidden className="grid h-16 w-16 place-items-center rounded-full bg-surface-2 text-xl font-black text-navy">
          {data.name.slice(0, 1)}
        </div>
      )}
      <div>
        <h1 className="text-2xl font-bold">
          {data.name}
          {data.englishName && <span className="ml-2 text-sm font-normal text-text-2">{data.englishName}</span>}
        </h1>
        {data.description && <p className="mt-1 max-w-[720px] whitespace-pre-line text-text-2">{data.description}</p>}
      </div>
    </header>
  );
}
