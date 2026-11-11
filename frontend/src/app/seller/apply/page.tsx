"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ApplyForm } from "@/features/seller/apply/components/ApplyForm";
import { useSellerMe } from "@/features/seller/gate/useSellerMe";

export default function SellerApplyPage() {
  const router = useRouter();
  const { status, seller } = useSellerMe();
  const approvedOrSuspended = status === "APPROVED" || status === "SUSPENDED";

  useEffect(() => {
    if (approvedOrSuspended) router.replace("/seller");
  }, [approvedOrSuspended, router]);

  if (status === "none") return <ApplyForm mode="create" />;
  if ((status === "PENDING" || status === "REJECTED") && seller) return <ApplyForm mode="update" seller={seller} />;
  return null;
}
