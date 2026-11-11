"use client";

import { ChevronDown, ChevronUp } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { findCategoryPath, type CategoryNode } from "@/features/catalog/category-path";

/** hrefFor: 카테고리 선택 링크(필터·정렬은 호출자가 유지한다). categoryId 가 undefined 면 카테고리 해제. */
export function CategoryTree({
  categories,
  categoryId,
  hrefFor,
}: {
  categories: CategoryNode[];
  categoryId: number | undefined;
  hrefFor: (categoryId: number | undefined) => string;
}) {
  const activePath = categoryId === undefined ? [] : findCategoryPath(categories, categoryId);
  const [open, setOpen] = useState<Set<number>>(() => new Set(activePath.slice(0, 1).map((c) => c.categoryId)));
  const isOpen = (id: number) => open.has(id) || activePath[0]?.categoryId === id;
  const toggle = (id: number) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const link = "block min-h-[36px] leading-9 text-text-2 aria-[current=page]:font-bold aria-[current=page]:text-navy";

  return (
    <ul>
      <li>
        <Link href={hrefFor(undefined)} aria-current={categoryId === undefined ? "page" : undefined} className={link}>
          전체
        </Link>
      </li>
      {categories.map((c) => {
        const expanded = c.children.length > 0 && isOpen(c.categoryId);
        return (
          <li key={c.categoryId}>
            {c.children.length > 0 ? (
              <button
                type="button"
                aria-expanded={expanded}
                onClick={() => toggle(c.categoryId)}
                className="flex min-h-[40px] w-full items-center justify-between text-left font-medium"
              >
                {c.name}
                {expanded ? <ChevronUp className="h-4 w-4" aria-hidden /> : <ChevronDown className="h-4 w-4" aria-hidden />}
              </button>
            ) : (
              <Link href={hrefFor(c.categoryId)} aria-current={categoryId === c.categoryId ? "page" : undefined} className={`${link} font-medium`}>
                {c.name}
              </Link>
            )}
            {expanded && (
              <ul className="pl-3">
                <li>
                  <Link href={hrefFor(c.categoryId)} aria-current={categoryId === c.categoryId ? "page" : undefined} className={link}>
                    {c.name} 전체
                  </Link>
                </li>
                {c.children.map((child) => (
                  <li key={child.categoryId}>
                    <Link href={hrefFor(child.categoryId)} aria-current={categoryId === child.categoryId ? "page" : undefined} className={link}>
                      {child.name}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </li>
        );
      })}
    </ul>
  );
}
