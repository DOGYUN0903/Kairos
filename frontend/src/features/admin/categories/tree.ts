import type { components } from "@/api/schema";

export type CategoryNode = components["schemas"]["CategoryNode"];
export type OrderUpdate = { id: number; name: string; displayOrder: number };

const byOrder = (a: CategoryNode, b: CategoryNode) => a.displayOrder - b.displayOrder || a.categoryId - b.categoryId;

/** displayOrder(같으면 id) 오름차순으로 대분류·소분류를 정렬한 복사본. 서버가 2단계 트리를 주지만 순서는 화면에서도 보장한다. */
export function sortTree(nodes: CategoryNode[]): CategoryNode[] {
  return [...nodes].sort(byOrder).map((n) => ({ ...n, children: sortTree(n.children) }));
}

/** 새 형제의 기본 표시 순서: 마지막 + 1(없으면 0). */
export function nextDisplayOrder(siblings: CategoryNode[]): number {
  return siblings.reduce((max, n) => Math.max(max, n.displayOrder + 1), 0);
}

export function canMove(siblings: CategoryNode[], id: number, dir: "up" | "down"): boolean {
  const sorted = [...siblings].sort(byOrder);
  const i = sorted.findIndex((n) => n.categoryId === id);
  if (i < 0) return false;
  return dir === "up" ? i > 0 : i < sorted.length - 1;
}

/**
 * 형제 안에서 한 칸 옮긴 결과를 0..n-1 연속 순서로 정규화하고, 값이 바뀌는 행만 돌려준다.
 * 같은 displayOrder 가 섞여 있어도 보이는 순서(정렬 결과)가 그대로 저장된다. 못 옮기면 빈 배열.
 */
export function reorderUpdates(siblings: CategoryNode[], id: number, dir: "up" | "down"): OrderUpdate[] {
  if (!canMove(siblings, id, dir)) return [];
  const list = [...siblings].sort(byOrder);
  const i = list.findIndex((n) => n.categoryId === id);
  const j = dir === "up" ? i - 1 : i + 1;
  const a = list[i];
  const b = list[j];
  if (!a || !b) return [];
  list[i] = b;
  list[j] = a;
  return list.flatMap((n, idx) => (n.displayOrder === idx ? [] : [{ id: n.categoryId, name: n.name, displayOrder: idx }]));
}
