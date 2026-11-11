"use client";

import { useParams } from "next/navigation";
import { useRef } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useSellerProduct } from "@/features/seller/products/api";
import { ProductForm } from "@/features/seller/products/components/ProductForm";
import { contentEditKind } from "@/features/seller/products/product-actions";

export default function SellerProductEditPage() {
  const { productId: raw } = useParams<{ productId: string }>();
  const productId = Number(raw);
  const valid = Number.isSafeInteger(productId) && productId > 0;
  const detail = useSellerProduct(productId, valid);
  // 제출 성공 뒤 상세로 이동하는 동안 새 상세(pendingChangeRequestId)가 와도 "검수 중" 안내로 바꾸지 않는다.
  const leaving = useRef(false);

  if (!valid) return <EmptyState message="상품을 찾을 수 없습니다" action={{ label: "상품 목록으로", href: "/seller/products" }} />;
  if (detail.isPending) return <PageSkeleton />;
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  const p = detail.data;
  const kind = contentEditKind(p.status);
  const back = { label: "상품 상세로", href: `/seller/products/${p.productId}` };
  if (kind === null) return <EmptyState message="판매가 종료된 상품은 수정할 수 없습니다" action={back} />;
  if (kind === "change-request" && !leaving.current && p.pendingChangeRequestId != null) {
    return <EmptyState message="검수 중인 변경 요청이 있습니다. 철회한 뒤 다시 요청할 수 있습니다." action={back} />;
  }
  // 상세가 다시 불러와져도 입력 중인 폼이 초기화되지 않도록 key 는 상품 ID 로만 둔다.
  return <ProductForm key={p.productId} mode={kind} initial={p} leavingRef={leaving} />;
}
