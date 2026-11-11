import { Heart, Star } from "lucide-react";
import Link from "next/link";
import { Money } from "@/components/common/Money";
import type { ProductSummary } from "@/features/catalog/api";
import { cn } from "@/lib/utils";

const count = new Intl.NumberFormat("ko-KR");

/** 좋아요 수·별점은 표시 전용이다(카드에서는 좋아요를 누를 수 없다). */
export function ProductCard({ product }: { product: ProductSummary }) {
  const href = `/products/${product.productId}`;
  return (
    <li className={cn("relative", product.soldOut && "text-text-3")}>
      <Link href={href} className="block" aria-label={`${product.brandName} ${product.name}${product.soldOut ? " (품절)" : ""}`}>
        <div className="relative aspect-square overflow-hidden rounded bg-surface-2">
          {product.thumbnailUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
            <img src={product.thumbnailUrl} alt={product.name} loading="lazy" className="h-full w-full object-cover" />
          ) : (
            <div className="grid h-full w-full place-items-center text-xs text-text-3">이미지 없음</div>
          )}
          {product.soldOut && (
            <div className="absolute inset-0 grid place-items-center rounded bg-navy/55 text-[15px] font-bold text-white">품절</div>
          )}
        </div>
      </Link>
      <div className="pt-2.5">
        <p className={cn("text-xs font-bold", product.soldOut ? "text-text-3" : "text-foreground")}>
          <Link href={`/brands/${product.brandId}`} className="hover:underline">
            {product.brandName}
          </Link>
        </p>
        <p className="mt-0.5 line-clamp-2 text-sm text-text-2">
          <Link href={href} className={cn("hover:underline", product.soldOut && "text-text-3")}>
            {product.name}
          </Link>
        </p>
        <div className="mt-1.5 flex flex-wrap items-baseline gap-1.5">
          {product.discountRate > 0 && <span className="font-bold text-discount">{product.discountRate}%</span>}
          <Money value={product.salePrice} className={cn("text-[15px] font-bold", product.soldOut && "text-text-3")} />
          {product.discountRate > 0 && <Money value={product.originalPrice} className="text-xs text-text-3 line-through" />}
        </div>
        <div className="mt-1.5 flex items-center gap-3 text-xs text-text-3">
          <span className="flex items-center gap-1">
            <Heart className="h-3 w-3" aria-hidden />
            <span>좋아요 {count.format(product.likeCount)}</span>
          </span>
          {product.reviewCount > 0 && (
            <span className="flex items-center gap-1">
              <Star className="h-3 w-3" aria-hidden />
              <span>
                {product.averageRating.toFixed(1)} ({count.format(product.reviewCount)})
              </span>
            </span>
          )}
        </div>
      </div>
    </li>
  );
}
