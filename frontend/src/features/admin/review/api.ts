"use client";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { ApiError, api, unwrap } from "@/api/client";
import { resolveError } from "@/api/errors";
import type { components } from "@/api/schema";
import { toQueueRow, type AdminProductSummary, type BrandDetail, type ChangeRequestSummary, type QueueRow, type SellerDetail } from "@/features/admin/review/rows";
import { PENDING_STATUS, REVIEW_PAGE_SIZE, REVIEW_TABS, type ReviewSort, type ReviewTab } from "@/features/admin/review/review-params";
import type { ChangeRequestDetail } from "@/features/admin/review/diff";
import type { SellerProductDetail } from "@/features/seller/products/api";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

type Page<T> = Omit<components["schemas"]["PageResponse"], "content"> & { content: T[] };

export type QueuePage = Omit<components["schemas"]["PageResponse"], "content"> & { content: QueueRow[] };
export type QueueQuery = { sort: ReviewSort; page: number; size: number };

/** 한 큐를 읽어 QueueRow 로 맞춘다. 상태는 항상 검수 대기. */
export async function fetchQueue(tab: ReviewTab, q: QueueQuery): Promise<QueuePage> {
  const query = { status: PENDING_STATUS[tab], sort: q.sort, page: q.page, size: q.size };
  switch (tab) {
    case "sellers": {
      const p = unwrap<Page<SellerDetail>>(await api.GET("/api/v1/admin/sellers", { params: { query: { ...query, status: "PENDING" } } }));
      return { ...p, content: p.content.map((raw) => toQueueRow({ tab, raw })) };
    }
    case "brands": {
      const p = unwrap<Page<BrandDetail>>(await api.GET("/api/v1/admin/brands", { params: { query: { ...query, status: "PENDING" } } }));
      return { ...p, content: p.content.map((raw) => toQueueRow({ tab, raw })) };
    }
    case "products": {
      const p = unwrap<Page<AdminProductSummary>>(await api.GET("/api/v1/admin/products", { params: { query: { ...query, status: "PENDING_REVIEW" } } }));
      return { ...p, content: p.content.map((raw) => toQueueRow({ tab, raw })) };
    }
    case "change-requests": {
      const p = unwrap<Page<ChangeRequestSummary>>(await api.GET("/api/v1/admin/change-requests", { params: { query: { ...query, status: "PENDING" } } }));
      return { ...p, content: p.content.map((raw) => toQueueRow({ tab, raw })) };
    }
  }
}

export function useQueue(tab: ReviewTab, params: { sort: ReviewSort; page: number }) {
  const q: QueueQuery = { ...params, size: REVIEW_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.admin.queue(tab, { status: PENDING_STATUS[tab], ...q }),
    queryFn: () => fetchQueue(tab, q),
    // 같은 탭의 페이지·정렬 전환에서만 이전 목록을 유지한다(다른 탭의 행이 잠깐 보이면 안 된다).
    placeholderData: (prev: QueuePage | undefined) => (prev?.content[0] && prev.content[0].tab !== tab ? undefined : prev),
  });
}

const COUNT_REFETCH_MS = 30_000;

const countOptions = (tab: ReviewTab) => ({
  queryKey: queryKeys.admin.queueCount(tab),
  queryFn: async () => (await fetchQueue(tab, { sort: "OLDEST", page: 0, size: 1 })).totalElements,
  refetchInterval: COUNT_REFETCH_MS,
});

/** 대기 건수: size=1 로 읽은 totalElements. 30초마다 새로 읽는다. */
export function useQueueCount(tab: ReviewTab) {
  return useQuery(countOptions(tab));
}

export function useQueueCounts() {
  const results = useQueries({ queries: REVIEW_TABS.map(countOptions) });
  const counts = Object.fromEntries(REVIEW_TABS.map((t, i) => [t, results[i]?.data])) as Record<ReviewTab, number | undefined>;
  const known = REVIEW_TABS.map((t) => counts[t]);
  return {
    counts,
    total: known.every((v) => v !== undefined) ? known.reduce<number>((sum, v) => sum + (v ?? 0), 0) : undefined,
    isPending: results.some((r) => r.isPending),
    isError: results.some((r) => r.isError),
    error: results.find((r) => r.isError)?.error,
    refetch: () => results.forEach((r) => void r.refetch()),
  };
}

/** 대시보드 "오래된 대기 건": 큐마다 OLDEST 앞 2행. */
export function useOldestRows() {
  const results = useQueries({
    queries: REVIEW_TABS.map((tab) => ({
      queryKey: queryKeys.admin.queue(tab, { status: PENDING_STATUS[tab], sort: "OLDEST", page: 0, size: 2 }),
      queryFn: () => fetchQueue(tab, { sort: "OLDEST", page: 0, size: 2 }),
      refetchInterval: COUNT_REFETCH_MS,
    })),
  });
  return {
    rows: results.flatMap((r) => r.data?.content ?? []),
    isPending: results.some((r) => r.isPending),
    isError: results.some((r) => r.isError),
    error: results.find((r) => r.isError)?.error,
    refetch: () => results.forEach((r) => void r.refetch()),
  };
}

