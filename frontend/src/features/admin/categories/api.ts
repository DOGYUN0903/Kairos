"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { resolveError } from "@/api/errors";
import { sortTree, type CategoryNode } from "@/features/admin/categories/tree";
import type { CategoryValues } from "@/features/admin/categories/schemas";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

export function useCategoryTree() {
  return useQuery({
    queryKey: queryKeys.catalog.categories,
    queryFn: async () => sortTree(unwrap<CategoryNode[]>(await api.GET("/api/v1/categories"))),
  });
}

function useInvalidate() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: queryKeys.catalog.categories });
}

/** 이중 제출 가드: 처리 중 두 번째 호출은 무시한다(false). */
function useGuard() {
  const busy = useRef(false);
  return async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    if (busy.current) return undefined;
    busy.current = true;
    try {
      return await fn();
    } finally {
      busy.current = false;
    }
  };
}

export function useCreateCategory() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: async ({ parentId, values }: { parentId: number | null; values: CategoryValues }) =>
      unwrap<components["schemas"]["CreateCategoryResponse"]>(await api.POST("/api/v1/admin/categories", { body: { parentId, ...values } })),
    onSettled: invalidate,
  });
}

/** PUT 은 멱등. */
export function useUpdateCategory() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: async ({ id, values }: { id: number; values: CategoryValues }) => {
      await api.PUT("/api/v1/admin/categories/{categoryId}", { params: { path: { categoryId: id } }, body: values });
    },
    onSettled: invalidate,
  });
}

export function useDeleteCategory() {
  const invalidate = useInvalidate();
  const guard = useGuard();
  const m = useMutation({
    mutationFn: async (id: number) => {
      await api.DELETE("/api/v1/admin/categories/{categoryId}", { params: { path: { categoryId: id } } });
    },
    onSuccess: () => toast({ title: "카테고리를 삭제했습니다." }),
    onError: (e) => {
      // 409 CATEGORY_HAS_PRODUCTS / CATEGORY_HAS_CHILDREN 은 서버 메시지를 토스트로.
      const { message, traceId } = resolveError(e);
      toast({ variant: "destructive", title: message, ...(traceId ? { description: `문의 시 전달: ${traceId}` } : {}) });
    },
    onSettled: invalidate,
  });
  return { remove: (id: number) => guard(() => m.mutateAsync(id).then(() => true, () => false)), isPending: m.isPending };
}

/** 순서 변경: 한 요청(서버 한 트랜잭션)으로 같은 부모 아래 ID 순서를 보낸다. */
export function useReorderCategories() {
  const invalidate = useInvalidate();
  const guard = useGuard();
  const m = useMutation({
    mutationFn: async (v: { parentId: number | null; categoryIds: number[] }) => {
      await api.PUT("/api/v1/admin/categories/order", { body: v });
    },
    onError: (e) => {
      const { message } = resolveError(e);
      toast({ variant: "destructive", title: message });
    },
    onSettled: invalidate,
  });
  return { reorder: (v: { parentId: number | null; categoryIds: number[] }) => guard(() => m.mutateAsync(v).then(() => true, () => false)), isPending: m.isPending };
}
