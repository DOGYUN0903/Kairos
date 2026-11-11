import type { components } from "@/api/schema";

export type CategoryNode = components["schemas"]["CategoryNode"];

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
 * 형제 안에서 한 칸 옮긴 뒤의 ID 순서(보이는 순서 기준). 못 옮기면 null.
 * 서버가 이 순서로 0.. 연속 값을 다시 매긴다(같은 displayOrder 가 섞여 있어도 보이는 순서가 저장된다).
 */
export function reorderedIds(siblings: CategoryNode[], id: number, dir: "up" | "down"): number[] | null {
  if (!canMove(siblings, id, dir)) return null;
  const ids = [...siblings].sort(byOrder).map((n) => n.categoryId);
  const i = ids.indexOf(id);
  const j = dir === "up" ? i - 1 : i + 1;
  const a = ids[i];
  const b = ids[j];
  if (a === undefined || b === undefined) return null;
  ids[i] = b;
  ids[j] = a;
  return ids;
}
