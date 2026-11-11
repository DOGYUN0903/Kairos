import { Skeleton } from "@/components/ui/skeleton";
import type { ProductSummary } from "@/features/catalog/api";
import { ProductCard } from "./ProductCard";

export function ProductGrid({ products }: { products: ProductSummary[] }) {
  return (
    <ul className="mt-6 grid grid-cols-4 gap-x-5 gap-y-8">
      {products.map((p) => (
        <ProductCard key={p.productId} product={p} />
      ))}
    </ul>
  );
}

export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div aria-busy="true" aria-label="불러오는 중" className="mt-6 grid grid-cols-4 gap-x-5 gap-y-8">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="flex flex-col gap-2">
          <Skeleton className="aspect-square w-full" />
          <Skeleton className="h-3 w-16" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-24" />
        </div>
      ))}
    </div>
  );
}
