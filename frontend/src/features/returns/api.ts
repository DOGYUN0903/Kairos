"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ApiError, api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type ReturnPreview = components["schemas"]["ReturnPreview"];
export type ReturnReason = ReturnPreview["reason"];
export type RequestReturnBody = components["schemas"]["RequestReturnRequest"];

/**
 * 반품 환불 미리보기(예상값). 사유가 있을 때만 읽고, 실패해도 다시 시도하지 않으며, 캐시하지 않는다(금액은 항상 새로).
 * 미리보기는 안내일 뿐이라 실패가 요청을 막지는 않는다(막는 경우는 previewBlocksRequest).
 */
export function useReturnPreview(orderId: number, orderItemId: number, reason: ReturnReason | null) {
  return useQuery({
    queryKey: queryKeys.returns.preview(orderId, orderItemId, reason ?? ""),
    queryFn: async () =>
      unwrap<ReturnPreview>(
        await api.GET("/api/v1/orders/{orderId}/items/{orderItemId}/return-preview", {
          params: { path: { orderId, orderItemId }, query: { reason: reason as ReturnReason } },
        }),
      ),
    enabled: reason != null,
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });
}

const BLOCKING = new Set(["RETURN_NOT_ELIGIBLE", "RETURN_WINDOW_EXPIRED", "RETURN_ALREADY_REQUESTED"]);

/** 미리보기 실패가 반품 요청을 막아야 하는가: 서버가 자격이 없다고 답한 세 코드만. 네트워크·5xx 등은 막지 않는다. */
export function previewBlocksRequest(error: unknown): boolean {
  return error instanceof ApiError && BLOCKING.has(error.code);
}

/** 반품 요청. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다). */
export async function requestReturn(orderId: number, orderItemId: number, body: RequestReturnBody, key: string) {
  return unwrap<components["schemas"]["ReturnRequestResponse"]>(
    await api.POST("/api/v1/orders/{orderId}/items/{orderItemId}/returns", {
      params: { path: { orderId, orderItemId }, header: { [IDEMPOTENCY_HEADER]: key } },
      body,
    }),
  );
}

/** 반품 철회. 키 없음(서버가 멱등). 실패는 던진다. */
export async function withdrawReturn(returnId: number): Promise<void> {
  unwrap<unknown>(await api.DELETE("/api/v1/returns/{returnId}", { params: { path: { returnId } } }));
}

export type ReturnRequestResponse = components["schemas"]["ReturnRequestResponse"];
export type ReturnPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: ReturnRequestResponse[] };

export const RETURN_PAGE_SIZE = 10;

/** 내 반품 목록(최신 요청순). page 는 0부터. */
export function useMyReturns(page: number) {
  const query = { page, size: RETURN_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.returns.mine(query),
    queryFn: async () => unwrap<ReturnPage>(await api.GET("/api/v1/returns/me", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 반품 상세. 상태가 바뀌므로 마운트마다 다시 읽는다. refetchInterval 은 환불 확인 중의 재조회에 쓴다. */
export function useMyReturn(returnId: number, opts?: { refetchInterval?: number | false }) {
  return useQuery({
    queryKey: queryKeys.returns.detail(returnId),
    queryFn: async () => unwrap<ReturnRequestResponse>(await api.GET("/api/v1/returns/{returnId}", { params: { path: { returnId } } })),
    enabled: Number.isSafeInteger(returnId) && returnId > 0,
    staleTime: 0,
    refetchInterval: opts?.refetchInterval ?? false,
  });
}
