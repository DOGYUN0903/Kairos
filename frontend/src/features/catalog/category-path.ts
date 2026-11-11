import type { components } from "@/api/schema";

export type CategoryNode = components["schemas"]["CategoryNode"];

/** 루트부터 대상까지의 경로. 없으면 빈 배열. */
export function findCategoryPath(tree: CategoryNode[], categoryId: number): CategoryNode[] {
  for (const node of tree) {
    if (node.categoryId === categoryId) return [node];
    const rest = findCategoryPath(node.children, categoryId);
    if (rest.length > 0) return [node, ...rest];
  }
  return [];
}
