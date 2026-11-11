import { describe, expect, it } from "vitest";
import { canMove, nextDisplayOrder, reorderedIds, sortTree, type CategoryNode } from "./tree";

const n = (categoryId: number, displayOrder: number, children: CategoryNode[] = []): CategoryNode => ({ categoryId, name: `c${categoryId}`, displayOrder, children });

describe("category tree", () => {
  it("대분류와_소분류를_표시_순서_다음_id로_정렬한다", () => {
    const sorted = sortTree([n(3, 2), n(1, 0, [n(12, 1), n(11, 0)]), n(2, 0)]);
    expect(sorted.map((x) => x.categoryId)).toEqual([1, 2, 3]);
    expect(sorted[0]?.children.map((x) => x.categoryId)).toEqual([11, 12]);
  });
  it("새_형제의_순서는_마지막_더하기_1이고_비면_0이다", () => {
    expect(nextDisplayOrder([])).toBe(0);
    expect(nextDisplayOrder([n(1, 0), n(2, 4)])).toBe(5);
  });
  it("첫_행은_위로_마지막_행은_아래로_못_옮긴다", () => {
    const s = [n(1, 0), n(2, 1)];
    expect(canMove(s, 1, "up")).toBe(false);
    expect(canMove(s, 2, "down")).toBe(false);
    expect(canMove(s, 2, "up")).toBe(true);
    expect(reorderedIds(s, 1, "up")).toBeNull();
  });
  it("위로_옮기면_보이는_순서에서_두_ID가_바뀐다", () => {
    expect(reorderedIds([n(1, 0), n(2, 1), n(3, 2)], 3, "up")).toEqual([1, 3, 2]);
  });
  it("같은_순서값이_섞여_있어도_보이는_순서_기준으로_옮긴다", () => {
    expect(reorderedIds([n(1, 5), n(2, 5), n(3, 5)], 2, "up")).toEqual([2, 1, 3]);
  });
  it("모르는_id는_옮기지_않는다", () => {
    expect(reorderedIds([n(1, 0)], 99, "down")).toBeNull();
  });
});
