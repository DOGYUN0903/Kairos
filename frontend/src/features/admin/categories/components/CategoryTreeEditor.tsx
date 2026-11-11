"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useCategoryTree, useDeleteCategory, useReorderCategories } from "@/features/admin/categories/api";
import { CategoryDialog, type CategoryDialogState } from "@/features/admin/categories/components/CategoryDialog";
import { canMove, nextDisplayOrder, reorderUpdates, type CategoryNode } from "@/features/admin/categories/tree";

type Target = { node: CategoryNode; isRoot: boolean };

export function CategoryTreeEditor() {
  const tree = useCategoryTree();
  const del = useDeleteCategory();
  const reorder = useReorderCategories();
  const [dialog, setDialog] = useState<CategoryDialogState | null>(null);
  const [target, setTarget] = useState<Target | null>(null);
  const [collapsed, setCollapsed] = useState<ReadonlySet<number>>(new Set());

  const roots = tree.data ?? [];
  const toggle = (id: number) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  const move = (siblings: CategoryNode[], id: number, dir: "up" | "down") => void reorder.reorder(reorderUpdates(siblings, id, dir));
  const confirmDelete = async () => {
    if (!target) return;
    const id = target.node.categoryId;
    setTarget(null);
    await del.remove(id);
  };
  const busy = reorder.isPending || del.isPending;

  return (
    <section className="card" aria-labelledby="cat-h">
      <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
        <h2 id="cat-h" className="text-[15px] font-bold">카테고리</h2>
        <button type="button" className="btn btn-primary btn-sm" onClick={() => setDialog({ mode: "create", parentId: null, initial: { name: "", displayOrder: nextDisplayOrder(roots) } })}>
          대분류 추가
        </button>
      </div>
      {tree.isError ? (
        <ErrorState error={tree.error} onRetry={() => void tree.refetch()} />
      ) : tree.isPending ? (
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
          {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-11 w-full" />)}
        </div>
      ) : roots.length === 0 ? (
        <EmptyState message="등록된 카테고리가 없습니다" action={{ label: "대분류 추가", onClick: () => setDialog({ mode: "create", parentId: null, initial: { name: "", displayOrder: 0 } }) }} />
      ) : (
        <ul className="divide-y divide-border" aria-label="대분류 목록">
          {roots.map((root) => {
            const open = !collapsed.has(root.categoryId);
            return (
              <li key={root.categoryId}>
                <Row
                  node={root}
                  level="root"
                  siblings={roots}
                  busy={busy}
                  expanded={open}
                  onToggle={() => toggle(root.categoryId)}
                  onMove={(dir) => move(roots, root.categoryId, dir)}
                  onEdit={() => setDialog({ mode: "edit", id: root.categoryId, initial: { name: root.name, displayOrder: root.displayOrder } })}
                  onDelete={() => setTarget({ node: root, isRoot: true })}
                  onAddChild={() => setDialog({ mode: "create", parentId: root.categoryId, parentName: root.name, initial: { name: "", displayOrder: nextDisplayOrder(root.children) } })}
                />
                {open && root.children.length > 0 && (
                  <ul className="border-t border-border bg-surface" aria-label={`${root.name} 소분류`}>
                    {root.children.map((child) => (
                      <li key={child.categoryId} className="border-b border-border last:border-b-0">
                        <Row
                          node={child}
                          level="child"
                          siblings={root.children}
                          busy={busy}
                          onMove={(dir) => move(root.children, child.categoryId, dir)}
                          onEdit={() => setDialog({ mode: "edit", id: child.categoryId, initial: { name: child.name, displayOrder: child.displayOrder } })}
                          onDelete={() => setTarget({ node: child, isRoot: false })}
                        />
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <CategoryDialog state={dialog} onClose={() => setDialog(null)} />
      <ConfirmDialog
        open={target !== null}
        title={target ? `'${target.node.name}' 삭제` : "삭제"}
        description={
          target?.isRoot
            ? "삭제하면 상품 등록의 카테고리 선택에서 사라집니다. 소분류나 상품이 있으면 삭제할 수 없습니다."
            : "삭제하면 상품 등록의 카테고리 선택에서 사라집니다. 상품이 있으면 삭제할 수 없습니다."
        }
        confirmLabel="삭제"
        destructive
        onConfirm={() => void confirmDelete()}
        onCancel={() => setTarget(null)}
      />
    </section>
  );
}

function Row({
  node,
  level,
  siblings,
  busy,
  expanded,
  onToggle,
  onMove,
  onEdit,
  onDelete,
  onAddChild,
}: {
  node: CategoryNode;
  level: "root" | "child";
  siblings: CategoryNode[];
  busy: boolean;
  expanded?: boolean;
  onToggle?: () => void;
  onMove: (dir: "up" | "down") => void;
  onEdit: () => void;
  onDelete: () => void;
  onAddChild?: () => void;
}) {
  const isRoot = level === "root";
  return (
    <div className={`flex flex-wrap items-center gap-2 px-5 py-3 ${isRoot ? "" : "pl-12"}`}>
      {isRoot ? (
        <button
          type="button"
          className="btn btn-ghost btn-sm min-w-8 px-2"
          onClick={onToggle}
          aria-expanded={expanded}
          aria-label={`${node.name} 소분류 ${expanded ? "접기" : "펼치기"}`}
          disabled={node.children.length === 0}
        >
          {expanded ? "▾" : "▸"}
        </button>
      ) : null}
      <span className={isRoot ? "font-semibold" : ""}>{node.name}</span>
      <span className="text-xs text-text-3">순서 {node.displayOrder}{isRoot ? ` · 소분류 ${node.children.length}` : ""}</span>
      <span className="ml-auto flex flex-wrap items-center gap-1.5">
        <button type="button" className="btn btn-ghost btn-sm" aria-label={`${node.name} 위로`} disabled={busy || !canMove(siblings, node.categoryId, "up")} onClick={() => onMove("up")}>↑</button>
        <button type="button" className="btn btn-ghost btn-sm" aria-label={`${node.name} 아래로`} disabled={busy || !canMove(siblings, node.categoryId, "down")} onClick={() => onMove("down")}>↓</button>
        {onAddChild && <button type="button" className="btn btn-ghost btn-sm" onClick={onAddChild}>소분류 추가</button>}
        <button type="button" className="btn btn-ghost btn-sm" aria-label={`${node.name} 수정`} onClick={onEdit}>수정</button>
        <button type="button" className="btn btn-ghost btn-sm text-danger" aria-label={`${node.name} 삭제`} disabled={busy} onClick={onDelete}>삭제</button>
      </span>
    </div>
  );
}
