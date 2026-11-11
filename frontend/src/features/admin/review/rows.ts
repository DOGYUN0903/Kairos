import type { components } from "@/api/schema";
import { PENDING_STATUS, REVIEW_TABS, type ReviewTab } from "@/features/admin/review/review-params";

export type SellerDetail = components["schemas"]["SellerDetail"];
export type BrandDetail = components["schemas"]["BrandDetail"];
export type AdminProductSummary = components["schemas"]["AdminProductSummary"];
export type ChangeRequestSummary = components["schemas"]["ChangeRequestSummary"];

type Raw =
  | { tab: "sellers"; raw: SellerDetail }
  | { tab: "brands"; raw: BrandDetail }
  | { tab: "products"; raw: AdminProductSummary }
  | { tab: "change-requests"; raw: ChangeRequestSummary };

/** 네 큐의 행을 한 모양으로 맞춘 것. 표·대시보드가 같이 쓴다. */
type QueueRowBase = {
  /** 큐 안에서의 식별자(sellerId·brandId·productId·changeRequestId). */
  id: number;
  name: string;
  /** 이름 아래 보조 줄. */
  meta: string;
  /** 판매자 열. 브랜드 행은 판매자명이 없어 null. */
  sellerName: string | null;
  /** 요청 일시(createdAt 또는 requestedAt). */
  at: string;
  status: string;
  /** 변경 요청이 가리키는 상품 ID. */
  productId: number | null;
};

/** `tab` 으로 좁히면 `raw` 가 그 큐의 원본 행이다(상세 카드가 쓴다). */
export type QueueRow = QueueRowBase & Raw;

export function toQueueRow(input: Raw): QueueRow {
  switch (input.tab) {
    case "sellers": {
      const r = input.raw;
      return { tab: "sellers", raw: r, id: r.sellerId, name: r.companyName, meta: `사업자 ${r.businessNumber}`, sellerName: r.companyName, at: r.createdAt, status: r.status, productId: null };
    }
    case "brands": {
      const r = input.raw;
      return { tab: "brands", raw: r, id: r.brandId, name: r.name, meta: r.englishName ?? "", sellerName: null, at: r.createdAt, status: r.status, productId: null };
    }
    case "products": {
      const r = input.raw;
      return { tab: "products", raw: r, id: r.productId, name: r.name, meta: `#${r.productId} · ${r.brandName}`, sellerName: r.sellerName, at: r.createdAt, status: r.status, productId: r.productId };
    }
    case "change-requests": {
      const r = input.raw;
      return { tab: "change-requests", raw: r, id: r.changeRequestId, name: r.name, meta: `#CR-${r.changeRequestId} · 상품 #${r.productId}`, sellerName: r.sellerName, at: r.requestedAt, status: r.status, productId: r.productId };
    }
  }
}

/** 승인·반려가 가능한 상태인가(검수 대기뿐). */
export function canReview(tab: ReviewTab, status: string): boolean {
  return status === PENDING_STATUS[tab];
}

const tabOrder = new Map(REVIEW_TABS.map((t, i) => [t, i]));

/** 네 큐의 앞쪽 행을 요청 일시 오름차순(같으면 큐 순서·ID 순)으로 합쳐 앞 n개. */
export function mergeOldest(rows: QueueRow[], n: number): QueueRow[] {
  return [...rows]
    .sort((a, b) => (a.at < b.at ? -1 : a.at > b.at ? 1 : (tabOrder.get(a.tab) ?? 0) - (tabOrder.get(b.tab) ?? 0) || a.id - b.id))
    .slice(0, n);
}

/** 처리한 행 다음 행(없으면 이전 행)의 id. 둘 다 없으면 null. */
export function nextSelection(rows: QueueRow[], removedId: number): number | null {
  const i = rows.findIndex((r) => r.id === removedId);
  if (i < 0) return null;
  return rows[i + 1]?.id ?? rows[i - 1]?.id ?? null;
}
