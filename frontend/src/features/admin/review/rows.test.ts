import { describe, expect, it } from "vitest";
import { canReview, mergeOldest, nextSelection, toQueueRow, type QueueRow } from "./rows";

const row = (tab: QueueRow["tab"], id: number, at: string) =>
  ({ tab, id, name: "n", meta: "", sellerName: null, at, status: "PENDING", productId: null, raw: {} }) as unknown as QueueRow;

describe("toQueueRow", () => {
  it("변경_요청_행은_requestedAt과_sellerName을_쓴다", () => {
    const r = toQueueRow({
      tab: "change-requests",
      raw: { changeRequestId: 301, productId: 1024, sellerId: 5, name: "오버핏 티셔츠", originalPrice: 45000, status: "PENDING", requestedAt: "2026-11-07T14:20:00", sellerName: "판매자 A 주식회사" },
    });
    expect(r).toMatchObject({ id: 301, productId: 1024, at: "2026-11-07T14:20:00", sellerName: "판매자 A 주식회사", meta: "#CR-301 · 상품 #1024" });
  });

  it("상품_행은_createdAt과_브랜드_메타를_쓴다", () => {
    const r = toQueueRow({
      tab: "products",
      raw: { productId: 1018, sellerId: 2, brandName: "브랜드 B", name: "티셔츠", thumbnailUrl: "/files/1", originalPrice: 1000, salePrice: 900, status: "PENDING_REVIEW", createdAt: "2026-11-03T09:00:00.123456", sellerName: "판매자 B" },
    });
    expect(r).toMatchObject({ id: 1018, at: "2026-11-03T09:00:00.123456", meta: "#1018 · 브랜드 B", sellerName: "판매자 B" });
  });

  it("브랜드_행은_판매자명이_없고_판매자_행은_회사명이_이름이다", () => {
    const b = toQueueRow({ tab: "brands", raw: { brandId: 4, sellerId: 2, name: "노드 어패럴", englishName: "NODE APPAREL", status: "PENDING", createdAt: "2026-11-04T10:00:00" } });
    expect(b).toMatchObject({ id: 4, sellerName: null, meta: "NODE APPAREL" });
    const s = toQueueRow({
      tab: "sellers",
      raw: { sellerId: 9, memberId: 1, companyName: "주식회사 무드웨어", businessNumber: "2148611223", representativeName: "a", phone: "p", bank: "b", accountNumber: "1", holder: "h", commissionRateBp: 0, status: "PENDING", createdAt: "2026-11-05T10:00:00" },
    });
    expect(s).toMatchObject({ id: 9, name: "주식회사 무드웨어", meta: "사업자 2148611223", at: "2026-11-05T10:00:00" });
  });
});

describe("canReview", () => {
  it("검수_대기_상태에서만_승인_반려할_수_있다", () => {
    expect(canReview("sellers", "PENDING")).toBe(true);
    expect(canReview("sellers", "APPROVED")).toBe(false);
    expect(canReview("products", "PENDING_REVIEW")).toBe(true);
    expect(canReview("products", "ON_SALE")).toBe(false);
    expect(canReview("change-requests", "WITHDRAWN")).toBe(false);
    expect(canReview("brands", "REJECTED")).toBe(false);
  });
});

describe("mergeOldest", () => {
  it("요청일_오름차순으로_합쳐_앞_n개만_남긴다", () => {
    const merged = mergeOldest(
      [row("products", 1, "2026-11-08T00:00:00"), row("brands", 2, "2026-11-04T00:00:00"), row("sellers", 3, "2026-11-05T00:00:00"), row("change-requests", 4, "2026-11-07T00:00:00"), row("products", 5, "2026-11-03T00:00:00"), row("brands", 6, "2026-11-09T00:00:00")],
      5,
    );
    expect(merged.map((r) => r.id)).toEqual([5, 2, 3, 4, 1]);
  });
});

describe("nextSelection", () => {
  it("처리한_행의_다음_행을_고르고_마지막이면_이전_행_하나뿐이면_null", () => {
    const rows = [row("sellers", 1, "a"), row("sellers", 2, "b"), row("sellers", 3, "c")];
    expect(nextSelection(rows, 2)).toBe(3);
    expect(nextSelection(rows, 3)).toBe(2);
    expect(nextSelection([row("sellers", 1, "a")], 1)).toBeNull();
  });
});