export function useAdminProduct(id: number | null) {
  return useQuery({
    queryKey: queryKeys.admin.product(id ?? 0),
    queryFn: async () => unwrap<SellerProductDetail>(await api.GET("/api/v1/admin/products/{productId}", { params: { path: { productId: id ?? 0 } } })),
    enabled: id !== null,
  });
}

export function useAdminChangeRequest(id: number | null) {
  return useQuery({
    queryKey: queryKeys.admin.changeRequest(id ?? 0),
    queryFn: async () =>
      unwrap<ChangeRequestDetail>(await api.GET("/api/v1/admin/change-requests/{changeRequestId}", { params: { path: { changeRequestId: id ?? 0 } } })),
    enabled: id !== null,
  });
}

export type ActionVars = { id: number; reason?: string; commissionRateBp?: number };

async function run(tab: ReviewTab, kind: "approve" | "reject", v: ActionVars): Promise<void> {
  const reason = v.reason ?? "";
  if (kind === "approve") {
    switch (tab) {
      case "sellers":
        await api.POST("/api/v1/admin/sellers/{sellerId}/approve", { params: { path: { sellerId: v.id } }, body: { commissionRateBp: v.commissionRateBp ?? 0 } });
        return;
      case "brands":
        await api.POST("/api/v1/admin/brands/{brandId}/approve", { params: { path: { brandId: v.id } } });
        return;
      case "products":
        await api.POST("/api/v1/admin/products/{productId}/approve", { params: { path: { productId: v.id } } });
        return;
      case "change-requests":
        await api.POST("/api/v1/admin/change-requests/{changeRequestId}/approve", { params: { path: { changeRequestId: v.id } } });
        return;
    }
  }
  switch (tab) {
    case "sellers":
      await api.POST("/api/v1/admin/sellers/{sellerId}/reject", { params: { path: { sellerId: v.id } }, body: { reason } });
      return;
    case "brands":
      await api.POST("/api/v1/admin/brands/{brandId}/reject", { params: { path: { brandId: v.id } }, body: { reason } });
      return;
    case "products":
      await api.POST("/api/v1/admin/products/{productId}/reject", { params: { path: { productId: v.id } }, body: { reason } });
      return;
    case "change-requests":
      await api.POST("/api/v1/admin/change-requests/{changeRequestId}/reject", { params: { path: { changeRequestId: v.id } }, body: { reason } });
      return;
  }
}

const TRANSITION_CODES = new Set(["SELLER_INVALID_STATE_TRANSITION", "BRAND_INVALID_STATE_TRANSITION", "PRODUCT_INVALID_STATE_TRANSITION", "CHANGE_REQUEST_INVALID_STATE_TRANSITION"]);

/**
 * 승인·반려 뮤테이션. 처리 중에는 두 번째 호출을 무시하고(이중 제출 방지),
 * 이미 처리된 건(409 상태 전이)은 서버 상태가 목표와 같으므로 오류가 아니라 안내로 본다.
 * 끝나면(성공·실패 모두) 큐·대기 건수·상세를 다시 읽는다. 승인/반려 API 는 상태 전이로 멱등하다.
 */
function useReviewAction(tab: ReviewTab, kind: "approve" | "reject", onDone?: (id: number) => void) {
  const qc = useQueryClient();
  const inFlight = useRef(false);
  const mutation = useMutation({
    mutationFn: (v: ActionVars) => run(tab, kind, v),
    onSuccess: (_d, v) => {
      toast({ title: kind === "approve" ? "승인했습니다." : "반려했습니다." });
      onDone?.(v.id);
    },
    onError: (e, v) => {
      if (e instanceof ApiError && TRANSITION_CODES.has(e.code)) {
        toast({ title: "이미 처리된 건입니다. 목록을 새로 불러옵니다." });
        onDone?.(v.id);
        return;
      }
      const { message, traceId } = resolveError(e);
      toast({ variant: "destructive", title: message, ...(traceId ? { description: `문의 시 전달: ${traceId}` } : {}) });
    },
    onSettled: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: ["admin", "queue"] }),
        qc.invalidateQueries({ queryKey: ["admin", "queueCount"] }),
        qc.invalidateQueries({ queryKey: ["admin", "changeRequest"] }),
        qc.invalidateQueries({ queryKey: ["admin", "product"] }),
      ]),
  });
  const submit = async (v: ActionVars): Promise<boolean> => {
    if (inFlight.current) return false;
    inFlight.current = true;
    try {
      await mutation.mutateAsync(v);
      return true;
    } catch {
      return false;
    } finally {
      inFlight.current = false;
    }
  };
  return { submit, isPending: mutation.isPending };
}

/** 성공 시 onDone(처리한 id): 호출자가 다음 행을 선택한다. */
export const useApprove = (tab: ReviewTab, onDone?: (id: number) => void) => useReviewAction(tab, "approve", onDone);
export const useReject = (tab: ReviewTab, onDone?: (id: number) => void) => useReviewAction(tab, "reject", onDone);
