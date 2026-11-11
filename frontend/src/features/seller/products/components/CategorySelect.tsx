"use client";

import { useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { useCategories, type CategoryNode } from "@/features/catalog/api";

/** 대분류 → 소분류 2단 셀렉트. 값은 소분류 ID 만(CATEGORY_NOT_LEAF 방지). 고르지 않았으면 NaN. */
export function CategorySelect({
  value,
  onChange,
  onBlur,
  error,
}: {
  value: number;
  onChange: (categoryId: number) => void;
  onBlur?: () => void;
  error?: string;
}) {
  const q = useCategories();
  const tree = q.data ?? [];
  const parentOfValue = tree.find((t) => t.children.some((c) => c.categoryId === value))?.categoryId;
  const [picked, setPicked] = useState<number | undefined>(undefined);
  const parentId = parentOfValue ?? picked;
  const children: CategoryNode[] = tree.find((t) => t.categoryId === parentId)?.children ?? [];

  return (
    <div className="field">
      <span id="p-cat-label" className="text-[13px] font-medium">
        카테고리(소분류)<span className="text-danger" aria-hidden>*</span>
      </span>
      {q.isPending ? (
        <Skeleton className="h-11 w-full" aria-label="카테고리 불러오는 중" />
      ) : q.isError ? (
        <p role="alert" className="text-sm text-danger">
          카테고리를 불러오지 못했습니다.{" "}
          <button type="button" className="underline" onClick={() => void q.refetch()}>
            다시 시도
          </button>
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          <select
            id="p-cat-top"
            className="input"
            aria-label="대분류"
            value={parentId ?? ""}
            onChange={(e) => {
              setPicked(e.target.value === "" ? undefined : Number(e.target.value));
              onChange(Number.NaN);
            }}
          >
            <option value="">대분류 선택</option>
            {tree.map((t) => (
              <option key={t.categoryId} value={t.categoryId}>
                {t.name}
              </option>
            ))}
          </select>
          <select
            id="p-category"
            className="input"
            aria-label="소분류"
            disabled={parentId === undefined || children.length === 0}
            value={Number.isNaN(value) ? "" : value}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? "p-cat-err" : undefined}
            onBlur={onBlur}
            onChange={(e) => onChange(e.target.value === "" ? Number.NaN : Number(e.target.value))}
          >
            <option value="">{parentId !== undefined && children.length === 0 ? "소분류가 없습니다" : "소분류 선택"}</option>
            {children.map((c) => (
              <option key={c.categoryId} value={c.categoryId}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
      )}
      {error && (
        <span id="p-cat-err" className="field-error">
          {error}
        </span>
      )}
    </div>
  );
}
