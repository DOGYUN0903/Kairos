import Link from "next/link";
import type { components } from "@/api/schema";

type CategoryNode = components["schemas"]["CategoryNode"];

const item = "block min-h-[44px] border-b-2 border-transparent px-3.5 py-3 font-medium hover:border-navy";

/** categoryId: undefined = 목록 화면이 아님(현재 표시 없음), null = 전체. */
export function CategoryNav({ categories, categoryId }: { categories: CategoryNode[]; categoryId?: number | null }) {
  return (
    <nav aria-label="카테고리" className="overflow-x-auto border-b border-border bg-bg">
      <div className="container">
        <ul className="flex min-w-max gap-1">
          <li>
            <span aria-disabled="true" className={`${item} cursor-not-allowed font-bold text-hot hover:border-transparent`}>
              핫딜 <span className="ml-1 rounded-sm border border-border px-1 text-[10px] text-text-3">준비 중</span>
            </span>
          </li>
          <li>
            <Link href="/products" aria-current={categoryId === null ? "page" : undefined} className={`${item} aria-[current=page]:border-navy aria-[current=page]:font-bold`}>
              전체
            </Link>
          </li>
          {categories.map((c) => (
            <li key={c.categoryId}>
              <Link
                href={`/products?categoryId=${c.categoryId}`}
                aria-current={categoryId === c.categoryId ? "page" : undefined}
                className={`${item} aria-[current=page]:border-navy aria-[current=page]:font-bold`}
              >
                {c.name}
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </nav>
  );
}
