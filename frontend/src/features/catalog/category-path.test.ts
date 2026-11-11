import { describe, expect, it } from "vitest";
import { findCategoryPath, type CategoryNode } from "./category-path";

const tree: CategoryNode[] = [
  { categoryId: 1, name: "상의", displayOrder: 1, children: [{ categoryId: 11, name: "티셔츠", displayOrder: 1, children: [] }] },
  { categoryId: 2, name: "바지", displayOrder: 2, children: [] },
];

describe("findCategoryPath", () => {
  it("소분류는_대분류부터의_경로를_돌려준다", () => {
    expect(findCategoryPath(tree, 11).map((c) => c.name)).toEqual(["상의", "티셔츠"]);
  });
  it("대분류만_찾으면_하나", () => {
    expect(findCategoryPath(tree, 2).map((c) => c.name)).toEqual(["바지"]);
  });
  it("없으면_빈_배열", () => {
    expect(findCategoryPath(tree, 99)).toEqual([]);
  });
});
