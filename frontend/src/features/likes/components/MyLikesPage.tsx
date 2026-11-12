"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Money } from "@/components/common/Money";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { LikeButton } from "@/features/catalog/components/LikeButton";
import { isOnSale, useMyLikes, type LikedProduct } from "@/features/likes/api";
import { MyPageHead } from "@/features/mypage/components/MyPageHead";
import { parsePage } from "@/lib/page";
import { cn } from "@/lib/utils";

/** 좋아요한 상품 카드. 정가·할인율·평점은 이 응답에 없어 그리지 않는다. */
function LikedCard({ product }: { product: LikedProduct }) {
  const href = `/products/${product.productId}`;
  const ended = !isOnSale(product.productStatus);
  return (
    <li className="relative">
      <Link href={href} className="block" aria-label={`${product.brandName} ${product.name}${ended ? " (판매 종료)" : ""}`}>
        <div className="relative aspect-square overflow-hidden rounded bg-surface-2">
          {product.thumbnailUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
            <img src={product.thumbnailUrl} alt={product.name} loading="lazy" className={cn("h-full w-full object-cover", ended && "opacity-45")} />
          ) : (
            <div className="grid h-full w-full place-items-center text-xs text-text-3">이미지 없음</div>
          )}
          {ended && <div className="absolute inset-0 grid place-items-center rounded bg-navy/55 text-[15px] font-bold text-white">판매 종료</div>}
        </div>
      </Link>
      <div className={cn("pt-2.5", ended && "opacity-45")}>
        <p className="text-xs font-bold">{product.brandName}</p>
        <p className="mt-0.5 line-clamp-2 text-sm text-text-2">
          <Link href={href} className="hover:underline">
            {product.name}
          </Link>
        </p>
        <Money value={product.salePrice} className="mt-1.5 block text-[15px] font-bold" />
      </div>
      <div className="absolute right-2 top-2">
        <LikeButton productId={product.productId} liked tone="navy" label={`${product.name} 좋아요 취소`} />
      </div>
    </li>
  );
}

/** 마이페이지 좋아요. ?page= 가 진실이다. 하트를 누르면 좋아요가 취소되고 목록을 다시 읽는다. */
export function MyLikesPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const q = useMyLikes(page);
  const data = q.data;
  const go = (p: number) => router.push(p > 0 ? `${pathname}?page=${p}` : pathname);

  return (
    <div>
      <MyPageHead title="좋아요" />
      {q.isError && !data ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : data.content.length === 0 ? (
        data.totalPages > 0 && page > 0 ? (
          <EmptyState message="해당 페이지에 상품이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
        ) : (
          <EmptyState message="좋아요한 상품이 없습니다" action={{ label: "상품 보러 가기", href: "/products" }} />
        )
      ) : (
        <>
          <ul className="mt-6 grid grid-cols-4 gap-x-5 gap-y-8">
            {data.content.map((p) => (
              <LikedCard key={p.productId} product={p} />
            ))}
          </ul>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
        </>
      )}
    </div>
  );
}
