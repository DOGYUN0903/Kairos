import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { CartPage } from "@/features/cart/components/CartPage";

export default function Page() {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <CartPage />
      </RequireAuth>
    </Suspense>
  );
}
